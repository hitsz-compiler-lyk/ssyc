package top.origami404.ssyc.frontend;

import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

import top.origami404.ssyc.frontend.folder.CondFolder;
import top.origami404.ssyc.frontend.folder.FloatConstantFolder;
import top.origami404.ssyc.frontend.folder.IntConstantFolder;
import top.origami404.ssyc.frontend.info.InstCache;
import top.origami404.ssyc.frontend.info.CurrDefInfo;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.BoolConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.INode;

public class IRBuilder {
    public IRBuilder() {
        // TODO: 再次认真考虑要不要为了支持全局常量表达式求值而引入这个 null
        this.currBB = null;
        this.currFunc = null;
    }

    public IRBuilder(BasicBlock currentBasicBlock) {
        this.currBB = currentBasicBlock;
        this.currFunc = currentBasicBlock.getParentOpt()
            .orElseThrow(() -> new RuntimeException("Cannot use free blocks as builder's argument"));
        addInfos(currBB);
    }

    public Value insertINeg(Value arg) { return foldInt(new UnaryOpInst(InstKind.INeg, arg)); }
    public Value insertIAdd(Value lhs, Value rhs) { return foldInt(new BinaryOpInst(InstKind.IAdd, lhs, rhs)); }
    public Value insertISub(Value lhs, Value rhs) { return foldInt(new BinaryOpInst(InstKind.ISub, lhs, rhs)); }
    public Value insertIMul(Value lhs, Value rhs) { return foldInt(new BinaryOpInst(InstKind.IMul, lhs, rhs)); }
    public Value insertIDiv(Value lhs, Value rhs) { return foldInt(new BinaryOpInst(InstKind.IDiv, lhs, rhs)); }
    public Value insertIMod(Value lhs, Value rhs) { return foldInt(new BinaryOpInst(InstKind.IMod, lhs, rhs)); }

    public Value insertFNeg(Value arg) { return foldFloat(new UnaryOpInst(InstKind.FNeg, arg)); }
    public Value insertFAdd(Value lhs, Value rhs) { return foldFloat(new BinaryOpInst(InstKind.FAdd, lhs, rhs)); }
    public Value insertFSub(Value lhs, Value rhs) { return foldFloat(new BinaryOpInst(InstKind.FSub, lhs, rhs)); }
    public Value insertFMul(Value lhs, Value rhs) { return foldFloat(new BinaryOpInst(InstKind.FMul, lhs, rhs)); }
    public Value insertFDiv(Value lhs, Value rhs) { return foldFloat(new BinaryOpInst(InstKind.FDiv, lhs, rhs)); }

