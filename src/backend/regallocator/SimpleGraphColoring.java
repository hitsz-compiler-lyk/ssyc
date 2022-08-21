package backend.regallocator;

import backend.Consts;
import backend.arm.*;
import backend.operand.*;
import utils.Log;
import utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

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
                spillNodes.add(chooseSpillNode(func));
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
                                && !flag.contains(oneReg))
                        .findFirst().orElse(
                                Consts.allocableIRegs.stream().filter(oneReg -> !flag.contains(oneReg)).findFirst()
                                        .orElseThrow(() -> new RuntimeException("reg allocate failed")));
                ans.put(reg, phyReg);
            } else {
                final var phyReg = Consts.allocableFRegs.stream()
                        .filter(oneReg -> (oneReg.isCallerSave() || (oneReg.isCalleeSave() && used.contains(oneReg)))
                                && !flag.contains(oneReg))
                        .findFirst().orElse(
                                Consts.allocableFRegs.stream().filter(oneReg -> !flag.contains(oneReg)).findFirst()
                                        .orElseThrow(() -> new RuntimeException("reg allocate failed")));
                ans.put(reg, phyReg);
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
        haveSimplify = new HashSet<>();
        adj = func.stream().flatMap(List::stream)
                .map(ArmInst::getOperands).flatMap(List::stream)
                .filter(op -> op instanceof Reg).map(op -> (Reg) op)
                .distinct().collect(Collectors.toMap(op -> op, InterfereRegs::new));
        LivenessAnalysis.funcLivenessAnalysis(func);
        for (var block : func.asElementView()) {
            var live = new HashSet<>(block.getBlockLiveInfo().getLiveOut());

            var instsInReverse = new ArrayList<>(block);
            Collections.reverse(instsInReverse);
            for (final var inst : instsInReverse) {
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

    private Reg chooseSpillNode(ArmFunction func) {
        Reg spillNode = null;
        for (var reg : remainNodes) {
            if (func.getStackAddrMap().containsKey(reg)
                    // || func.getParamLoadMap().containsKey(reg)
                    // || func.getStackLoadMap().containsKey(reg)
                    // || func.getAddrLoadMap().containsKey(reg)
                    // 不优先处理
                    || func.getImmMap().containsKey(reg)) {
                if (func.getSpillNodes().contains(reg)) {
                    continue;
                }
                if (spillNode == null
                        || adj.get(reg).getRegs().size() > adj.get(spillNode).getRegs().size()) {
                    spillNode = reg;
                }
            }
        }
        if (spillNode == null) {
            for (var reg : remainNodes) {
                if (func.getSpillNodes().contains(reg)) {
                    continue;
                }
                if (spillNode == null
                        || adj.get(reg).getRegs().size() > adj.get(spillNode).getRegs().size()) {
                    spillNode = reg;
                }
            }
        }
        Log.ensure(spillNode != null, "choose spill node is null");
        remove(spillNode);
        return spillNode;
    }

    private void spill(ArmFunction func, List<Reg> spillNodes) {
        Map<Reg, Integer> offsetMap = new HashMap<>();
        Set<Operand> specialNode = new HashSet<>();
        for (var spill : spillNodes) {
            if (func.getAddrLoadMap().containsKey(spill)
                    || func.getParamLoadMap().containsKey(spill)
                    || (func.getStackLoadMap().containsKey(spill) && !func.getStackStoreSet().contains(spill))
                    || func.getImmMap().containsKey(spill)
                    || func.getStackAddrMap().containsKey(spill)) {
                specialNode.add(spill);
            } else if (!func.getStackStoreSet().contains(spill)) {
                int offset = func.getStackSize();
                func.addStackSize(4);
                offsetMap.put(spill, offset);
            }
        }
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                boolean nxt = false;
                for (var reg : inst.getRegDef()) {
                    if (specialNode.contains(reg)) {
                        inst.freeFromIList();
                        // 可以删除在prologue中的param load
                        nxt = true;
                        break;
                    }
                }
                if (nxt) {
                    continue;
                }
                for (var spill : spillNodes) {
                    if (inst.getOperands().contains(spill)) {
                        if (func.getImmMap().containsKey(spill)) {
                            Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                            Reg vr = spill.IsInt() ? new IVirtualReg() : new FVirtualReg();
                            var oldMove = func.getImmMap().get(spill);
                            var newMove = new ArmInstMove(vr, oldMove.getSrc());
                            inst.insertBeforeCO(newMove);
                            inst.replaceOperand(spill, vr);
                            func.getImmMap().put(vr, newMove);
                            func.getSpillNodes().add(vr);
                        } else if (func.getAddrLoadMap().containsKey(spill)) {
                            Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                            var vr = new IVirtualReg();
                            var oldLoad = func.getAddrLoadMap().get(spill);
                            var newLoad = new ArmInstLoad(vr, oldLoad.getAddr());
                            inst.insertBeforeCO(newLoad);
                            inst.replaceOperand(spill, vr);
                            func.getAddrLoadMap().put(vr, newLoad);
                            func.getSpillNodes().add(vr);
                        } else if (func.getStackAddrMap().containsKey(spill)) {
                            Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                            var vr = new IVirtualReg();
                            var oldStackAddr = func.getStackAddrMap().get(spill);
                            var newStackAddr = new ArmInstStackAddr(vr, oldStackAddr.getOffset());
                            newStackAddr.setFix(oldStackAddr.isFix());
                            newStackAddr.setCAlloc(oldStackAddr.isCAlloc());
                            newStackAddr.setUpper(oldStackAddr.getUpper());
                            newStackAddr.setNether(oldStackAddr.getNether());
                            newStackAddr.setTrueOffset(oldStackAddr.getTrueOffset());
                            inst.insertBeforeCO(newStackAddr);
                            inst.replaceOperand(spill, vr);
                            func.getStackAddrMap().put(vr, newStackAddr);
                            func.getSpillNodes().add(vr);
                        } else if (func.getParamLoadMap().containsKey(spill)) {
                            Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                            Reg vr = spill.IsInt() ? new IVirtualReg() : new FVirtualReg();
                            var oldParamLoad = func.getParamLoadMap().get(spill);
                            var newParamLoad = new ArmInstParamLoad(vr, oldParamLoad.getOffset());
                            // newParamLoad.replaceAddr(oldParamLoad.getAddr());
                            // newParamLoad.setTrueOffset(oldParamLoad.getTrueOffset());
                            // 对于param load 恢复到最初的状态 即从sp中获取数据 再去找addr
                            inst.insertBeforeCO(newParamLoad);
                            inst.replaceOperand(spill, vr);
                            func.getParamLoadMap().put(vr, newParamLoad);
                            func.getSpillNodes().add(vr);
                        } else if (func.getStackLoadMap().containsKey(spill) && !(inst instanceof ArmInstStackStore)
                                && !inst.getRegDef().contains(spill)) {
                            Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                            Reg vr = spill.IsInt() ? new IVirtualReg() : new FVirtualReg();
                            var oldStackLoad = func.getStackLoadMap().get(spill);
                            var newStackLoad = new ArmInstStackLoad(vr, oldStackLoad.getOffset());
                            newStackLoad.replaceAddr(oldStackLoad.getAddr());
                            inst.insertBeforeCO(newStackLoad);
                            inst.replaceOperand(spill, vr);
                            func.getStackLoadMap().put(vr, newStackLoad);
                            func.getSpillNodes().add(vr);
                        } else if (!func.getStackStoreSet().contains(spill)) {
                            Reg vr = spill.IsInt() ? new IVirtualReg() : new FVirtualReg();
                            int offset = offsetMap.get(spill);
                            if (inst.getRegUse().contains(spill)) {
                                var stackLoad = new ArmInstStackLoad(vr, new IImm(offset));
                                inst.insertBeforeCO(stackLoad);
                                func.getStackLoadMap().put(vr, stackLoad);
                            }
                            if (inst.getRegDef().contains(spill)) {
                                inst.insertAfterCO(new ArmInstStackStore(vr, new IImm(offset)));
                                func.getStackStoreSet().add(vr);
                            }
                            inst.replaceOperand(spill, vr);
                            func.getSpillNodes().add(vr);
                        }
                        func.getSpillNodes().add(spill);
                    }
                }
            }
        }
    }
}
