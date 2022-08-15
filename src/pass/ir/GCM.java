package pass.ir;

import ir.BasicBlock;
import ir.Function;
import ir.Module;
import ir.inst.CallInst;
import ir.inst.Instruction;
import ir.inst.LoadInst;
import ir.inst.PhiInst;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import pass.ir.loop.CollectLoops;
import pass.ir.loop.JustLoopBlockInfo;
import utils.INodeOwner;
import utils.Log;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * GCM: Global Code Motion
 */
public class GCM implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().forEach(this::runOnFunction);
    }

    public void runOnFunction(Function function) {
        CollectLoops.allAndaddToBlockInfo(function);

        final var domConstructor = new ConstructDominatorInfo();
        domConstructor.runOnFunction(function);

        visited.clear();
        function.stream().flatMap(List::stream).forEach(this::moveToEarly);

        visited.clear();
        function.stream().flatMap(List::stream).forEach(this::moveToLate);
    }

    private final Set<Instruction> visited = new HashSet<>();
    private boolean hasVisited(Instruction instruction) {
        return visited.contains(instruction);
    }
    private void markAsVisited(Instruction instruction) {
        visited.add(instruction);
    }


    void moveToEarly(Instruction instruction) {
        if (canNotBeMoved(instruction) || hasVisited(instruction)) {
            return;
        }

        markAsVisited(instruction);

        // 先将使用的参数尽可能地往上移
        final var opBlocks = instruction.getOperands().stream()
            .filter(Instruction.class::isInstance).map(Instruction.class::cast)
            .peek(this::moveToEarly)
            .map(INodeOwner::getParent)
            .collect(Collectors.toList());

        // 真的会有没有参数的 Instruction 吗? 好像没有吧...
        final var earliestBlockOpt = max(opBlocks, DominatorInfo::domTreeDepth);
        earliestBlockOpt.ifPresent(earliestBlock -> {
            if (earliestBlock == instruction.getParent()) {
                return;
            }

            instruction.freeFromIList();
            earliestBlock.addInstBeforeTerminator(instruction);
        });
    }

    void moveToLate(Instruction instruction) {
        if (canNotBeMoved(instruction) || hasVisited(instruction)) {
            return;
        }

        markAsVisited(instruction);

        // 先将所有 user 尽可能地往下移
        final var userBlocks = instruction.getUserList().stream()
            .filter(Instruction.class::isInstance).map(Instruction.class::cast)
            .peek(this::moveToLate)
            .map(INodeOwner::getParent)
            .collect(Collectors.toList());

        final var loopDepthInEarliest = loopDepth(instruction);

        final var userLCAOpt = userBlocks.stream().reduce(GCM::lcaInDomTree);
        Log.ensure(userLCAOpt.isPresent(), "A non-user non-void instruction should NOT even exist");

        // 找到循环深度与最早的块相同的支配树中最深的块
        var targetBlock = userLCAOpt.get();
        while (loopDepth(targetBlock) > loopDepthInEarliest) {
            targetBlock = DominatorInfo.idom(targetBlock);
        }

        if (targetBlock != instruction.getParent()) {
            instruction.freeFromIList();
            // 因为有可能 user 就在这个块里, 所以要放到最前面去来支配所有 user
            targetBlock.addInstAfterPhi(instruction);
            // targetBlock.addInstBeforeTerminator(instruction);
        }
    }

    int loopDepth(Instruction instruction) {
        return loopDepth(instruction.getParent());
    }

    int loopDepth(BasicBlock block) {
        return block.getAnalysisInfo(JustLoopBlockInfo.class).getLoopDepth();
    }

    boolean canNotBeMoved(Instruction instruction) {
        return instruction.getType().isVoid()   // 类型为 void 的必然是带副作用的, 带副作用的不能被调度
            || instruction instanceof CallInst  // Call 有可能带副作用, 不调度
            || instruction instanceof PhiInst   // Phi 是位置相关的指令, 不调度
            || instruction instanceof LoadInst; // Load 的值有可能是位置相关的, 不调度
        // 其它指令都可以被调度
    }

    static BasicBlock lcaInDomTree(BasicBlock a, BasicBlock b) {
        // make sure that a is deeper than b
        if (!(domDepth(a) > domDepth(b))) {
            final var tmp = a;
            a = b;
            b = tmp;
        }

        while (domDepth(a) > domDepth(b)) {
            a = DominatorInfo.idom(a);
        }

        while (a != b) {
            a = DominatorInfo.idom(a);
            b = DominatorInfo.idom(b);
        }

        return a;
    }

    private static int domDepth(BasicBlock block) {
        return DominatorInfo.domTreeDepth(block);
    }

    static <T> Optional<T> max(Collection<T> elms, ToIntFunction<T> keyFunction) {
        int currMax = Integer.MIN_VALUE;
        T currMaxElm = null;

        for (final var elm : elms) {
            final var key = keyFunction.applyAsInt(elm);
            if (key > currMax) {
                currMax = key;
                currMaxElm = elm;
            }
        }

        return Optional.ofNullable(currMaxElm);
    }


}
