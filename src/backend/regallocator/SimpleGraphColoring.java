package backend.regallocator;

import java.util.*;
import java.util.stream.Collectors;

import backend.Consts;
import backend.arm.ArmFunction;
import backend.arm.ArmInst;
import backend.arm.ArmInstLoad;
import backend.arm.ArmInstStore;
import backend.operand.FVirtualReg;
import backend.operand.IPhyReg;
import backend.operand.IVirtualReg;
import backend.operand.Reg;
import utils.Log;
import utils.Pair;

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

        @Override
        public String toString() {
            return regs.toString();
        }
    }

    private Map<Reg, InterfereRegs> adj;
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
            if (reg.IsPhy()) {
                continue;
            }
            remainNodes.add(reg);
            if (ent.getValue().canSimolify()) {
                simplifyQueue.add(reg);
                haveSimplify.add(reg);
            }
        }
        simplify();
        if (!remainNodes.isEmpty()) {
            List<Reg> spillNodes = new ArrayList<>();
            while (!remainNodes.isEmpty()) {
                spillNodes.add(chooseSpillNode());
                simplify();
            }
            spill(func, spillNodes);
            return null;
        }
        Map<Reg, Reg> ans = new HashMap<>();
        Set<Reg> used = Consts.allocableRegs.stream().filter(adj::containsKey).collect(Collectors.toSet());
        for (int i = simplifyWorkLists.size() - 1; i >= 0; i--) {
            var workList = simplifyWorkLists.get(i);
            var reg = workList.getKey();
            var conflictNodes = workList.getValue();
            Set<Reg> flag = new HashSet<>();
            Log.ensure(!ans.containsKey(reg), "reg: " + reg + " allocator twice");
            conflictNodes.stream().filter(Consts.allocableRegs::contains).forEach(flag::add);
            conflictNodes.stream().filter(ans::containsKey).map(ans::get).forEach(flag::add);
            if (reg.IsInt()) {
                final var phyReg = Consts.allocableIRegs.stream()
                        .filter(oneReg -> (oneReg.isCallerSave() || (oneReg.isCalleeSave() && used.contains(oneReg)))
                                && !flag.contains(oneReg)).findFirst().orElse(
                                Consts.allocableIRegs.stream().filter(oneReg -> !flag.contains(oneReg)).findFirst()
                                        .orElseThrow(() -> new RuntimeException("reg allocate failed"))
                        );
                ans.put(reg, phyReg);
            } else {
                final var phyReg = Consts.allocableFRegs.stream()
                        .filter(oneReg -> (used.contains(oneReg) && !flag.contains(oneReg))).findFirst().orElse(
                                Consts.allocableFRegs.stream().filter(oneReg -> !flag.contains(oneReg)).findFirst()
                                        .orElseThrow(()->new RuntimeException("reg allocate failed"))
                        );
                ans.put(reg, phyReg);
            }
            used.add(ans.get(reg));
        }
        return ans;
    }

    private void buildGraph(ArmFunction func) {
        simplifyWorkLists = new ArrayList<>();
        remainNodes = new HashSet<>();
        simplifyQueue = new ArrayDeque<>();
        haveSimplify = new HashSet<>();
        adj = func.stream().flatMap(List::stream)
                .map(ArmInst::getOperands).flatMap(List::stream)
                .map(op -> (Reg) op).collect(Collectors.toMap(op -> op, InterfereRegs::new));
        LivenessAnalysis.funcLivenessAnalysis(func);
        for (var block : func.asElementView()) {
            var live = new HashSet<>(block.getBlockLiveInfo().getLiveOut());
            var insts = block.asElementView();
            for (int i = insts.size() - 1; i >= 0; i--) {
                var inst = insts.get(i);
                for (var def : inst.getRegDef()) {
                    for (var reg : live) {
                        if (!def.equals(reg)) {
                            adj.get(reg).add(def);
                            adj.get(def).add(reg);
                        }
                    }
                }
                live.removeAll(inst.getRegDef());
                live.addAll(inst.getRegUse());
            }
        }
    }

    private void simplify() {
        while (!simplifyQueue.isEmpty()) {
            var reg = simplifyQueue.element();
            simplifyQueue.remove();
            List<Reg> neighbors = new ArrayList<>(adj.get(reg).getRegs());
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
                        Reg vr = spill.IsInt() ? new IVirtualReg() : new FVirtualReg();
                        int offset = offsetMap.get(spill);
                        if (inst.getRegUse().contains(spill)) {
                            inst.insertBeforeCO(new ArmInstLoad(vr, new IPhyReg("sp"), offset));
                        }
                        if (inst.getRegDef().contains(spill)) {
                            inst.insertAfterCO(new ArmInstStore(vr, new IPhyReg("sp"), offset));
                        }
                        inst.replaceOperand(spill, vr);
                    }
                }
            }
        }
    }
}
