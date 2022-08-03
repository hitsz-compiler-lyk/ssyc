package top.origami404.ssyc.backend.regallocator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import top.origami404.ssyc.backend.Consts;
import top.origami404.ssyc.backend.arm.ArmFunction;
import top.origami404.ssyc.backend.arm.ArmInstLoad;
import top.origami404.ssyc.backend.arm.ArmInstStore;
import top.origami404.ssyc.backend.operand.FVirtualReg;
import top.origami404.ssyc.backend.operand.IPhyReg;
import top.origami404.ssyc.backend.operand.IVirtualReg;
import top.origami404.ssyc.backend.operand.Reg;
import top.origami404.ssyc.utils.Log;
import top.origami404.ssyc.utils.Pair;

public class SimpleGraphColoring implements RegAllocator {
    public static class InterfereRegs {
        private int icnt, fcnt;
        private Set<Reg> regs;
        private boolean selfIsInt;

        public InterfereRegs(Reg self) {
            this.regs = new HashSet<>();
            this.selfIsInt = self.IsInt();
            this.icnt = 0;
            this.fcnt = 0;
            if (this.selfIsInt) {
                this.icnt++;
            } else {
                this.fcnt++;
            }
        }

        public void add(Reg reg) {
            if (regs.contains(reg)) {
                return;
            }
            if (reg.IsInt()) {
                this.icnt++;
            } else {
                this.fcnt++;
            }
            regs.add(reg);
        }

        public void remove(Reg reg) {
            Log.ensure(regs.contains(reg), "remove reg not contains in regs");
            if (reg.IsInt()) {
                this.icnt--;
            } else {
                this.fcnt--;
            }
            regs.remove(reg);
        }

        public Set<Reg> getRegs() {
            return regs;
        }

        public boolean canSimolify() {
            return icnt <= Consts.iAllocableRegCnt && fcnt <= Consts.fAllocableRegCnt;
        }

        public void clear() {
            this.regs.clear();
            this.icnt = 0;
            this.fcnt = 0;
            if (this.selfIsInt) {
                this.icnt++;
            } else {
                this.fcnt++;
            }
        }
    }

    private Map<Reg, InterfereRegs> adj;
    private Set<Reg> exist;
    private Set<Reg> remainNodes; // 必须是一个虚拟寄存器
    private Queue<Reg> simplifyQueue;
    private List<Pair<Reg, List<Reg>>> simplifyWorkLists;
    private Set<Reg> haveSimplify;

    @Override
    public String getName() {
        return Consts.SimpleGraphColoring;
    }

    @Override
    public Map<Reg, Reg> run(ArmFunction func) {
        while (true) {
            var ans = runTurn(func);
            if (ans != null) {
                return ans;
            }
        }
    }

    public Map<Reg, Reg> runTurn(ArmFunction func) {
        buildGraph(func);
        for (var ent : adj.entrySet()) {
            var reg = ent.getKey();
            var regInfo = ent.getValue();
            if (reg.allocable() || !exist.contains(reg)) {
                continue;
            }
            remainNodes.add(reg);
            if (regInfo.canSimolify()) {
                simplifyQueue.add(reg);
                haveSimplify.add(reg);
            }
        }
        simplify();
        if (!remainNodes.isEmpty()) {
            List<Reg> spillNodes = new ArrayList<>();
            while (!remainNodes.isEmpty()) {
                var reg = chooseSpillNode();
                spillNodes.add(reg);
                simplify();
            }
            spill(func, spillNodes);
            return null;
        }
        Map<Reg, Reg> ans = new HashMap<>();
        Set<Reg> used = new HashSet<>();
        for (var reg : Consts.allocableRegs) {
            if (exist.contains(reg)) {
                used.add(reg);
            }
        }
        for (int i = simplifyWorkLists.size() - 1; i >= 0; i--) {
            var workList = simplifyWorkLists.get(i);
            var reg = workList.getKey();
            var conflictNodes = workList.getValue();
            Set<Reg> flag = new HashSet<>();
            Log.ensure(!ans.containsKey(reg), "reg: " + reg + " allocator twice");
            for (var node : conflictNodes) {
                if (Consts.allocableRegs.contains(node)) {
                    flag.add(node);
                } else if (ans.containsKey(node)) {
                    flag.add(ans.get(node));
                }
            }
            if (reg.IsInt()) {
                for (var phyReg : Consts.allocableIRegs) {
                    if ((phyReg.isCallerSave() || (phyReg.isCalleeSave() && used.contains(phyReg)))
                            && !flag.contains(phyReg)) {
                        ans.put(reg, phyReg);
                        break;
                    }
                }
                if (!ans.containsKey(reg)) {
                    for (var phyReg : Consts.allocableIRegs) {
                        if (!flag.contains(phyReg)) {
                            ans.put(reg, phyReg);
                            break;
                        }
                    }
                }
            } else {
                for (var phyReg : Consts.allocableFRegs) {
                    if (used.contains(phyReg) && !flag.contains(phyReg)) {
                        ans.put(reg, phyReg);
                        break;
                    }
                }
                if (!ans.containsKey(reg)) {
                    for (var phyReg : Consts.allocableFRegs) {
                        if (!flag.contains(phyReg)) {
                            ans.put(reg, phyReg);
                            break;
                        }
                    }
                }
            }
            Log.ensure(ans.containsKey(reg), "reg allocate failed");
            used.add(ans.get(reg));
        }
        return ans;
    }

