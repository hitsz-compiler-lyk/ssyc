package pass.ir;

import ir.BasicBlock;
import ir.Function;
import ir.analysis.AnalysisInfo;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import pass.ir.dataflow.DataFlowInfo;
import pass.ir.dataflow.ForwardDataFlowPass;
import utils.Log;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConstructDominatorInfo
    extends ForwardDataFlowPass<Set<BasicBlock>, DominatorInfo>
{
    @Override
    protected Set<BasicBlock> transfer(BasicBlock block, Set<BasicBlock> in) {
        return in;
    }

    @Override
    protected Set<BasicBlock> meet(BasicBlock block, List<Set<BasicBlock>> predOuts) {
        if (predOuts.isEmpty()) {
            // must be entry block
            Log.ensure(block == block.getParent().getEntryBBlock(),
                "Non-entry block %s does NOT have any pred".formatted(block));
            return entryIn(block);
        }

        final var result = new LinkedHashSet<>(predOuts.get(0));
        predOuts.forEach(result::retainAll);

        result.add(block);
        return result;
    }

    @Override
    protected Set<BasicBlock> topElement(BasicBlock block) {
        return new LinkedHashSet<>(block.getParent());
    }

    @Override
    protected Set<BasicBlock> entryIn(BasicBlock block) {
        return Set.of(block);
    }

    @Override
    protected Class<DominatorInfo> getInfoClass() {
        return DominatorInfo.class;
    }

    @Override
    protected DominatorInfo createInfo(BasicBlock block) {
        return new DominatorInfo(block);
    }

    @Override
    public void runOnFunction(Function function) {
        if (FunctionStructureCache.needUpdate(function)) {
            super.runOnFunction(function);
            calcIDom(function);
            buildDomTree(function);

            FunctionStructureCache.updateCache(function);
        }
    }

    void calcIDom(Function function) {
        for (final var block : function) {
            final var info = DominatorInfo.getInfo(block);
            info.idom = info.calcImmediateDom();
        }
    }

    void buildDomTree(Function function) {
        for (final var block : function) {
            final var idom = DominatorInfo.idom(block);

            if (idom != block) {
                DominatorInfo.getInfo(idom).domTreeChildren.add(block);
            } else {
                Log.ensure(function.getEntryBBlock() == block);
            }
        }
    }

    public static class DominatorInfo extends DataFlowInfo<Set<BasicBlock>> {
        DominatorInfo(BasicBlock self) {
            this.self = self;
            this.idom = null;
            this.domTreeChildren = new LinkedHashSet<>();
        }

        public Set<BasicBlock> getDom() {
            return in();
        }

        public Set<BasicBlock> getStrictDom() {
            final var copyOfDom = new LinkedHashSet<>(getDom());
            copyOfDom.remove(self);
            return copyOfDom;
        }

        public BasicBlock getImmediateDom() {
            return idom;
        }

        private BasicBlock calcImmediateDom() {
            final var selfSDom = sdom(self);

            for (final var block : getDom()) {
                final var sdom = sdom(block);
                sdom.retainAll(selfSDom);
                if (sdom.isEmpty()) {
                    // do NOT contain any other sdom of n
                    return block;
                }
            }

            // 只有可能起始块才没有 idom
            // 这时候不妨规定起始块的 idom 就是自己
            return self;
        }

        private final BasicBlock self;
        private BasicBlock idom;
        private final Set<BasicBlock> domTreeChildren;

        private static DominatorInfo getInfo(BasicBlock block) {
            return block.getAnalysisInfo(DominatorInfo.class);
        }

        public static Set<BasicBlock> dom(BasicBlock block) {
            return getInfo(block).getDom();
        }
        public static Set<BasicBlock> sdom(BasicBlock block) {
            return getInfo(block).getStrictDom();
        }
        public static BasicBlock idom(BasicBlock block) {
            return getInfo(block).getImmediateDom();
        }
        public static Set<BasicBlock> domTreeChildren(BasicBlock block) {
            return Collections.unmodifiableSet(getInfo(block).domTreeChildren);
        }
    }
}

class FunctionStructureCache implements AnalysisInfo {
    public static boolean needUpdate(Function function) {
        if (!function.containsAnalysisInfo(FunctionStructureCache.class)) {
            return true;
        }

        final var info = function.getAnalysisInfo(FunctionStructureCache.class);
        return function.size() != info.size || calcStructureHash(function) != info.hash;
    }

    public static void updateCache(Function function) {
        if (function.containsAnalysisInfo(FunctionStructureCache.class)) {
            function.removeAnalysisInfo(FunctionStructureCache.class);
        }

        final var size = function.size();
        final var hash = calcStructureHash(function);
        final var info = new FunctionStructureCache(size, hash);

        function.addAnalysisInfo(info);
    }

    static int calcStructureHash(Function function) {
        // 需要一个与块顺序无关的 hash
        return function.stream().mapToInt(FunctionStructureCache::calcStructureHash).sum();
    }

    static int calcStructureHash(BasicBlock block) {
        // 需要一个与前继/后继顺序无关的 hash (但是显然不能前继变后继之后还不变的)
        int selfHash = System.identityHashCode(block);
        int predHash = block.getPredecessors().stream().mapToInt(System::identityHashCode).sum();
        int succHash = block.getSuccessors().stream().mapToInt(System::identityHashCode).sum();

        return ((selfHash * 37) + predHash) * 37 + succHash;
    }

    FunctionStructureCache(int size, int hash) {
        this.size = size;
        this.hash = hash;
    }

    private final int size;
    private final int hash;
}
