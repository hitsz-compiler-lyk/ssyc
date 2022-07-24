package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.frontend.info.InstCache;
import top.origami404.ssyc.ir.*;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.visitor.ValueVisitor;
import top.origami404.ssyc.pass.ir.InstructionUnique.DomInfoMaker.DomInfo.BrKind;
import top.origami404.ssyc.pass.ir.InstructionUnique.DomInfoMaker.DomInfo.BrKindHandler;
import top.origami404.ssyc.utils.Log;

import java.util.*;

public class InstructionUnique implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().forEach(InstructionUnique::runOnFunction);
    }

    static void runOnFunction(Function func) {
        final var uniquer = new ValueUniquer();
        for (final var block : func.getBasicBlocks()) {
            block.allInst().forEach(uniquer::visitInstruction);
        }

        final var domInfo = new DomInfoMaker(func);

    }

    static class DomInfoMaker {
        public DomInfoMaker(Function function) {
            insertDomInfo(function);
            fillDomInfoInitValue(function);
            calcBrEdgeKind(function.getEntryBBlock());
            calcDepth(function.getEntryBBlock(), 1);
        }

        private void insertDomInfo(Function func) {
            func.getBasicBlocks().forEach(block -> block.addAnalysisInfo(new DomInfo()));
        }

        private void fillDomInfoInitValue(Function func) {
            for (final var block : func.getBasicBlocks()) {
                final var blockInfo = block.getAnalysisInfo(DomInfo.class);
                forEachWithIndex(block.getSuccessors(), (i, succ) -> {
                    final var succInfo = succ.getAnalysisInfo(DomInfo.class);
                    final var selfPosInPred = succ.getPredecessors().indexOf(block);
                    final var kindHandler = new BrKindHandler();

                    blockInfo.succKinds.set(i, kindHandler);
                    succInfo.predKinds.set(selfPosInPred, kindHandler);
                });
            }
        }

        private final Set<BasicBlock> stack = new HashSet<>();
        private final Set<BasicBlock> visited = new HashSet<>();
        /** 构建 DFS 树 + 给边分类(树边, 返祖边, 其他边) */
        private void calcBrEdgeKind(BasicBlock now) {
            stack.add(now);
            visited.add(now);

            final var info = now.getAnalysisInfo(DomInfo.class);

            forEachWithIndex(now.getSuccessors(), (i, to) ->{
                final var handler = info.succKinds.get(i);

                if (visited.contains(to)) {
                    handler.kind = stack.contains(to) ? BrKind.Back : BrKind.Other;
                } else {
                    handler.kind = BrKind.Tree;
                    calcBrEdgeKind(to);
                }
            });

            stack.remove(now);
        }

        /** 为构建出的 DFS 树中的节点维护深度信息 (用于 LCA) */
        private void calcDepth(BasicBlock now, int currDepth) {
            final var info = now.getAnalysisInfo(DomInfo.class);
            Log.ensure(info.depth == 0);
            info.depth = currDepth;

            forEachWithIndex(now.getSuccessors(), (i, succ) -> {
                final var kind = info.succKinds.get(i).kind;
                if (kind == BrKind.Tree) {
                    calcDepth(succ, currDepth + 1);
                }
            });
        }

        /** 找到 a, b 两个基本块的最近的公共支配块 */
        public BasicBlock lcdom(BasicBlock a, BasicBlock b) {
            if (a == b) {
                return idom(a);
            }

            while (true) {
                while (getDepth(a) != getDepth(b)) {
                    if (!(getDepth(a) > getDepth(b))) {
                        final var tmp = a;
                        a = b;
                        b = tmp;
                    }

                    while (getDepth(a) > getDepth(b)) {
                        a = idom(a);
                    }
                }

                if (a == b) {
                    return a;
                }

                a = idom(a);
                b = idom(b);
            }
        }

        /** 找到该集合内所有基本块的最近公共支配块 */
        public BasicBlock lcdom(Set<BasicBlock> blocks) {
            if (blocks.size() == 0) {
                throw new RuntimeException("Can NOT find LCDom for empty set");
            }

            // 随后一个一个合并可能作为支配块的前继块
            while (blocks.size() > 1) {
                final var iter = blocks.iterator();

                // 每次拿两个出来
                final var first = iter.next();
                blocks.remove(first);

                final var second = iter.next();
                blocks.remove(second);

                // 算出它们的最近公共支配块之后, 塞回去
                blocks.add(lcdom(first, second));
            }

            return blocks.iterator().next();
        }

        /** 找到 block 的最近的支配块 */
        public BasicBlock idom(BasicBlock block) {
            // entry block
            if (block.getPredecessorSize() == 0) {
                return block;
            }

            final var info = block.getAnalysisInfo(DomInfo.class);
            if (info.idom.isEmpty()) {
                // 从前继中筛选出那些不是返祖的 (以避免找 idom 时循环找到自己)
                // 因为凡是返祖返回来的块都是在自己的控制流 "下面" 的, 不可能是自己的支配块
                final var truePredSet = new HashSet<BasicBlock>();
                forEachWithIndex(block.getPredecessors(), (i, pred) -> {
                    if (info.predKinds.get(i).kind != BrKind.Back) {
                        truePredSet.add(pred);
                    }
                });

                // 稍微做个缓存省省时间
                info.idom = Optional.of(lcdom(truePredSet));
            }

            return info.idom.orElseThrow();
        }

        private int getDepth(BasicBlock block) {
            return block.getAnalysisInfo(DomInfo.class).depth;
        }

        static class DomInfo implements AnalysisInfo {
            enum BrKind { Unknown, Tree, Back, Other };
            static class BrKindHandler {
                public BrKind kind = BrKind.Unknown;
            }

            int depth = 0;
            Optional<BasicBlock> idom = Optional.empty();
            final List<BrKindHandler> succKinds = new ArrayList<>();
            final List<BrKindHandler> predKinds = new ArrayList<>();
        }

        interface BiFunctionInt<E> {
            void run(int idx, E elm);
        }

        private static <E> void forEachWithIndex(List<E> list, BiFunctionInt<E> func) {
            final var size = list.size();
            for (int i = 0; i < size; i++) {
                func.run(i, list.get(i));
            }
        }
    }

    static class ValueUniquer implements ValueVisitor<Void> {

        @Override
        public Void visitInstruction(final Instruction inst) {
            // System.out.println(inst);

            if (canNotBeUniqued(inst)) {
                return null;
            }

            for (final var operand : inst.getOperands()) {
                if (operand == inst) {
                    Log.ensure(inst instanceof PhiInst, "Only phi can use itself as an operand");
                    continue;
                }

                visit(operand);
            }

            // 我很想写成这样的, 但是为了异常检查还是算了吧...
            // inst.getOperands().stream().filter(o -> o != inst).forEach(this::visit);

            cache.addOrReplace(inst);
            return null;
        }

        public boolean canNotBeUniqued(Instruction inst) {
            if (inst.getType().isVoid()) {
                return true;
            }

            final var classesUnableToUnique = Set.of(
                CAllocInst.class, CallInst.class, LoadInst.class, PhiInst.class
            );

            return classesUnableToUnique.contains(inst.getClass());
        }

        @Override public Void visitParameter(final Parameter value)    { return null; }
        @Override public Void visitBasicBlock(final BasicBlock value)  { return null; }
        @Override public Void visitGlobalVar(final GlobalVar value)    { return null; }
        @Override public Void visitConstant(final Constant value)      { return null; }
        @Override public Void visitFunction(final Function value)      { return null; }

        private final InstCache cache = new InstCache();
    }

}
