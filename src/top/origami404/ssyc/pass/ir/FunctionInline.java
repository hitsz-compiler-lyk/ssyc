package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.frontend.SourceCodeSymbol;
import top.origami404.ssyc.ir.*;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.visitor.InstructionVisitor;
import top.origami404.ssyc.ir.visitor.ValueVisitor;
import top.origami404.ssyc.utils.Log;

import java.util.*;
import java.util.stream.Collectors;

public class FunctionInline {
    public static boolean run(Function function) {
        return false;
    }

    static void doInline(CallInst callInst) {
        /*
         *                                           │   │    │
         *       │    │    │                         │   │    │ BB_inline_front
         *  BB   │    │    │                   ┌─────▼───▼────▼───────┐
         * ┌─────▼────▼────▼──────┐            │                      │
         * │                      │            │ Other Inst...        │    ┌───────────────────┐
         * │                      │            └──────┬───────────────┘    │                   │
         * │  Other Inst...       │                   │                    │ Blocks from callee│
         * │                      │                   └────────────────────►                   │
         * │ ┌──────────────────┐ │  变成                                  │    ......         │
         * │ │ Call Inst        │ │ ───────►    BB_inline_exit             │                   │
         * │ └──────────────x───┘ │           ┌────────────────────────┐   └───┬───┬──┬────────┘
         * │                x     │           │                        │       │   │  │
         * │  Other Inst... x     │   变成    │ ─────────────────────┐ ◄───────┘   │  │
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

        final var splitResult = splitList(callerBlock.getIList(), callInst);

        // 处理 frontBB (该块的 Call 指令之前的指令要放的新块)
        final var frontBB = new BasicBlock(callerFunc, callerBBSym.newSymbolWithSuffix("_inline_front"));
        // 接手原基本块 CallInst 前面的所有指令
        for (final var frontInst : splitResult.front) {
            frontBB.getIList().add(frontInst);
        }
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
        final var backBB = new BasicBlock(callerFunc, callerBBSym.newSymbolWithSuffix("_inline_back"));
        // 接手后继
        // 因为后继是通过 Br 指令获取的, 所以要在移除所有指令之前处理
        final var oldSuccs = new ArrayList<>(backBB.getSuccessors());
        for (final var succ : oldSuccs) {
            // 让所有后继的前继从原本的块变成 backBB
            succ.replacePredcessor(callerBlock, backBB);
        }
        // 接手原基本块 CallInst 后面的所有指令
        // 因为后继是通过 Br 指令获取的, 所以不需要特地维护 backBB 的后继列表
        for (final var backInst : splitResult.back) {
            backBB.getIList().add(backInst);
        }

        // 处理 exitBB (内联的函数的 "return" 指令要跳转到的块)
        final var exitBB = new BasicBlock(callerFunc, callerBBSym.newSymbolWithSuffix("_inline_exit"));
        final var returnType = calleeFunc.getType().getReturnType();
        if (!returnType.isVoid()) {
            // 构造用于存返回值的 Phi
            final var returnValueSym = new SourceCodeSymbol(calleeFunc.getFunctionSourceName() + "_inline_return", 0, 0);
            final var returnValuePhi = new PhiInst(exitBB, calleeFunc.getType().getReturnType(), returnValueSym);
            exitBB.getIList().add(returnValuePhi);
            // 将对 CallInst 的引用全部换成对返回值的 Phi 的引用
            callInst.replaceAllUseWith(returnValuePhi);
        }
        // 构造最后的跳转
        exitBB.getIList().add(new BrInst(exitBB, backBB));

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

        // 进行拷贝
        final var inliner = new FunctionInliner(callerFunc, calleeFunc, paramToArg, exitBB);
        final var calleeNewBlocks = inliner.convert();

        // 整理待加入 callerFunc 的基本块列表
        final var newBlocks = new ArrayList<BasicBlock>();
        newBlocks.add(frontBB);
        newBlocks.addAll(calleeNewBlocks);
        newBlocks.add(exitBB);
        newBlocks.add(backBB);

        // 移除旧的块, 加入新的块
        final var oldBlockIndex = callerFunc.getIList().indexOf(callerBlock);
        callerFunc.getIList().remove(oldBlockIndex);
        callerFunc.getIList().addAll(oldBlockIndex, newBlocks);
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
    static class FunctionInliner implements ValueVisitor<Value> {
        public FunctionInliner(Function caller, Function callee, Map<Parameter, Value> paramToArgs, BasicBlock exitBB) {
            this.oldToNew = new HashMap<>(paramToArgs);
            this.caller = caller;
            this.callee = callee;
            this.exitBB = exitBB;
        }

        public List<BasicBlock> convert() {
            for (final var block : callee.getBasicBlocks()) {
                for (final var inst : block.allInst()) {
                    getOrCreate(inst);
                }

                getOrCreate(block);
            }

            if (!callee.getType().getReturnType().isVoid()) {
                final var first = exitBB.getIList().get(0);
                Log.ensure(first instanceof PhiInst);
                ((PhiInst) first).setIncomingCO(returnValues);
            }

            return callee.getBasicBlocks().stream().map(this::getOrCreate).collect(Collectors.toList());
        }

        public <T extends Value> T getOrCreate(T old) {
            return (T) visit(old);
        }

        @Override
        public BasicBlock visitBasicBlock(final BasicBlock value) {
            if (oldToNew.containsKey(value)) {
                return (BasicBlock) oldToNew.get(value);
            }

            final var newBB = new BasicBlock(caller, value.getSymbol().newSymbolWithSuffix("_inline"));
            oldToNew.put(value, newBB);
            return newBB;
        }

        @Override
        public Instruction visitInstruction(final Instruction value) {
            if (oldToNew.containsKey(value)) {
                return (Instruction) oldToNew.get(value);
            }

            final var newInst = instructionCloner.visit(value);
            oldToNew.put(value, newInst);
            return newInst;
        }

        @Override
        public Value visitParameter(final Parameter value) {
            Log.ensure(oldToNew.containsKey(value), "FunctionInliner 必须包含完整的形参映射");
            return oldToNew.get(value);
        }

        // 全局变量, 函数与常量不需要被复制
        @Override public GlobalVar visitGlobalVar(final GlobalVar value) { return value; }
        @Override public Function visitFunction(final Function value) { return value; }
        @Override public Constant visitConstant(final Constant value) { return value; }

        private final Map<Value, Value> oldToNew;
        private final Function caller;
        private final Function callee;
        private final BasicBlock exitBB;
        private final List<Value> returnValues = new ArrayList<>();
        private final InstructionCloner instructionCloner = new InstructionCloner();

        /** <h3>完成函数内联所需要的指令的复制</h3>
         * <p>基本上就是对各条指令, 获取其参数, 随后尝试获取旧参数所对应的新的值, 没有就创建.</p>
         * <p>但是对 Return 指令做了特殊处理, 将其变为跳转到 exitBB 的语句 (有返回值的话还记录了返回值是什么)</p>
         */
        class InstructionCloner implements InstructionVisitor<Instruction> {
            @Override
            public Instruction visitBinaryOpInst(final BinaryOpInst inst) {
                final var lhs = getOrCreate(inst.getLHS());
                final var rhs = getOrCreate(inst.getRHS());
                return new BinaryOpInst(inst.getKind(), lhs, rhs);
            }

            @Override
            public Instruction visitBoolToIntInst(final BoolToIntInst inst) {
                final var from = getOrCreate(inst.getFrom());
                return new BoolToIntInst(from);
            }

            @Override
            public Instruction visitBrCondInst(final BrCondInst inst) {
                final var currBB = getOrCreate(inst.getParent());
                final var cond = getOrCreate(inst.getCond());
                final var trueBB = getOrCreate(inst.getTrueBB());
                final var falseBB = getOrCreate(inst.getFalseBB());
                return new BrCondInst(currBB, cond, trueBB, falseBB);
            }

            @Override
            public Instruction visitBrInst(final BrInst inst) {
                final var currBB = getOrCreate(inst.getParent());
                final var nextBB = getOrCreate(inst.getNextBB());
                return new BrInst(currBB, nextBB);
            }

            @Override
            public Instruction visitCallInst(final CallInst inst) {
                final var callee = getOrCreate(inst.getCallee());
                final var args = inst.getArgList().stream()
                    .map(FunctionInliner.this::getOrCreate).collect(Collectors.toList());
                return new CallInst(callee, args);
            }

            @Override
            public Instruction visitCAllocInst(final CAllocInst inst) {
                return new CAllocInst(inst.getAllocType());
            }

            @Override
            public Instruction visitCmpInst(final CmpInst inst) {
                final var lhs = getOrCreate(inst.getLHS());
                final var rhs = getOrCreate(inst.getRHS());
                return new CmpInst(inst.getKind(), lhs, rhs);
            }

            @Override
            public Instruction visitFloatToIntInst(final FloatToIntInst inst) {
                final var from = getOrCreate(inst.getFrom());
                return new FloatToIntInst(from);
            }

            @Override
            public Instruction visitGEPInst(final GEPInst inst) {
                final var ptr = getOrCreate(inst.getPtr());
                final var indices = inst.getIndices().stream()
                    .map(FunctionInliner.this::getOrCreate).collect(Collectors.toList());
                return new GEPInst(ptr, indices);
            }

            @Override
            public Instruction visitIntToFloatInst(final IntToFloatInst inst) {
                final var from = getOrCreate(inst.getFrom());
                return new IntToFloatInst(from);
            }

            @Override
            public Instruction visitLoadInst(final LoadInst inst) {
                final var ptr = getOrCreate(inst.getPtr());
                return new LoadInst(ptr);
            }

            @Override
            public Instruction visitMemInitInst(final MemInitInst inst) {
                final var ptr = getOrCreate(inst.getArrayPtr());
                final var init = getOrCreate(inst.getInit());
                return new MemInitInst(ptr, init);
            }

            @Override
            public Instruction visitPhiInst(final PhiInst inst) {
                final var currBB = getOrCreate(inst.getParent());
                final var incomingValues = inst.getIncomingValues().stream()
                    .map(FunctionInliner.this::getOrCreate).collect(Collectors.toList());

                final var phi = new PhiInst(currBB, inst.getType(), inst.getSymbol());
                phi.setIncomingCO(incomingValues);
                return phi;
            }

            @Override
            public Instruction visitReturnInst(final ReturnInst inst) {
                // return 换成 br
                final var currBB = getOrCreate(inst.getParent());
                inst.getReturnValue().map(FunctionInliner.this::getOrCreate).ifPresent(returnValues::add);
                return new BrInst(currBB, exitBB);
            }

            @Override
            public Instruction visitStoreInst(final StoreInst inst) {
                final var ptr = getOrCreate(inst.getPtr());
                final var val = getOrCreate(inst.getVal());
                return new StoreInst(ptr, val);
            }

            @Override
            public Instruction visitUnaryOpInst(final UnaryOpInst inst) {
                final var arg = getOrCreate(inst.getArg());
                return new UnaryOpInst(inst.getKind(), arg);
            }
        }
    }
}
