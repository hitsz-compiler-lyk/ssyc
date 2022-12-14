package pass.ir;

import frontend.SourceCodeSymbol;
import ir.Module;
import ir.*;
import ir.inst.*;
import pass.ir.util.MultiBasicBlockCloner;
import utils.Log;

import java.util.*;

public class FunctionInline implements IRPass {
    @Override
    public void runPass(final Module module) {
        GlobalModificationStatus.doUntilNoChange(() ->
            IRPass.instructionStream(module)
                .filter(CallInst.class::isInstance).map(CallInst.class::cast)
                .filter(this::canInline)
                .forEach(this::doInline)
        );
    }

    private boolean canInline(CallInst call) {
        final var callee = call.getCallee();
        return !callee.isExternal() && isSimpleFunction(callee);
    }

    private boolean isSimpleFunction(Function func) {
        return func.stream()
            .flatMap(List<Instruction>::stream)
            .filter(CallInst.class::isInstance).map(CallInst.class::cast)
            .allMatch(call -> call.getCallee().isExternal());
    }

    private static int blockCount = 0;
    static String randomPrefix() {
        final var prefix = new char[4];

        var exp = 1;
        for (int i = prefix.length - 1; i >= 0; i--) {
            prefix[i] = Character.forDigit(blockCount / exp % 10, 10);
            exp *= 10;
        }

        blockCount += 1;
        return new String(prefix);
    }

    void doInline(CallInst callInst) {
        /*
         *                                           │   │    │
         *       │    │    │                         │   │    │ BB_inline_front
         *  BB   │    │    │                   ┌─────▼───▼────▼───────┐
         * ┌─────▼────▼────▼──────┐            │                      │
         * │                      │            │ Other Inst...        │    ┌───────────────────┐
         * │                      │            └──────┬───────────────┘    │                   │
         * │  Other Inst...       │                   │                    │ Blocks from callee│
         * │                      │                   └────────────────────►                   │
         * │ ┌──────────────────┐ │  Become                                │    ......         │
         * │ │ Call Inst        │ │ ───────►    BB_inline_exit             │                   │
         * │ └──────────────x───┘ │           ┌────────────────────────┐   └───┬───┬──┬────────┘
         * │                x     │           │                        │       │   │  │
         * │  Other Inst... x     │ Become    │ ─────────────────────┐ ◄───────┘   │  │
         * │                xxxxxxxxxxxxxxxxxxxxx Phi Inst for       │ │           │  │
         * │                      │           │ │ different return   │ ◄───────────┘  │
         * └──────┬────────┬──────┘           │ └────────────────────┘ │              │
         *        │        │                  │                        ◄──────────────┘
         *        ▼        ▼                  └───────────┬────────────┘
         *                                                │
         *                                                │ BB_inline_back
         *                                    ┌───────────▼────────────┐
         *                                    │                        │
         *                                    │ Other Inst...          │
         *                                    └──────┬──────────┬──────┘
         *                                           │          │
         *                                           ▼          ▼
         */

        final var callerBlock = callInst.getParent();
        final var calleeFunc = callInst.getCallee();
        final var callerBBSym = callerBlock.getSymbol();
        final var callerFunc = callerBlock.getParent();
        final var prefix = "_" + randomPrefix();

        final var splitResult = splitList(callerBlock, callInst);

        // 处理 frontBB (该块的 Call 指令之前的指令要放的新块)
        final var frontBB = BasicBlock.createFreeBBlock(callerBBSym.newSymbolWithSuffix(prefix + "_inline_front"));
        // 接手原基本块 CallInst 前面的所有指令
        for (final var frontInst : splitResult.front) {
            frontBB.addInstAtEnd(frontInst);
        }
        callerBlock.adjustPhiEnd();
        // 接手原基本块的所有前继
        final var oldPreds = new ArrayList<>(callerBlock.getPredecessors());
        for (final var pred : oldPreds) {
            // 从原基本块中删除前继
            callerBlock.removePredecessorWithPhiUpdated(pred);
            // 将其前继到原块的跳转改成到自己的跳转
            pred.getTerminator().replaceOperandCO(callerBlock, frontBB);
            // 将前继加到自己的前继列表中
            frontBB.addPredecessor(pred);
        }

        // 处理 backBB (该块的 Call 指令之后的指令要放的新块)
        final var backBB = BasicBlock.createFreeBBlock(callerBBSym.newSymbolWithSuffix(prefix + "_inline_back"));
        // 接手后继
        // 因为后继是通过 Br 指令获取的, 所以要在移除所有指令之前处理
        final var oldSuccs = new ArrayList<>(callerBlock.getSuccessors());
        for (final var succ : oldSuccs) {
            // 让所有后继的前继从原本的块变成 backBB
            succ.replacePredecessor(callerBlock, backBB);
        }
        // 接手原基本块 CallInst 后面的所有指令
        // 因为后继是通过 Br 指令获取的, 所以不需要特地维护 backBB 的后继列表
        for (final var backInst : splitResult.back) {
            backBB.addInstAtEnd(backInst);
        }

        // 处理 exitBB (内联的函数的 "return" 指令要跳转到的块)
        final var exitBB = BasicBlock.createFreeBBlock(callerBBSym.newSymbolWithSuffix(prefix + "_inline_exit"));
        final var returnType = calleeFunc.getType().getReturnType();
        if (!returnType.isVoid()) {
            // 构造用于存返回值的 Phi
            final var returnValueSym = new SourceCodeSymbol(calleeFunc.getFunctionSourceName() + prefix + "_inline_return", 0, 0);
            final var returnValuePhi = new PhiInst(calleeFunc.getType().getReturnType(), returnValueSym);
            exitBB.addInstAtEnd(returnValuePhi);
            // 将对 CallInst 的引用全部换成对返回值的 Phi 的引用
            callInst.replaceAllUseWith(returnValuePhi);
        }
        // 构造最后的跳转
        exitBB.addInstAtEnd(new BrInst(exitBB, backBB));

        // 开始函数内联
        // 获取形参到实参的映射
        final var paramToArg = new HashMap<Parameter, Value>();
        final var args = callInst.getArgList();
        final var params = calleeFunc.getParameters();
        Log.ensure(args.size() == params.size(), "Parameters' amount must match arguments'");
        final var paramSize = params.size();
        for (int i = 0; i < paramSize; i++) {
            paramToArg.put(params.get(i), args.get(i));
        }

        // 将 call 指令从原块中移除并清理其使用的 operand
        // 之所以现在才清理, 是因为前面获取形参到实参的映射部分还需要用到 CallInst 的 operand
        callInst.freeAll();

        // 进行拷贝
        final var cloner = new FunctionInlineCloner(calleeFunc, paramToArg, exitBB, prefix);
        final var calleeNewBlocks = cloner.convert();

        // 插入从 frontBB 到内联进来的函数的 entry 块的跳转
        frontBB.addInstAtEnd(new BrInst(frontBB, calleeNewBlocks.get(0)));
        // 调整各个新块的 phiEnd
        frontBB.adjustPhiEnd();
        backBB.adjustPhiEnd();
        exitBB.adjustPhiEnd();


        // 整理待加入 callerFunc 的基本块列表
        final var newBlocks = new ArrayList<BasicBlock>();
        newBlocks.add(frontBB);
        newBlocks.addAll(calleeNewBlocks);
        newBlocks.add(exitBB);
        newBlocks.add(backBB);

        // 移除旧的块, 加入新的块
        final var oldBlockIndex = callerFunc.indexOf(callerBlock);
        callerBlock.freeAll();
        callerFunc.addAll(oldBlockIndex, newBlocks);
    }

