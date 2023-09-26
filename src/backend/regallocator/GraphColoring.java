package backend.regallocator;

import backend.lir.ArmFunction;
import backend.lir.inst.ArmInst;
import backend.lir.inst.ArmInstMove;
import backend.lir.operand.*;
import utils.*;

import java.util.*;
import java.util.stream.Collectors;

public class GraphColoring implements RegAllocator {

    private List<Reg> regList;
    private Map<Reg, InterfereRegs> adj;
    private Map<Reg, Map<Reg, Integer>> moveEdge;
    private DisjointSetUnion<Reg> dsu;
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
        for (var reg : adj.keySet()) {
            if (reg.isVirtual()) {
                remainNodes.add(reg);
            }
        }
        boolean success = true;
        Set<Reg> spillNodes = new HashSet<>();
        // 溢出
        while (!remainNodes.isEmpty()) {
            // 简化
            simplify();
            if (remainNodes.isEmpty()) break;
            if (coalesce()) continue;
            if (freeze()) continue;
            success = false;
            spillNodes.add(chooseSpillNode(func));
        }
        if (!success) {
            SimpleGraphColoring.spill(func, spillNodes);
            return null;
        }
        Map<Reg, Reg> ans = new HashMap<>();
        Set<Reg> used = Reg.getAllAllocatableRegs().stream().filter(adj::containsKey).collect(Collectors.toSet());
        // 寄存器分配
        // 先把已经分配为物理寄存器的进行分配
        for (var reg : regList) {
            if (reg.isVirtual() && dsu.find(reg).isPhy()) {
                ans.put(reg, dsu.find(reg));
            }
        }
        for (int i = simplifyWorkLists.size() - 1; i >= 0; i--) {
            var workList = simplifyWorkLists.get(i);
            var reg = workList.getKey();
            Log.ensure(dsu.find(reg).equals(reg), "simplify work reg is not the father:" + reg);
            var conflictNodes = workList.getValue();
            Set<Reg> flag = new HashSet<>();
            Log.ensure(!ans.containsKey(reg), "reg: " + reg + " allocator twice");
            conflictNodes.stream().filter(Reg.getAllAllocatableRegs()::contains).forEach(flag::add);
            conflictNodes.stream().map(dsu::find).map(x -> ans.getOrDefault(x, x)).forEach(flag::add);
            Log.ensure(flag.stream().allMatch(Operand::isPhy), "flag exist virtual reg");
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
        for (var reg : regList) {
            if (reg.isVirtual() && !dsu.find(reg).equals(reg)) {
                var fa = dsu.find(reg);
                if (fa.isVirtual()) {
                    ans.put(reg, ans.get(fa));
                } else {
                    ans.put(reg, fa);
                }
            }
        }
        return ans;
    }

