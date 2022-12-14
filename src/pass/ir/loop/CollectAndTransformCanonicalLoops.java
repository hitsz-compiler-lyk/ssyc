package pass.ir.loop;

import frontend.SourceCodeSymbol;
import ir.BasicBlock;
import ir.Function;
import ir.Module;
import ir.Value;
import ir.inst.BrCondInst;
import ir.inst.BrInst;
import ir.inst.PhiInst;
import pass.ir.IRPass;
import utils.CollectionTools;
import utils.Log;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收集 IR 中的循环并将其转化为 "正规" 的循环
 * <p>
 * 正规循环的定义详见: <a href="https://llvm.org/docs/LoopTerminology.html#loop-simplify-form">LLVM 循环技术</a>
 */
public class CollectAndTransformCanonicalLoops {
    public static class DryRunPass implements IRPass {
        @Override
        public void runPass(final Module module) {
            final var collector = new CollectAndTransformCanonicalLoops();
            final var loopCount = module.getNonExternalFunction().stream()
                .map(collector::collect).mapToLong(List::size).sum();
            Log.info("Collect loop: #" + loopCount);
        }
    }

    public List<CanonicalLoop> collect(Function function) {
        // collect loops 会构造 dom 信息的
        return CollectLoops.topLevel(function).stream()
            .map(loop -> transformToCanonicalLoop(null, loop))
            .collect(Collectors.toList());
    }

    CanonicalLoop transformToCanonicalLoop(CanonicalLoop parent, JustLoop justLoop) {
        final var header = justLoop.header;

        if (justLoop.body.isEmpty()) {
            // 特殊情况, header 与 latch 合二为一了
            // 那我们只能把 header 最后的 Br/BrCond 拆出来单独成块, 形成一个 header <-> latch 的 rotated loop 了
            // 并且这种情况下显然不可能再有子循环了

            final var latch = createEmptyBlockWithInfoFromSymbol("latch", header.getSymbol());
            header.insertAfterCO(latch);

            final var oldSucc = header.getSuccessors();
            latch.add(header.getTerminator());
            for (final var succ : oldSucc) {
                succ.replacePredecessor(header, latch);
            }

            header.add(new BrInst(header, latch));

            final var loop = new CanonicalLoop(parent, header);
            loop.markAsRotated();

            return loop;
        }

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

        // 考虑到多个函数内联到同一个函数的情况, 必须是添加后缀而不能直接使用新名字
        final var preHeaderInfo  = createOrReuseBlockAs("_pre_header", header, predOutsideIndices);
        final var latchInfo      = createOrReuseBlockAs("_latch", header, predInsideIndices);

        final var preHeader = preHeaderInfo.block;
        final var latch = latchInfo.block;

        // 重新构建 header 的前继与 phi
        IRPass.copyForChange(header.getPredecessors()).forEach(header::removePredecessorWithPhiUpdated);
        // 注意顺序
        preHeader.add(new BrInst(preHeader, header));
        latch.add(new BrInst(latch, header));

        preHeader.adjustPhiEnd();
        latch.adjustPhiEnd();

        CollectionTools.iterWithIndex(header.phis(), (idx, phi) -> {
            final var newIncoming = List.of(preHeaderInfo.newIncomings.get(idx), latchInfo.newIncomings.get(idx));
            phi.setIncomingCO(newIncoming);
        });

        // 构造 CanonicalLoop
        final var canonicalLoop = new CanonicalLoop(parent, header);

        // 将 preHeader 与 latch 其插入到上层 loop 的 body 中
        justLoop.parent.ifPresent(superLoop -> superLoop.body.add(preHeader));
        justLoop.parent.ifPresent(superLoop -> superLoop.body.add(latch));

        // 此时 CanonicalLoop 已经有 body 了, 可以调用 getUniqueExit 了
        // 将 pre-header 插入于 header 之前, latch 插入于循环的所有块之后
        // 因为有可能 latch 块是 loop body 里的唯一一块, 所以 justLoop.body 有可能所有块都不在 function 内, 因此需要把 header 也考虑进去
        // 由于上层循环的 latch 有可能是本层循环的 exit, 所以必须在访问内部循环之前先把 latch 插入回 function
        canonicalLoop.getHeader().insertBeforeCO(preHeader);
        final var function = header.getParent();
        final var maxIndexInBody = findLastIndexInBlocks(function, justLoop.getAll());
        function.add(maxIndexInBody + 1, latch);

        // 处理 loop 森林
        for (final var subLoop : justLoop.subLoops) {
            final var subCanonicalLoop = transformToCanonicalLoop(canonicalLoop, subLoop);
            canonicalLoop.addSubLoop(subCanonicalLoop);
        }

        // 正是因为下层 loop 有可能给上层 loop 插入新节点, 所以必须先构造 CanonicalLoop, 然后执行插入,
        // 随后还要先处理完子循环, 确保 body 完整之后再将 loop 的 body 加入


        // 因为 SysY 中没有 do-while 循环, 所以只可能在循环不变量外提的时候产生 rotated loop
        // 而在循环不变量外提运行之前本分析就将运行完成, 所以我们应该理论上不会碰到 rotated loop...

        // 事实上就算碰上了, 其它优化最多能把 pre-header 跟 header 合在一起, 所以有上面的检测就足够维护结构了
        // 为了保险, 在最后再检测一下是不是 rotated loop

        if (!(header.getTerminator() instanceof BrCondInst) && latch.getTerminator() instanceof BrCondInst) {
            canonicalLoop.markAsRotated();
        }
        // 将 body 加入其中
        justLoop.body.forEach(canonicalLoop::addBodyBlock);
        canonicalLoop.addBodyBlock(latch);

        // 验证并返回
        canonicalLoop.verify();
        return canonicalLoop;
    }

