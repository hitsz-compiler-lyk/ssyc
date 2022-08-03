package ir;

import ir.analysis.AnalysisInfo;
import utils.Log;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ConstructDominatorInfo implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().forEach(DominatorInfo::run);
    }

    static class DominatorInfo implements AnalysisInfo {
        static void run(Function function) {
            insertInfo(function);
            iterDomUntilFixPoint(function);
            calcIDom(function);
            buildDomTree(function);
        }

        static void insertInfo(Function function) {
            for (final var block : function) {
                final var allBlocks = new LinkedHashSet<>(function);
                block.addAnalysisInfo(new DominatorInfo(block, allBlocks));
            }

            final var entry = function.getEntryBBlock();
            final var entryDom = dom(entry);
            entryDom.clear();
            entryDom.add(entry);
        }

        static void iterDomUntilFixPoint(Function function) {
            final var blocksWithoutEntry = new LinkedHashSet<>(function);
            blocksWithoutEntry.remove(function.getEntryBBlock());

            boolean hasChange = true;
            while (hasChange) {
                hasChange = false;

                for (final var block : blocksWithoutEntry) {
                    final var newDom = new LinkedHashSet<>(function);
                    block.getPredecessors().stream().map(DominatorInfo::dom).forEach(newDom::retainAll);
                    newDom.add(block);
                    hasChange = getInfo(block).setDom(newDom) || hasChange;
                }
            }
        }

        static void calcIDom(Function function) {
            for (final var block : function) {
                final var info = getInfo(block);
                info.idom = info.calcImmediateDom();
            }
        }

        static void buildDomTree(Function function) {
            for (final var block : function) {
                final var info = getInfo(block);
                final var idom = info.getImmediateDom();

                if (idom != block) {
                    info.domTreeChildren.add(block);
                } else {
                    Log.ensure(function.getEntryBBlock() == block);
                }
            }
        }



        DominatorInfo(BasicBlock self, Set<BasicBlock> init) {
            this.self = self;
            this.dom = init;
            this.idom = null;
            this.domTreeChildren = new LinkedHashSet<>();
        }

        boolean setDom(Set<BasicBlock> newDom) {
            if (!newDom.equals(dom)) {
                dom = newDom;
                return true;
            } else {
                return false;
            }
        }

        public Set<BasicBlock> getDom() {
            return dom;
        }

        public Set<BasicBlock> getStrictDom() {
            final var copyOfDom = new LinkedHashSet<>(dom);
            copyOfDom.remove(self);
            return copyOfDom;
        }

        public BasicBlock getImmediateDom() {
            return idom;
        }

        private BasicBlock calcImmediateDom() {
            final var selfSDom = sdom(self);

            for (final var block : dom) {
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
        private Set<BasicBlock> dom;
        private BasicBlock idom;
        private final Set<BasicBlock> domTreeChildren;

        public static DominatorInfo getInfo(BasicBlock block) {
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