    private void buildGraph(ArmFunction func) {
        // 构建冲突图
        simplifyWorkLists = new ArrayList<>();
        remainNodes = new HashSet<>();
        haveSimplify = new HashSet<>();
        dsu = new DisjointSetUnion<>();
        regUsedCnt = new HashMap<>();
        regWeight = new HashMap<>();
        regList = func.stream().flatMap(List::stream).map(ArmInst::getOperands).flatMap(List::stream).filter(Reg.class::isInstance).map(Reg.class::cast).distinct().toList();
        moveEdge = regList.stream().collect(Collectors.toMap(op -> op, op -> new HashMap<>()));
        adj = regList.stream().collect(Collectors.toMap(op -> op, InterfereRegs::new));
        LivenessAnalysis.funcLivenessAnalysis(func);
        for (var block : func) {
            var live = new HashSet<>(block.getBlockLiveInfo().getLiveOut());
            var instsInReverse = new ArrayList<>(block);
            Collections.reverse(instsInReverse);
            for (final var inst : instsInReverse) {
                if (inst instanceof ArmInstMove move) {
                    if (move.getDst() instanceof Reg dst && move.getSrc() instanceof Reg src) {
                        var cnt = moveEdge.get(dst).getOrDefault(src, 0);
                        moveEdge.get(dst).put(src, cnt + 1);
                        moveEdge.get(src).put(dst, cnt + 1);
                    }
                }
            }
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
        for (var reg : regList) {
            for (var u : adj.get(reg).getRegs()) {
                moveEdge.get(reg).remove(u);
            }
            if (reg.isPhy()) {
                for (var phyReg : Reg.getAllAllocatableRegs()) {
                    if (!reg.equals(phyReg)) {
                        moveEdge.get(reg).remove(phyReg);
                    }
                }
            }
        }
        simplifyQueue = new ArrayDeque<>();
        for (var reg : remainNodes) {
            if (moveEdge.get(reg).isEmpty() && adj.get(reg).canSimplify()) {
                simplifyQueue.add(reg);
                haveSimplify.add(reg);
            }
        }
        while (!simplifyQueue.isEmpty()) {
            var reg = simplifyQueue.element();
            simplifyQueue.remove();
            List<Reg> neighbors = new ArrayList<>(adj.get(reg).getRegs());
            int allocatableCnt = Reg.getAllocatableRegsCnt(reg.isInt());
            for (var u : adj.get(reg).getRegs()) {
                adj.get(u).remove(reg);
                if (adj.get(u).getCnt(u.isInt()) == allocatableCnt && !haveSimplify.contains(u) && u.isVirtual() && moveEdge.get(u).isEmpty()) {
                    simplifyQueue.add(u);
                    haveSimplify.add(u);
                }
            }
            simplifyWorkLists.add(new Pair<>(reg, neighbors));
            adj.get(reg).clear();
            remainNodes.remove(reg);
        }
    }

    private boolean coalesce() {
        List<Triplet<Reg, Reg, Integer>> moves = new ArrayList<>();
        Set<Pair<Reg, Reg>> distinctSet = new HashSet<>();
        for (var reg : regList) {
            for (var ent : moveEdge.get(reg).entrySet()) {
                if (!distinctSet.contains(new Pair<>(reg, ent.getKey())) && !distinctSet.contains(new Pair<>(ent.getKey(), reg))) {
                    moves.add(new Triplet<>(reg, ent.getKey(), ent.getValue()));
                    distinctSet.add(new Pair<>(reg, ent.getKey()));
                    distinctSet.add(new Pair<>(ent.getKey(), reg));
                }
            }
        }
        moves.sort(new Comparator<Triplet<Reg, Reg, Integer>>() {
            @Override
            public int compare(Triplet<Reg, Reg, Integer> a, Triplet<Reg, Reg, Integer> b) {
                return -Integer.compare(a.getValue2(), b.getValue2());
            }
        });
        int ret = 0;
        for (var move : moves) {
            var u = dsu.find(move.getValue0());
            var v = dsu.find(move.getValue1());
            if (u.equals(v)) {
                ret += move.getValue2();
                continue;
            }
            if (u.isPhy() && v.isPhy()) continue;
            if (!adj.get(u).getRegs().contains(v) && conservativeCheck(u, v)) {
                merge(u, v);
                ret += move.getValue2();
            }
        }
        return ret > 0;
    }

    private boolean conservativeCheck(Reg u, Reg v) {
        if (u.isInt() != v.isInt()) {
            return false;
        }
        int significantNeighbors = 0;
        final int allocatableCnt = Reg.getAllocatableRegsCnt(u.isInt());
        Set<Reg> sharedAdj = new HashSet<>();
        Set<Reg> uniqueAdj = new HashSet<>();
        for (var x : adj.get(u).getRegs()) {
            if (adj.get(v).getRegs().contains(x)) {
                sharedAdj.add(x);
            } else {
                uniqueAdj.add(x);
            }
        }
        for (var x : adj.get(v).getRegs()) {
            if (!adj.get(u).getRegs().contains(x)) {
                uniqueAdj.add(x);
            }
        }
        for (var reg : uniqueAdj) {
            if (adj.get(reg).getCnt(u.isInt()) - 1 >= allocatableCnt) significantNeighbors++;
        }
        for (var reg : sharedAdj) {
            if (adj.get(reg).getCnt(u.isInt()) - 1 > allocatableCnt) significantNeighbors++;
        }
        return significantNeighbors < allocatableCnt;
    }

    // 以 v 为父亲
    private void merge(Reg u, Reg v) {
        Log.ensure(u.isVirtual() || v.isVirtual(), "reg1: " + u + "and reg2:" + v + " are all physical");
        if (u.isPhy()) {
            var temp = u;
            u = v;
            v = temp;
        }
        Log.ensure(u.isVirtual(), "reg u:" + u + " is not virtual");
        dsu.merge(v, u);
        remainNodes.remove(u);
        for (var x : adj.get(u).getRegs()) {
            adj.get(x).remove(u);
            adj.get(x).add(v);
            adj.get(v).add(x);
        }
        regWeight.put(v, Math.max(regWeight.getOrDefault(u, 2), regWeight.getOrDefault(v, 2)));
        // 如果
        adj.get(u).clear();
        for (var ent : moveEdge.get(u).entrySet()) {
            moveEdge.get(ent.getKey()).remove(u);
            if (!ent.getKey().equals(v)) {
                var w = moveEdge.get(ent.getKey()).getOrDefault(v, 0);
                moveEdge.get(ent.getKey()).put(v, w + ent.getValue());
                moveEdge.get(v).put(ent.getKey(), w + ent.getValue());
            }
        }
        moveEdge.get(u).clear();
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
        remainNodes.remove(spillNode);
        for (var u : adj.get(spillNode).getRegs()) {
            adj.get(u).remove(spillNode);
        }
        for (var u : moveEdge.get(spillNode).keySet()) {
            moveEdge.get(u).remove(spillNode);
        }
        adj.get(spillNode).clear();
        moveEdge.get(spillNode).clear();

        return spillNode;
    }

    private boolean freeze() {
        Reg selected = null;
        for (var u : remainNodes) {
            if (adj.get(u).canSimplify()) {
                if (selected == null || adj.get(u).getAllCnt() > adj.get(selected).getAllCnt()) {
                    selected = u;
                }
            }
        }
        if (selected == null) return false;
        for (var u : moveEdge.get(selected).keySet()) {
            moveEdge.get(u).remove(selected);
        }
        moveEdge.get(selected).clear();
        return true;
    }
}