    public Value insertICmpEq(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.ICmpEq, lhs, rhs)); }
    public Value insertICmpNe(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.ICmpNe, lhs, rhs)); }
    public Value insertICmpLt(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.ICmpLt, lhs, rhs)); }
    public Value insertICmpLe(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.ICmpLe, lhs, rhs)); }
    public Value insertICmpGt(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.ICmpGt, lhs, rhs)); }
    public Value insertICmpGe(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.ICmpGe, lhs, rhs)); }
    public Value insertFCmpEq(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.FCmpEq, lhs, rhs)); }
    public Value insertFCmpNe(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.FCmpNe, lhs, rhs)); }
    public Value insertFCmpLt(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.FCmpLt, lhs, rhs)); }
    public Value insertFCmpLe(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.FCmpLe, lhs, rhs)); }
    public Value insertFCmpGt(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.FCmpGt, lhs, rhs)); }
    public Value insertFCmpGe(Value lhs, Value rhs) { return foldCmp(new CmpInst(InstKind.FCmpGe, lhs, rhs)); }

    public void insertBrCond(Value cond, BasicBlock trueBB, BasicBlock falseBB) { foldBr(cond, trueBB, falseBB); }

    public void insertBranch(BasicBlock nextBB) { direct(new BrInst(currBB, nextBB)); }

    public CallInst insertCall(Function func, List<Value> args) { return direct(new CallInst(func, args)); }
    public void insertReturn() { direct(new ReturnInst()); }
    public void insertReturn(Value returnVal) { direct(new ReturnInst(returnVal)); }

    public CAllocInst insertCAlloc(ArrayIRTy allocBaseType) { return direct(new CAllocInst(allocBaseType)); }

    public LoadInst insertLoad(Value ptr) { return direct(new LoadInst(ptr)); }
    public void insertStore(Value ptr, Value val) { direct(new StoreInst(ptr, val)); }

    // GEP 指令是需要被加进 Cache 里的, 因为底层的指针偏移运算肯定是可复用并且越少越好的
    public GEPInst insertGEP(Value ptr, List<? extends Value> indices) { return direct(new GEPInst(ptr, indices)); }

    public GEPInst insertGEPByInts(Value ptr, List<Integer> indices) {
        final var valueIndices = indices.stream().map(Constant::createIntConstant).collect(Collectors.toList());
        return insertGEP(ptr, valueIndices);
    }

    public Value insertI2F(Value from) { return foldFloat(new IntToFloatInst(from));    }
    public Value insertF2I(Value from) { return foldInt(new FloatToIntInst(from));      }
    public Value insertB2I(Value from) { return foldInt(new BoolToIntInst(from));       }

    public MemInitInst insertMemInit(Value arrPtr) { return direct(new MemInitInst(arrPtr)); }

    public PhiInst insertEmptyPhi(IRType type, SourceCodeSymbol variable) {
        final var phi = new PhiInst(currBB, type, variable);
        currBB.addPhi(phi);
        return phi;
    }

    public Function getFunction() {
        return currFunc;
    }

    public BasicBlock getBasicBlock() {
        return currBB;
    }

    public BasicBlock createFreeBBlock(SourceCodeSymbol symbol) {
        return BasicBlock.createFreeBBlock(currFunc, symbol);
    }

    public void appendBBlock(BasicBlock newBB) {
        if (currBB != null && !currBB.isTerminated()) {
            insertBranch(newBB);
        }
        changeBasicBlock(newBB);
        currFunc.getIList().add(newBB);
    }

    public void createAndAppendBBlock(SourceCodeSymbol symbol) {
        appendBBlock(createFreeBBlock(symbol));
    }

    public void changeBasicBlock(BasicBlock newBB) {
        currBB = newBB;
        newBB.getParentOpt().ifPresentOrElse(func -> currFunc = func, () -> newBB.setParent(currFunc));
        addInfos(newBB);
    }

    public void switchToGlobal() {
        this.currBB = null;
        this.currFunc = null;
    }

    public void switchToFunction(Function function) {
        currFunc = function;
        final var funcSym = function.getSymbol();
        final var symbol = new SourceCodeSymbol(funcSym.getName() + "_entry", funcSym.getLine(), funcSym.getColumn());
        createAndAppendBBlock(symbol);
    }

    private static void addInfos(BasicBlock bb) {
        bb.addIfAbsent(CurrDefInfo.class, CurrDefInfo::new);
    }

    private static ListIterator<INode<Instruction, BasicBlock>> getLastINodeItr(BasicBlock bb) {
        return bb.asINodeView().listIterator(bb.getInstructionCount());
    }

    /**
     * 注意: 不会更新 currDef !!!!!!!!!!!!!!!
     */
    public static void refold(Instruction val) {
        if (val instanceof BrCondInst) {
            final var br = (BrCondInst) val;
            final var cond = (br.getCond());

            if (cond instanceof BoolConst) {
                final var value = ((BoolConst) cond).getValue();
                final var currBB = br.getParentOpt().orElseThrow();
                final var nextBB = value ? br.getTrueBB() : br.getFalseBB();
                final var fakeBB = value ? br.getFalseBB() : br.getTrueBB();

                fakeBB.removePredecessorWithPhiUpdated(currBB);

                final var directBr = new BrInst(currBB, nextBB);
                val.replaceAllUseWith(directBr);
            }

        } else {
            final var exp = foldExp(val);
            if (exp != val) {
                val.replaceAllUseWith(exp);
            }
        }
    }

    private static Value foldExp(Instruction val) {
        final var type = val.getType();
        if (type.isInt() && IntConstantFolder.canFold(val)) {
            return IntConstantFolder.foldConst(val);
        } else if (type.isFloat() && FloatConstantFolder.canFold(val)) {
            return FloatConstantFolder.foldConst(val);
        } else if (type.isBool()) {
            return foldCond(val);
        }

        return val;
    }

    private static Value foldCond(Value cond) {
        if (cond instanceof CmpInst) {
            final var cmp = (CmpInst) cond;
            if (CondFolder.canFold(cmp)) {
                return CondFolder.foldConst(cmp);
            }
        }

        return cond;
    }

    private Value foldInt(Instruction val) {
        if (IntConstantFolder.canFold(val)) {
            return IntConstantFolder.foldConst(val);
        } else {
            return direct(val);
        }
    }

    private Value foldFloat(Instruction val) {
        if (FloatConstantFolder.canFold(val)) {
            return FloatConstantFolder.foldConst(val);
        } else {
            return direct(val);
        }
    }

    private Value foldCmp(CmpInst cond) {
        if (CondFolder.canFold(cond)) {
            return CondFolder.foldConst(cond);
        } else {
            return direct(cond);
        }
    }

    private void foldBr(Value cond, BasicBlock trueBB, BasicBlock falseBB) {
        // 不可以先生成一条 BrCondInst 进来再折叠
        // 因为 BrCondInst 的构造函数必须是一致的, 它会往 tureBB/falseBB 里加入当前块作为前继
        // 而如果构造 BrCondInst 之后再折叠的话, 还要去另一个块里把前继删掉, 太麻烦了
        // 所以干脆直接三个参数传进来, 等到不能折叠的时候再构造
        if (cond instanceof BoolConst) {
            final var targetBB = ((BoolConst) cond).getValue() ? trueBB : falseBB;
            direct(new BrInst(currBB, targetBB));
        } else if (trueBB == falseBB) {
            direct(new BrInst(currBB, trueBB));
        } else {
            direct(new BrCondInst(currBB, cond, trueBB, falseBB));
        }
    }

    private<T extends Instruction> T direct(T inst) {
        insert(inst);
        return inst;
    }

    private<T extends Instruction> void insert(T inst) {
        currBB.getIList().add(inst);
    }

    private Function currFunc;
    private BasicBlock currBB;
}
