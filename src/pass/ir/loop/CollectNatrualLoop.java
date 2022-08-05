package pass.ir.loop;

import ir.BasicBlock;
import ir.Function;
import ir.Module;
import pass.ir.ConstructDominatorInfo;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import pass.ir.IRPass;
import utils.Log;

import java.util.stream.Collectors;

public class CollectNatrualLoop implements IRPass {
    @Override
    public void runPass(final Module module) {
        final var domInfo = new ConstructDominatorInfo();
        domInfo.runPass(module);

        module.getNonExternalFunction().stream()
            .peek(this::insertInfo)
            .forEach(this::runOnFunction);
        Log.ensure(currLoop == null);
    }

    void insertInfo(Function function) {
        function.addOrOverwriteInfo(new LoopFunctionInfo());
        function.forEach(block -> block.addOrOverwriteInfo(new LoopBlockInfo()));
    }

    NaturalLoop currLoop = null;
    void runOnFunction(Function function) {
        final var funcInfo = function.getAnalysisInfo(LoopFunctionInfo.class);
        for (final var block : function) {
            // 理论上基本块都是按顺序排列的
            // 那么当我们第一次碰到不在当前循环里的块的时候, 我们就离开了这个循环了
            if (currLoop != null && !currLoop.getBlocks().contains(block)) {
                currLoop = currLoop.getParent().orElse(null);
            }
            // 确保弹出一次 currLoop 之后, 当前块在 currLoop 里
            Log.ensure(currLoop == null || currLoop.getBlocks().contains(block));

            final var doms = DominatorInfo.dom(block);
            final var domChildrenInPred = block.getPredecessors().stream()
                .filter(doms::contains).collect(Collectors.toList());

            if (!domChildrenInPred.isEmpty()) {
                currLoop = new NaturalLoop(currLoop, block);
                funcInfo.addLoop(currLoop);

                domChildrenInPred.forEach(this::collectBlocksInLoop);
            }
        }
    }

    void collectBlocksInLoop(BasicBlock block) {
        for (final var pred : block.getPredecessors()) {
            if (currLoop.getBlocks().contains(pred)) {
                continue;
            }

            // 理论上基本块都是按顺序排列的
            // 一个块如果在一个内层循环里, 那它必然会先加入到外层循环,再加入到内层循环中
            // 那么最后这个块就会在最内层循环里了
            currLoop.addBlockCO(pred);
            collectBlocksInLoop(pred);
        }
    }
}