    int findLastIndexInBlocks(Function function, Set<BasicBlock> blocks) {
        var maxIndex = -1;
        var currIndex = 0;

        for (final var block : function) {
            if (blocks.contains(block) && currIndex > maxIndex) {
                maxIndex = currIndex;
            }
            currIndex += 1;
        }

        Log.ensure(maxIndex != -1);
        return maxIndex;
    }

    record NewBlocksInfo(BasicBlock block, List<? extends Value> newIncomings) {}
    NewBlocksInfo createOrReuseBlockAs(String name, BasicBlock header, List<Integer> inheritIndices) {
        // 如果这种前继只有一个
        if (inheritIndices.size() == 1) {
            final var oldBBIndex = inheritIndices.get(0);
            final var oldBB = header.getPredecessors().get(oldBBIndex);

            // 且这个前继的后继唯一就是自己
            // 这个前继的后继不唯一的情况详见功能性样例 55
            // 两个 while 循环直接并排, 有可能内层 while_cond 直接把外层 while_cond 当作 pre-header 了
            if (oldBB.getSuccessors().size() == 1) {
                final var oldBBIncomings = header.phis().stream()
                    .map(phi -> phi.getIncomingValue(oldBBIndex)).collect(Collectors.toList());

                // 删掉 oldBB 的 terminator, 使其与新创建的块一样是缺尾部的, 方便后续代码处理
                oldBB.getTerminator().freeAll();
                // 将 oldBB 移出列表使其与新创建的块一样是自由的, 方便后续代码处理
                oldBB.freeFromIList();

                return new NewBlocksInfo(oldBB, oldBBIncomings);
            }
        }

        // need a new block
        final var newBB = createEmptyBlockWithInfoFromSymbol(name, header.getSymbol());
        fillBlockWithPhiInherited(header, newBB, inheritIndices);
        return new NewBlocksInfo(newBB, newBB.phis());
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
        return BasicBlock.createFreeBBlock(symbol.newSymbolWithSuffix(name));
    }
}