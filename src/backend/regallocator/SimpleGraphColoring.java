package backend.regallocator;

import backend.lir.ArmFunction;
import backend.lir.inst.*;
import backend.lir.operand.*;
import utils.Log;
import utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 简单的图着色寄存器
 */
public class SimpleGraphColoring implements RegAllocator {
    private Map<Reg, InterfereRegs> adj;
    private Set<Reg> remainNodes; // 必须是一个虚拟寄存器
    private Queue<Reg> simplifyQueue;
    private List<Pair<Reg, List<Reg>>> simplifyWorkLists;
    private Set<Reg> haveSimplify;
    private Map<Reg, Integer> regUsedCnt;
    private Map<Reg, Integer> regWeight;

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
        // 建立冲突图
        buildGraph(func);
        for (var ent : adj.entrySet()) {
            var reg = ent.getKey();
            if (reg.isPhy()) {
                continue;
            }
            remainNodes.add(reg);
            if (ent.getValue().canSimplify()) {
                simplifyQueue.add(reg);
                haveSimplify.add(reg);
            }
        }
        // 简化
        simplify();
        // 溢出
        if (!remainNodes.isEmpty()) {
            Set<Reg> spillNodes = new HashSet<>();
            while (!remainNodes.isEmpty()) {
                spillNodes.add(chooseSpillNode(func));
                simplify();
            }
            spill(func, spillNodes);
            return null;
        }
        Map<Reg, Reg> ans = new HashMap<>();
        Set<Reg> used = Reg.getAllAllocatableRegs().stream().filter(adj::containsKey).collect(Collectors.toSet());
        // 寄存器分配
        for (int i = simplifyWorkLists.size() - 1; i >= 0; i--) {
            var workList = simplifyWorkLists.get(i);
            var reg = workList.getKey();
            var conflictNodes = workList.getValue();
            Set<Reg> flag = new HashSet<>();
            Log.ensure(!ans.containsKey(reg), "reg: " + reg + " allocator twice");
            conflictNodes.stream().filter(Reg.getAllAllocatableRegs()::contains).forEach(flag::add);
            conflictNodes.stream().filter(ans::containsKey).map(ans::get).forEach(flag::add);
            if (reg.isInt()) {
                final var phyReg = IPhyReg.getIntAllocatableRegs().stream().filter(oneReg -> (oneReg.isCallerSave() || (oneReg.isCalleeSave() && used.contains(oneReg))) && !flag.contains(oneReg)).findFirst().orElse(IPhyReg.getIntAllocatableRegs().stream().filter(oneReg -> !flag.contains(oneReg)).findFirst().orElseThrow(() -> new RuntimeException("reg allocate failed")));
                ans.put(reg, phyReg);
            } else {
                final var phyReg = FPhyReg.getFloatAllocatableRegs().stream().filter(oneReg -> (oneReg.isCallerSave() || (oneReg.isCalleeSave() && used.contains(oneReg))) && !flag.contains(oneReg)).findFirst().orElse(FPhyReg.getFloatAllocatableRegs().stream().filter(oneReg -> !flag.contains(oneReg)).findFirst().orElseThrow(() -> new RuntimeException("reg allocate failed")));
                ans.put(reg, phyReg);
            }
            Log.ensure(ans.containsKey(reg), "reg allocate failed");
            used.add(ans.get(reg));
        }
        return ans;
    }

    private void buildGraph(ArmFunction func) {
        // 构建冲突图
        simplifyWorkLists = new ArrayList<>();
        remainNodes = new HashSet<>();
        simplifyQueue = new ArrayDeque<>();
        haveSimplify = new HashSet<>();
        regUsedCnt = new HashMap<>();
        regWeight = new HashMap<>();
        adj = func.stream().flatMap(List::stream).map(ArmInst::getOperands).flatMap(List::stream).filter(Reg.class::isInstance).map(Reg.class::cast).distinct().collect(Collectors.toMap(op -> op, InterfereRegs::new));
        LivenessAnalysis.funcLivenessAnalysis(func);
        for (var block : func) {
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
                for (var reg : inst.getOperands().stream().filter(Reg.class::isInstance).map(Reg.class::cast).filter(Operand::isVirtual).toList()) {
                    regWeight.put(reg, Math.max(regWeight.getOrDefault(reg, 1), (block.getLoopDepth() + 1) * (block.getLoopDepth() + 1)));
                }
                for (var use : inst.getRegUse()) {
                    regUsedCnt.put(use, regUsedCnt.getOrDefault(use, 0) + 1);
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
        Log.ensure(reg.isVirtual(), "remove reg must be a virtual reg");
        for (var u : adj.get(reg).getRegs()) {
            adj.get(u).remove(reg);
            if (adj.get(u).canSimplify() && !haveSimplify.contains(u) && u.isVirtual()) {
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
            if (func.getAddrLoadMap().containsKey(reg) || func.getStackAddrMap().containsKey(reg) || func.getImmMap().containsKey(reg)) {
                if (regUsedCnt.getOrDefault(reg, 0) >= 16) {
                    continue;
                }
                if (func.getSpillNodes().contains(reg)) {
                    continue;
                }
                if (spillNode == null || adj.get(reg).getRegs().size() * regWeight.get(spillNode) > adj.get(spillNode).getRegs().size() * regWeight.get(reg)) {
                    spillNode = reg;
                }
            }
        }
        if (spillNode == null) {
            for (var reg : remainNodes) {
                if (func.getSpillNodes().contains(reg)) {
                    continue;
                }
                if (spillNode == null || adj.get(reg).getRegs().size() * regWeight.get(spillNode) > adj.get(spillNode).getRegs().size() * regWeight.get(reg)) {
                    spillNode = reg;
                }
            }
        }
        Log.ensure(spillNode != null, "choose spill node is null");
        remove(spillNode);
        return spillNode;
    }

    protected static void spill(ArmFunction func, Set<Reg> spillNodes) {
        Map<Reg, Integer> offsetMap = new HashMap<>();
        Set<Operand> specialNode = new HashSet<>();
        for (var spill : spillNodes) {
            if (func.getAddrLoadMap().containsKey(spill) || func.getParamLoadMap().containsKey(spill) || (func.getStackLoadMap().containsKey(spill) && !func.getStackStoreSet().contains(spill)) || func.getImmMap().containsKey(spill) || func.getStackAddrMap().containsKey(spill)) {
                // 需要跳过处理的 spill node
                specialNode.add(spill);
            } else if (!func.getStackStoreSet().contains(spill)) {
                int offset = func.getStackSize();
                func.addStackSize(4);
                offsetMap.put(spill, offset);
            }
        }
        for (var block : func) {
            for (var inst : block) {
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
                for (var op : inst.getOperands()) {
                    if (op instanceof Reg spill) {
                        if (spillNodes.contains(spill)) {
                            if (func.getImmMap().containsKey(spill)) {
                                // 直接替换立即数
                                Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                                Reg vr = spill.isInt() ? new IVirtualReg() : new FVirtualReg();
                                var oldMove = func.getImmMap().get(spill);
                                var newMove = new ArmInstMove(vr, oldMove.getSrc());
                                inst.insertBeforeCO(newMove);
                                inst.replaceOperand(spill, vr);
                                func.getImmMap().put(vr, newMove);
                                func.getSpillNodes().add(vr);
                            } else if (func.getAddrLoadMap().containsKey(spill)) {
                                // 直接替换 addr
                                Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                                var vr = new IVirtualReg();
                                var oldLoad = func.getAddrLoadMap().get(spill);
                                var newLoad = new ArmInstLoad(vr, oldLoad.getAddr());
                                inst.insertBeforeCO(newLoad);
                                inst.replaceOperand(spill, vr);
                                func.getAddrLoadMap().put(vr, newLoad);
                                func.getSpillNodes().add(vr);
                            } else if (func.getStackAddrMap().containsKey(spill)) {
                                // 直接替换 stack addr
                                Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                                var vr = new IVirtualReg();
                                var oldStackAddr = func.getStackAddrMap().get(spill);
                                var newStackAddr = new ArmInstStackAddr(vr, oldStackAddr.getOffset());
                                newStackAddr.setFix(oldStackAddr.isFix());
                                newStackAddr.setCAlloc(oldStackAddr.isCAlloc());
                                newStackAddr.setTrueOffset(oldStackAddr.getTrueOffset());
                                inst.insertBeforeCO(newStackAddr);
                                inst.replaceOperand(spill, vr);
                                func.getStackAddrMap().put(vr, newStackAddr);
                                func.getSpillNodes().add(vr);
                            } else if (func.getParamLoadMap().containsKey(spill)) {
                                // 直接替换 param load
                                Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                                Reg vr = spill.isInt() ? new IVirtualReg() : new FVirtualReg();
                                var oldParamLoad = func.getParamLoadMap().get(spill);
                                var newParamLoad = new ArmInstParamLoad(vr, oldParamLoad.getOffset());
                                // newParamLoad.replaceAddr(oldParamLoad.getAddr());
                                // newParamLoad.setTrueOffset(oldParamLoad.getTrueOffset());
                                // 对于param load 恢复到最初的状态 即从sp中获取数据 再去找addr
                                inst.insertBeforeCO(newParamLoad);
                                inst.replaceOperand(spill, vr);
                                func.getParamLoadMap().put(vr, newParamLoad);
                                func.getSpillNodes().add(vr);
                            } else if (func.getStackLoadMap().containsKey(spill) && !(inst instanceof ArmInstStackStore) && !inst.getRegDef().contains(spill)) {
                                // 把一个基本块内合并的StackLoad又重新分裂
                                Log.ensure(!inst.getRegDef().contains(spill), "def reg contains special node");
                                Reg vr = spill.isInt() ? new IVirtualReg() : new FVirtualReg();
                                var oldStackLoad = func.getStackLoadMap().get(spill);
                                var newStackLoad = new ArmInstStackLoad(vr, oldStackLoad.getOffset());
                                newStackLoad.replaceAddr(oldStackLoad.getAddr());
                                inst.insertBeforeCO(newStackLoad);
                                inst.replaceOperand(spill, vr);
                                func.getStackLoadMap().put(vr, newStackLoad);
                                func.getSpillNodes().add(vr);
                            } else if (!func.getStackStoreSet().contains(spill)) {
                                // 直接分裂
                                Reg vr = spill.isInt() ? new IVirtualReg() : new FVirtualReg();
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
}