    private void buildGraph(ArmFunction func) {
        simplifyWorkLists = new ArrayList<>();
        remainNodes = new HashSet<>();
        simplifyQueue = new ArrayDeque<>();
        adj = new HashMap<>();
        exist = new HashSet<>();
        haveSimplify = new HashSet<>();
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                for (var op : inst.getOperands()) {
                    if (op instanceof Reg) {
                        adj.put((Reg) op, new InterfereRegs((Reg) op));
                    }
                }
            }
        }
        LivenessAnalysis.funcLivenessAnalysis(func);
        List<Reg> liveList = new ArrayList<>();
        List<Reg> newNodes = new ArrayList<>();
        for (var block : func.asElementView()) {
            var live = new HashSet<>(block.getBlockLiveInfo().getLiveOut());
            liveList.clear();
            for (var reg : live) {
                if (reg.IsVirtual() || reg.allocable()) {
                    liveList.add(reg);
                }
            }
            var insts = block.asElementView();
            if (insts.size() > 0) {
                for (var reg : insts.get(insts.size() - 1).getRegDef()) {
                    if (reg.IsVirtual() || reg.allocable()) {
                        liveList.add(reg);
                    }
                }
            }

            for (int i = 0; i < liveList.size(); i++) {
                for (int j = 0; j < i; j++) {
                    if (liveList.get(i).equals(liveList.get(j))) {
                        continue;
                    }
                    adj.get(liveList.get(i)).add(liveList.get(j));
                    adj.get(liveList.get(j)).add(liveList.get(i));
                }
            }

            for (int i = insts.size() - 1; i >= 0; i--) {
                var inst = insts.get(i);
                newNodes.clear();
                for (var reg : inst.getRegDef()) {
                    if (reg.IsVirtual() || reg.allocable()) {
                        exist.add(reg);
                        live.remove(reg);
                    }
                }
                for (var reg : inst.getRegUse()) {
                    if (reg.IsVirtual() || reg.allocable()) {
                        exist.add(reg);
                        newNodes.add(reg);
                        if (!live.contains(reg)) {
                            for (var u : live) {
                                adj.get(reg).add(u);
                                adj.get(u).add(reg);
                            }
                            live.add(reg);
                        }
                    }
                }
                if (i != 0) {
                    for (var reg : insts.get(i - 1).getRegDef()) {
                        if (reg.IsVirtual() || reg.allocable()) {
                            newNodes.add(reg);
                            if (!live.contains(reg)) {
                                for (var u : live) {
                                    adj.get(reg).add(u);
                                    adj.get(u).add(reg);
                                }
                            }
                        }
                    }
                }
                for (int j = 0; j < newNodes.size(); j++) {
                    for (int k = 0; k < j; k++) {
                        if (newNodes.get(j).equals(newNodes.get(j))) {
                            continue;
                        }
                        adj.get(newNodes.get(j)).add(newNodes.get(k));
                        adj.get(newNodes.get(k)).add(newNodes.get(j));
                    }
                }
            }
        }
    }

    private void simplify() {
        while (!simplifyQueue.isEmpty()) {
            var reg = simplifyQueue.element();
            simplifyQueue.remove();
            List<Reg> neighbors = new ArrayList<>();
            for (var u : adj.get(reg).getRegs()) {
                neighbors.add(u);
            }
            remove(reg);
            simplifyWorkLists.add(new Pair<>(reg, neighbors));
        }
    }

    private void remove(Reg reg) {
        Log.ensure(reg.IsVirtual(), "remove reg must be a virtual reg");
        for (var u : adj.get(reg).getRegs()) {
            adj.get(u).remove(reg);
            if (adj.get(u).canSimolify() && !haveSimplify.contains(u) && u.IsVirtual()) {
                simplifyQueue.add(u);
                haveSimplify.add(u);
            }
        }
        adj.get(reg).clear();
        remainNodes.remove(reg);
    }

    private Reg chooseSpillNode() {
        Reg spillNode = null;
        for (var reg : remainNodes) {
            if (spillNode == null
                    || adj.get(reg).getRegs().size() > adj.get(spillNode).getRegs().size()) {
                spillNode = reg;
            }
        }
        Log.ensure(spillNode != null, "choose spill node is null");
        remove(spillNode);
        return spillNode;
    }

    private void spill(ArmFunction func, List<Reg> spillNodes) {
        Map<Reg, Integer> offsetMap = new HashMap<>();
        for (var spill : spillNodes) {
            int offset = func.getStackSize();
            func.addStackSize(4);
            offsetMap.put(spill, offset);
        }
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                for (var spill : spillNodes) {
                    if (inst.getOperands().contains(spill)) {
                        Reg vr = null;
                        if (spill.IsInt()) {
                            vr = new IVirtualReg();
                        } else {
                            vr = new FVirtualReg();
                        }
                        int offset = offsetMap.get(spill);
                        if (inst.getRegUse().contains(spill)) {
                            var load = new ArmInstLoad(vr, new IPhyReg("sp"), offset);
                            inst.insertBeforeCO(load);
                        }
                        if (inst.getRegDef().contains(spill)) {
                            var store = new ArmInstStore(vr, new IPhyReg("sp"), offset);
                            inst.insertAfterCO(store);
                        }
                        inst.replaceOperand(spill, vr);
                    }
                }
            }
        }
    }
}
