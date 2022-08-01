package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.utils.Log;
import top.origami404.ssyc.pass.ir.dataflow.*;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConstructDominatorInfo
    extends ForwardDataFlowPass<Set<BasicBlock>, ConstructDominatorInfo.DominatorInfo>
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
    public void runOnFunction(Function func) {
        super.runOnFunction(func);
        calcIDom(func);
        buildDomTree(func);
    }

    void calcIDom(Function function) {
        for (final var block : function) {
            final var info = DominatorInfo.getInfo(block);
            info.idom = info.calcImmediateDom();
        }
    }

    void buildDomTree(Function function) {
        for (final var block : function) {
            final var info = DominatorInfo.getInfo(block);
            final var idom = info.getImmediateDom();

            if (idom != block) {
                info.domTreeChildren.add(block);
            } else {
                Log.ensure(function.getEntryBBlock() == block);
            }
        }
    }

    static class DominatorInfo extends DataFlowInfo<Set<BasicBlock>> {
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
