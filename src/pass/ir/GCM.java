package pass.ir;

import ir.BasicBlock;
import ir.Function;
import ir.Module;
import ir.Value;
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
        // loop 会构造 Dom 信息的
        CollectLoops.allAndaddToBlockInfo(function);

        final var shouldBeMove = function.stream()
            .flatMap(List::stream)
            .filter(this::canNotBeMoved)
            .collect(Collectors.toList());

        shouldBeMove.forEach(this::getEarliestBlock);
        shouldBeMove.forEach(this::getLatestBlock);
        shouldBeMove.forEach(this::moveInstruction);

        // 有可能用 phiEnd 插指令到块开头, 所以要调整一下
        function.forEach(BasicBlock::adjustPhiEnd);
    }

    private final HashMap<Instruction, BasicBlock> earliest = new HashMap<>();
    BasicBlock getEarliestBlock(Instruction instruction) {
        if (canNotBeMoved(instruction)) {
            return instruction.getParent();
        }

        if (!earliest.containsKey(instruction)) {
            // 先将使用的参数尽可能地往上移
            final var opBlocks = instruction.getOperands().stream()
                .filter(Instruction.class::isInstance).map(Instruction.class::cast)
                .map(this::getEarliestBlock)
                .collect(Collectors.toList());

            // 真的会有没有 Instruction 做参数的 Instruction 吗? 好像没有吧... 有的都被常数折叠折掉了
            // 如果真的什么都不依赖, 那么最早完全可以去到 entry 块
            final var earliestBlockOpt = max(opBlocks, DominatorInfo::domTreeDepth);
            earliest.put(instruction, earliestBlockOpt.orElseGet(
                () -> instruction.getParent().getParent().getEntryBBlock()));
        }

        return earliest.get(instruction);
    }

    private final HashMap<Instruction, BasicBlock> latest = new HashMap<>();
    BasicBlock getLatestBlock(Instruction instruction) {
        if (canNotBeMoved(instruction)) {
            return instruction.getParent();
        }

        if (!latest.containsKey(instruction)) {
            // 先将所有 user 尽可能地往下移
            final var userBlocks = instruction.getUserList().stream()
                .filter(Instruction.class::isInstance).map(Instruction.class::cast)
                .map(this::getLatestBlock)
                .collect(Collectors.toList());

            final var userLCAOpt = userBlocks.stream().reduce(GCM::lcaInDomTree);
            Log.ensure(userLCAOpt.isPresent(), "A non-user non-void instruction should NOT even exist");
            latest.put(instruction, userLCAOpt.get());
        }

        return latest.get(instruction);
    }

    BasicBlock selectBlockBetweenEarliestAndLatest(Instruction instruction) {
        final var earliest  = getEarliestBlock(instruction);
        final var latest    = getLatestBlock(instruction);

        final var possibleSmallestLoopDepth = loopDepth(earliest);

        // 从最晚的块依次沿着支配树向上, 找到尽可能晚的循环深度与最早的块相同的块
        var targetBlock = latest;
        while (loopDepth(targetBlock) > possibleSmallestLoopDepth) {
            targetBlock = DominatorInfo.idom(targetBlock);
        }

        return targetBlock;
    }

    void moveInstruction(Instruction instruction) {
        final var targetBlock = selectBlockBetweenEarliestAndLatest(instruction);

        // Don't need to move
        if (targetBlock == instruction.getParent()) {
            return;
        }

        final var hasUserInBlock        = hasInstructionIn(targetBlock, instruction.getUserList());
        final var hasOperandsInBlock    = hasInstructionIn(targetBlock, instruction.getOperands());

        if (hasUserInBlock && hasOperandsInBlock) {
            Log.ensure(instruction.getParent() == targetBlock,
                "Only instruction in this block could have both user and operand in the block");
            throw new RuntimeException();
        } else if (hasUserInBlock) {
            // 因为有可能 user 就在这个块里, 所以要放到最前面去来支配所有 user
            targetBlock.addInstAfterPhi(instruction);
        } else if (hasOperandsInBlock) {
            // 因为有可能 operands 就在这个块里, 所以要放到最后面去确保被所有 operand 支配
            targetBlock.addInstBeforeTerminator(instruction);
        } else {
            // 随便乱放一个地方就可以了, 我挑了末尾
            targetBlock.addInstBeforeTerminator(instruction);
        }
    }

    private boolean hasInstructionIn(BasicBlock targetBlock, List<? extends Value> values) {
        return values.stream()
            .filter(Instruction.class::isInstance).map(Instruction.class::cast)
            .map(INodeOwner::getParent).anyMatch(block -> block == targetBlock);
    }

    private int loopDepth(BasicBlock block) {
        return block.getAnalysisInfo(JustLoopBlockInfo.class).getLoopDepth();
    }

    private boolean canBeMoved(Instruction instruction) {
        return !canNotBeMoved(instruction);
    }

    private boolean canNotBeMoved(Instruction instruction) {
        return instruction.getType().isVoid()   // 类型为 void 的必然是带副作用的, 带副作用的不能被调度
            || instruction instanceof CallInst  // Call 有可能带副作用, 不调度
            || instruction instanceof PhiInst   // Phi 是位置相关的指令, 不调度
            || instruction instanceof LoadInst; // Load 的值有可能是位置相关的, 不调度
        // 其它指令都可以被调度
    }

    static BasicBlock lcaInDomTree(BasicBlock a, BasicBlock b) {
        // make sure that A is deeper than B in dom tree
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