    static class SplitResult<T> {
        final List<T> front = new ArrayList<>();
        final List<T> back = new ArrayList<>();
    }

    private static <T> SplitResult<T> splitList(List<T> list, T splitPos) {
        final var result = new SplitResult<T>();
        final var iter = list.listIterator();

        while (iter.hasNext()) {
            final var next = iter.next();
            if (next == splitPos) {
                break;
            }

            result.front.add(next);
        }

        while (iter.hasNext()) {
            result.back.add(iter.next());
        }

        return result;
    }

    /** 完成函数内联所需要的基本块和指令的复制 */
    static class FunctionInlineCloner extends MultiBasicBlockCloner {
        public FunctionInlineCloner(
            Function callee,
            Map<Parameter, Value> paramToArgs, BasicBlock exitBB, String prefix
        ) {
            super(new LinkedHashSet<>(callee));
            super.replaceInstructionCloner(new FunctionInlineInstructionCloner());
            super.oldToNew.putAll(paramToArgs);

            this.callee = callee;
            this.exitBB = exitBB;
            this.prefix = prefix;
        }

        public List<BasicBlock> convert() {
            final var newBBs = super.convert(callee);

            if (!callee.getType().getReturnType().isVoid()) {
                final var first = exitBB.get(0);
                Log.ensure(first instanceof PhiInst);
                assert first instanceof PhiInst;

                ((PhiInst) first).setIncomingCO(returnValues);
            }

            return newBBs;
        }


        @Override
        protected BasicBlock createNewBBFromOld(final BasicBlock oldBB) {
            return BasicBlock.createFreeBBlock(oldBB.getSymbol().newSymbolWithSuffix(prefix + "_inline"));
        }

        @Override
        protected BasicBlock getOtherBB(final BasicBlock blockShouldNotBeCloned) {
            Log.ensure(false, "Any block in a function should NOT jump to block in other function");
            throw new RuntimeException();
        }

        @Override
        public Value visitParameter(final Parameter value) {
            Log.ensure(oldToNew.containsKey(value), "FunctionInlineCloner 必须包含完整的形参映射");
            return oldToNew.get(value);
        }

        // 全局变量, 函数与常量不需要被复制

        private final Function callee;
        private final BasicBlock exitBB;
        private final String prefix;
        private final List<Value> returnValues = new ArrayList<>();

        /** <h3>完成函数内联所需要的指令的复制</h3>
         * <p>基本上就是对各条指令, 获取其参数, 随后尝试获取旧参数所对应的新的值, 没有就创建.</p>
         * <p>但是对 Return 指令做了特殊处理, 将其变为跳转到 exitBB 的语句 (有返回值的话还记录了返回值是什么)</p>
         */
        class FunctionInlineInstructionCloner extends MultiBasicBlockInstructionCloner {
            @Override
            public Instruction visitReturnInst(final ReturnInst inst) {
                // return 换成 br
                final var currBB = getOrCreate(inst.getParent());
                inst.getReturnValue().map(FunctionInlineCloner.this::getOrCreate).ifPresent(returnValues::add);
                return new BrInst(currBB, exitBB);
            }
        }
    }
}
