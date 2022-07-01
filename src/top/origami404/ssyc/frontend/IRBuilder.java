package top.origami404.ssyc.frontend;

import java.util.List;
import java.util.ListIterator;

import top.origami404.ssyc.frontend.folder.CondFolder;
import top.origami404.ssyc.frontend.folder.FloatConstantFolder;
import top.origami404.ssyc.frontend.folder.IntConstantFolder;
import top.origami404.ssyc.frontend.info.InstCache;
import top.origami404.ssyc.frontend.info.VersionInfo;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.BoolConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.AllocInst;
import top.origami404.ssyc.ir.inst.BinaryOpInst;
import top.origami404.ssyc.ir.inst.BrCondInst;
import top.origami404.ssyc.ir.inst.BrInst;
import top.origami404.ssyc.ir.inst.CallInst;
import top.origami404.ssyc.ir.inst.CmpInst;
import top.origami404.ssyc.ir.inst.FloatToIntInst;
import top.origami404.ssyc.ir.inst.GEPInst;
import top.origami404.ssyc.ir.inst.InstKind;
import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.inst.IntToFloatInst;
import top.origami404.ssyc.ir.inst.LoadInst;
import top.origami404.ssyc.ir.inst.MemInitInst;
import top.origami404.ssyc.ir.inst.ReturnInst;
import top.origami404.ssyc.ir.inst.StoreInst;
import top.origami404.ssyc.ir.inst.UnaryOpInst;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.INode;

public class IRBuilder {
    public IRBuilder(BasicBlock currentBasicBlock) {
        this(currentBasicBlock, getLastINodeItr(currentBasicBlock));
    }

    public IRBuilder(BasicBlock currentBasicBlock, ListIterator<INode<Instruction, BasicBlock>> position) {
        this.currBB = currentBasicBlock;
        this.pos = position;
        this.currFunc = currentBasicBlock.getParent().get();
        addInfosToBasicBlock(currBB);
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

    public Instruction insertBrCond(Value cond, BasicBlock trueBB, BasicBlock falseBB) { return foldBr(new BrCondInst(cond, trueBB, falseBB)); }

    public Instruction insertBrICmpEq(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertICmpEq(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrICmpNe(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertICmpNe(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrICmpLt(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertICmpLt(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrICmpLe(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertICmpLe(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrICmpGt(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertICmpGt(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrICmpGe(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertICmpGe(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrFCmpEq(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertFCmpEq(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrFCmpNe(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertFCmpNe(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrFCmpLt(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertFCmpLt(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrFCmpLe(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertFCmpLe(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrFCmpGt(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertFCmpGt(lhs, rhs), trueBB, falseBB); }
    public Instruction insertBrFCmpGe(Value lhs, Value rhs, BasicBlock trueBB, BasicBlock falseBB) { return insertBrCond(insertFCmpGe(lhs, rhs), trueBB, falseBB); }

    public BrInst insertBranch(BasicBlock nextBB) { return direct(new BrInst(nextBB)); }

    public CallInst insertCall(Function func, List<Value> args) { return cache(new CallInst(func, args)); }
    public ReturnInst insertReturn() { return direct(new ReturnInst()); }
    public ReturnInst insertReturn(Value returnVal) { return direct(new ReturnInst(returnVal)); }

    public AllocInst insertAllocVariable(IRType varType) { return direct(new AllocInst(varType)); }
    public AllocInst insertAllocArray(int elementNum, IRType baseType) {
        final var arrayTy = IRType.createArrayTy(elementNum, baseType);
        return direct(new AllocInst(arrayTy));
    }

    public LoadInst insertLoad(Value ptr) { return direct(new LoadInst(ptr)); }
    public StoreInst insertStore(Value ptr, Value val) { return direct(new StoreInst(ptr, val)); }

    // GEP 指令是需要被加进 Cache 里的, 因为底层的指针偏移运算肯定是可复用并且越少越好的
    public GEPInst insertGEP(Value ptr, List<? extends Value> indices) { return cache(new GEPInst(ptr, indices)); }

    public GEPInst insertGEPByInts(Value ptr, List<Integer> indices) {
        final var valueIndices = indices.stream().map(Constant::createIntConstant).toList();
        return insertGEP(ptr, valueIndices);
    }

    public Value insertI2F(Value from) { return foldFloat(new IntToFloatInst(from));    }
    public Value insertF2I(Value from) { return foldInt(new FloatToIntInst(from));      }

    public MemInitInst insertMemInit(Value arrPtr) { return direct(new MemInitInst(arrPtr)); }

    public Function getFunction() {
        return currFunc;
    }

    public BasicBlock getBasicBlock() {
        return currBB;
    }

    public BasicBlock createBasicBlock() {
        return new BasicBlock(currFunc);
    }

    public void changeBasicBlock(BasicBlock newBB) {
        currBB = newBB;
        pos = getLastINodeItr(newBB);
        addInfosToBasicBlock(newBB);
    }

    private static void addInfosToBasicBlock(BasicBlock bb) {
        bb.addIfAbsent(InstCache.class, () -> new InstCache());
        bb.addIfAbsent(VersionInfo.class, () -> new VersionInfo());
    }

    private static ListIterator<INode<Instruction, BasicBlock>> getLastINodeItr(BasicBlock bb) {
        return bb.asINodeView().listIterator(bb.getInstructionCount());
    }

    private Value foldInt(Instruction val) {
        if (IntConstantFolder.canFold(val)) {
            return IntConstantFolder.foldConst(val);
        } else {
            return cache(val);
        }
    }

    private Value foldFloat(Instruction val) {
        if (FloatConstantFolder.canFold(val)) {
            return FloatConstantFolder.foldConst(val);
        } else {
            return cache(val);
        }
    }

    private Value foldCmp(CmpInst cond) {
        if (CondFolder.canFold(cond)) {
            return CondFolder.foldConst(cond);
        } else {
            return cache(cond);
        }
    }

    private Instruction foldBr(BrCondInst inst) {
        if (inst.getCond() instanceof BoolConst cond) {
            final var targetBB = cond.getValue() ? inst.getTrueBB() : inst.getFalseBB();
            return direct(new BrInst(targetBB));
        } else {
            return direct(inst);
        }
    }

    private<T extends Instruction> T cache(T inst) {

        final var cachedInst = createWithCache(inst);
        pos.add(cachedInst.getINode());
        return cachedInst;
    }

    private<T extends Instruction> T direct(T inst) {
        pos.add(inst.getINode());
        return inst;
    }

    @SuppressWarnings("unchecked")
    private<T extends Instruction> T createWithCache(T inst) {
        final var cache = currFunc.getAnalysisInfo(InstCache.class);
        return (T) cache.getOrElse(inst);
    }

    private Function currFunc;
    private BasicBlock currBB;
    private ListIterator<INode<Instruction, BasicBlock>> pos;
}
