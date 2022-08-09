package pass.ir.loop;

import frontend.SourceCodeSymbol;
import ir.BasicBlock;
import ir.Function;
import ir.Module;
import ir.Value;
import ir.inst.BrCondInst;
import ir.inst.BrInst;
import ir.inst.PhiInst;
import pass.ir.ConstructDominatorInfo;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import pass.ir.IRPass;
import utils.CollectionTools;
import utils.Log;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 收集 IR 中的循环并将其转化为 "正规" 的循环
 * <p>
 * 正规循环的定义详见: <a href="https://llvm.org/docs/LoopTerminology.html#loop-simplify-form">LLVM 循环技术</a>
 */
public class CollectLoopsAndMakeItCanonical {
    public static class DryRunPass implements IRPass {
        @Override
        public void runPass(final Module module) {
            final var collector = new CollectLoopsAndMakeItCanonical();
            final var loopCount = module.getNonExternalFunction().stream()
                .map(collector::collect).mapToLong(List::size).sum();
            Log.info("Collect loop: #" + loopCount);
        }
    }

    public List<CanonicalLoop> collect(Function function) {
        final var domInfoConstructor = new ConstructDominatorInfo();
        domInfoConstructor.runOnFunction(function);

        return collectLoops(function).stream()
            .map(loop -> transformToCanonicalLoop(null, loop))
            .collect(Collectors.toList());
    }

    CanonicalLoop transformToCanonicalLoop(CanonicalLoop parent, JustLoop justLoop) {
        final var header = justLoop.header;

        // 因为 SysY 是完全结构化的语言, 循环中可能的跳转只有 break
        // 所以循环体中所有往外的跳转必然都只会跳转到 while_exit
        // 相当于其天然地满足 CanonicalLoop 对唯一出口的条件
        // 并且由于 header 必然会往 exit 跳, 所以如果有 break 的话 exit 必然有两个前继, 从而会有 closing phi
        // 如果没有 break 的话, exit 只能用 header 的 phi, 那么就要考虑将外面的所有对 cond 里面的值的使用换成 exit 里的 phi 了

        // 但是循环中的 continue 语句将会产生多个 latch, 我们需要将这些 latch 合并起来构造新块

        // header 中的 phi 将会有来自外界的与来自循环内的, 我们要将它们分开
        // 来自外界的归到 pre-header 中成为 pre-header 中的 phi
        // 来自循环体内的归到 latch 中成为 latch 的 phi
        // 最后再把 header 中的 phi 改成合并 latch 与 pre-header 两处的 phi 的 phi

        final var predOfHeader = header.getPredecessors();
        final var predOutsideIndices = CollectionTools.findForIndices(predOfHeader, block -> !justLoop.body.contains(block));
        final var predInsideIndices  = CollectionTools.findForIndices(predOfHeader, block -> justLoop.body.contains(block));

        final var preHeaderInfo  = createOrReuseBlockAs("pre_header", header, predOutsideIndices);
        final var latchInfo      = createOrReuseBlockAs("latch", header, predInsideIndices);

        IRPass.copyForChange(header.getPredecessors()).forEach(header::removePredecessorWithPhiUpdated);
        header.addPredecessor(preHeaderInfo.block);
        header.addPredecessor(latchInfo.block);

        CollectionTools.iterWithIndex(header.phis(), (idx, phi) -> {
            final var newIncoming = List.of(preHeaderInfo.newIncomings.get(idx), latchInfo.newIncomings.get(idx));
            phi.setIncomingCO(newIncoming);
        });

        // 因为 SysY 中没有 do-while 循环, 所以只可能在循环不变量外提的时候产生 rotated loop
        // 而在循环不变量外提运行之前本分析就将运行完成, 所以我们应该理论上不会碰到 rotated loop...

        // 事实上就算碰上了, 其它优化最多能把 pre-header 跟 header 合在一起, 所以有上面的检测就足够维护结构了
        // 为了保险, 在最后再检测一下是不是 rotated loop

        final var canonicalLoop = new CanonicalLoop(parent, header);
        if (latchInfo.block.getTerminator() instanceof BrCondInst) {
            canonicalLoop.markAsRotated();
        }
        justLoop.body.forEach(canonicalLoop::addBodyBlock);
        canonicalLoop.addBodyBlock(latchInfo.block);

        // 处理 loop 森林
        for (final var subLoop : justLoop.subLoops) {
            final var subCanonicalLoop = transformToCanonicalLoop(canonicalLoop, subLoop);
            canonicalLoop.addSubLoop(subCanonicalLoop);
        }

        canonicalLoop.verify();

        return canonicalLoop;
    }

