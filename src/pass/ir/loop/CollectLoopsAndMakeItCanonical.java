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
            // TODO: 干脆在生成的时候直接 drop 掉没有 unique exit 的循环好了
            // 这种循环没人权也没优化的
            .filter(CanonicalLoop::hasUniqueExit)
            // 同理, 反转的 do-while 循环也无优化机会的
            .filter(CanonicalLoop::isRotated)
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

        // 此时 CanonicalLoop 已经有 body 了, 可以调用 getUniqueExit 了
        // 将 pre-header 插入于 header 之前, latch 插入于 exit 之前 (即 body 的所有块之后)
        canonicalLoop.getHeader().insertBeforeCO(preHeader);
        if (canonicalLoop.hasUniqueExit()) {
            canonicalLoop.getUniqueExit().insertBeforeCO(latch);
        } else {
            canonicalLoop.getHeader().insertAfterCO(latch);
        }

        // 验证并返回
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
        final var block = BasicBlock.createFreeBBlock(symbol.newSymbolWithSuffix(name));
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
                } else {
                    // 有时候会有触及不到的情况
                    // 考虑功能性样例 55, CFG 如下的情况:
                    // ^> A
                    // |  v
                    // C< B <-> D
                    // BD 组成的循环相当于 "外挂" 在外层循环 ABC 上, 这时候做前继闭包是做不到 D 的
                    final var parent = currLoop.parent.get();
                    parent.body.addAll(currLoop.body);
                }
            }
        }

        return topLevelLoops;
    }

    void collectBlocksInLoop(BasicBlock block) {
        if (block == currLoop.header) {
            return;
        }

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