    static class NewBlocksInfo {
        public NewBlocksInfo(final BasicBlock block, final List<? extends Value> newIncomings) {
            this.block = block;
            this.newIncomings = newIncomings;
        }

        final BasicBlock block;
        final List<? extends Value> newIncomings;
    }
    NewBlocksInfo createOrReuseBlockAs(String name, BasicBlock header, List<Integer> inheritIndices) {
        if (inheritIndices.size() > 1) {
            // need a new block
            final var newBB = createEmptyBlockWithInfoFromSymbol(name, header.getSymbol());

            fillBlockWithPhiInherited(header, newBB, inheritIndices);
            newBB.add(new BrInst(newBB, header));
            newBB.adjustPhiEnd();

            return new NewBlocksInfo(newBB, newBB.phis());

        } else {
            final var oldBBIndex = inheritIndices.get(0);
            final var oldBB = header.getPredecessors().get(oldBBIndex);
            final var oldBBIncomings = header.phis().stream()
                .map(phi -> phi.getIncomingValue(oldBBIndex)).collect(Collectors.toList());

            return new NewBlocksInfo(oldBB, oldBBIncomings);
        }
    }

    static void fillBlockWithPhiInherited(BasicBlock oldBB, BasicBlock newBB, List<Integer> inheritIndices) {
        // 从旧块中将对应位置的 phi 参数抢过来成为新的 phi 参数
        for (final var phi : oldBB.phis()) {
            final var newPhi = new PhiInst(phi.getType(), phi.getWaitFor());

            final var incomingValueOutsideLoop = CollectionTools.selectFrom(phi.getIncomingValues(), inheritIndices);
            newPhi.setIncomingValueWithoutCheckingPredecessorsCO(incomingValueOutsideLoop);

            newBB.addPhi(newPhi);
        }
        // adjustPhiEnd 是迭代到第一个非 phi 指令
        // 现在基本块里只有 phi, 没有其它指令, 还不能调用 adjustPhiEnd
        // newBB.adjustPhiEnd();

        // 然后更新外面前继的指向
        final var outsidePreds = CollectionTools.selectFrom(oldBB.getPredecessors(), inheritIndices);
        for (final var outsidePred : outsidePreds) {
            outsidePred.getTerminator().replaceOperandCO(oldBB, newBB);
            newBB.addPredecessor(outsidePred);
        }
    }

    BasicBlock createEmptyBlockWithInfoFromSymbol(String name, SourceCodeSymbol symbol) {
        final var block = BasicBlock.createFreeBBlock(symbol.newSymbolWithName(name));
        block.addAnalysisInfo(new LoopBlockInfo());
        return block;
    }

    JustLoop currLoop = null;
    List<JustLoop> collectLoops(Function function) {
        final var topLevelLoops = new ArrayList<JustLoop>();

        for (final var block : function) {
            // 理论上基本块都是按顺序排列的
            // 那么当我们第一次碰到不在当前循环里的块的时候, 我们就离开了这个循环了
            while (currLoop != null && !currLoop.body.contains(block)) {
                currLoop = currLoop.parent.orElse(null);
            }
            // 确保弹出一次 currLoop 之后, 当前块在 currLoop 里
            Log.ensure(currLoop == null || currLoop.body.contains(block));

            final var domChildrenInPred = block.getPredecessors().stream()
                .filter(pred -> DominatorInfo.dom(pred).contains(block)).collect(Collectors.toList());

            if (!domChildrenInPred.isEmpty()) {
                currLoop = new JustLoop(currLoop, block);
                domChildrenInPred.forEach(this::collectBlocksInLoop);

                if (currLoop.parent.isEmpty()) {
                    topLevelLoops.add(currLoop);
                }
            }
        }

        return topLevelLoops;
    }

    void collectBlocksInLoop(BasicBlock block) {
        currLoop.body.add(block);

        block.getPredecessors().stream()
            // 防止无限循环或超出循环范围
            .filter(pred -> !currLoop.body.contains(pred) && pred != currLoop.header)
            // 理论上基本块都是按顺序排列的
            // 一个块如果在一个内层循环里, 那它必然会先加入到外层循环,再加入到内层循环中
            // 那么最后这个块就会在最内层循环里了
            .forEach(this::collectBlocksInLoop);
    }
}

class JustLoop {
    JustLoop(JustLoop parent, BasicBlock header) {
        this.header = header;
        this.body = new LinkedHashSet<>();

        this.parent = Optional.ofNullable(parent);
        this.subLoops = new ArrayList<>();
    }

    BasicBlock header;
    Set<BasicBlock> body;

    Optional<JustLoop> parent;
    List<JustLoop> subLoops;
}