package top.origami404.ssyc.backend.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.origami404.ssyc.backend.arm.ArmBlock;
import top.origami404.ssyc.backend.arm.ArmFunction;
import top.origami404.ssyc.backend.arm.ArmInstBinary;
import top.origami404.ssyc.backend.arm.ArmInstBranch;
import top.origami404.ssyc.backend.arm.ArmInstCall;
import top.origami404.ssyc.backend.arm.ArmInstCmp;
import top.origami404.ssyc.backend.arm.ArmInstFloatToInt;
import top.origami404.ssyc.backend.arm.ArmInstIntToFloat;
import top.origami404.ssyc.backend.arm.ArmInstLoad;
import top.origami404.ssyc.backend.arm.ArmFunction.FunctionInfo;
import top.origami404.ssyc.backend.arm.ArmInst.ArmCondType;
import top.origami404.ssyc.backend.arm.ArmInst.ArmInstKind;
import top.origami404.ssyc.backend.arm.ArmInstMove;
import top.origami404.ssyc.backend.arm.ArmInstReturn;
import top.origami404.ssyc.backend.arm.ArmInstStroe;
import top.origami404.ssyc.backend.arm.ArmInstTernay;
import top.origami404.ssyc.backend.arm.ArmInstUnary;
import top.origami404.ssyc.backend.operand.FVirtualReg;
import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.IPhyReg;
import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.backend.operand.addr;
import top.origami404.ssyc.backend.operand.IVirtualReg;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.GlobalVar;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.Parameter;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.BoolConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.FloatConst;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.BinaryOpInst;
import top.origami404.ssyc.ir.inst.BoolToIntInst;
import top.origami404.ssyc.ir.inst.BrCondInst;
import top.origami404.ssyc.ir.inst.BrInst;
import top.origami404.ssyc.ir.inst.CAllocInst;
import top.origami404.ssyc.ir.inst.CallInst;
import top.origami404.ssyc.ir.inst.CmpInst;
import top.origami404.ssyc.ir.inst.FloatToIntInst;
import top.origami404.ssyc.ir.inst.GEPInst;
import top.origami404.ssyc.ir.inst.InstKind;
import top.origami404.ssyc.ir.inst.IntToFloatInst;
import top.origami404.ssyc.ir.inst.LoadInst;
import top.origami404.ssyc.ir.inst.PhiInst;
import top.origami404.ssyc.ir.inst.ReturnInst;
import top.origami404.ssyc.ir.inst.StoreInst;
import top.origami404.ssyc.ir.inst.UnaryOpInst;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.utils.Log;

public class CodeGenManager {
    private List<ArmFunction> functions;
    private Map<Value, Operand> valMap;
    private Map<Function, ArmFunction> funcMap;
    private Map<BasicBlock, ArmBlock> blockMap;
    private Map<String, GlobalVar> globalvars;
    private static final Map<InstKind, ArmCondType> condMap = new HashMap<InstKind, ArmCondType>() {
        {
            put(InstKind.ICmpEq, ArmCondType.Eq);
            put(InstKind.ICmpNe, ArmCondType.Ne);
            put(InstKind.ICmpGt, ArmCondType.Gt);
            put(InstKind.ICmpGe, ArmCondType.Ge);
            put(InstKind.ICmpLt, ArmCondType.Lt);
            put(InstKind.ICmpLe, ArmCondType.Le);
            put(InstKind.FCmpEq, ArmCondType.Eq);
            put(InstKind.FCmpNe, ArmCondType.Ne);
            put(InstKind.FCmpGt, ArmCondType.Gt);
            put(InstKind.FCmpGe, ArmCondType.Ge);
            put(InstKind.FCmpLt, ArmCondType.Lt);
            put(InstKind.FCmpLe, ArmCondType.Le);
        }
    };

    public CodeGenManager() {
        functions = new ArrayList<ArmFunction>();
        valMap = new HashMap<>();
        funcMap = new HashMap<Function, ArmFunction>();
        blockMap = new HashMap<BasicBlock, ArmBlock>();
    }

    public List<ArmFunction> getFunctions() {
        return functions;
    }

    public void addFunction(ArmFunction func) {
        functions.add(func);
    }

    public void GenArm(Module irModule) {
        globalvars = irModule.getVariables();
        for (var val : globalvars.values()) {
            valMap.put(val, new addr(val.getName(), true));
        }

        for (var func : irModule.getFunctions().values()) {
            var armFunc = new ArmFunction(func.getName());
            armFunc.getFuncInfo().setParameter(func.getParameters());
            functions.add(armFunc);
            funcMap.put(func, armFunc);

            for (var block : func.asElementView()) {
                var armblock = new ArmBlock(armFunc, block.getName());
                blockMap.put(block, armblock);
            }

            if (func.asElementView().size() > 0) {
                var armblock = blockMap.get(func.asElementView().get(0));
                armFunc.getFuncInfo().getStartBlock().setTrueSuccBlock(armblock);
                armblock.addPred(armFunc.getFuncInfo().getStartBlock());
            }

            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                for (var pred : block.getPredecessors()) {
                    armBlock.addPred(blockMap.get(pred));
                }

                var succ = block.getSuccessors();
                for (int i = 0; i < succ.size(); i++) {
                    if (i == 0) {
                        armBlock.setTrueSuccBlock(blockMap.get(succ.get(i)));
                    }
                    if (i == 1) {
                        armBlock.setFalseSuccBlock(blockMap.get(succ.get(i)));
                    }
                }
            }

            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);

                for (var inst : block.asElementView()) {
                    if (inst instanceof BinaryOpInst) {
                        ResolveBinaryInst((BinaryOpInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof UnaryOpInst) {
                        ResolveUnaryInst((UnaryOpInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof LoadInst) {
                        ResolveLoadInst((LoadInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof StoreInst) {
                        ResolveStoreInst((StoreInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof CAllocInst) {
                        ResolveCAllocInst((CAllocInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof GEPInst) {
                        ResolveGEPInst((GEPInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof CallInst) {
                        ResolveCallInst((CallInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof ReturnInst) {
                        ResolveReturnInst((ReturnInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof IntToFloatInst) {
                        ResolveIntToFloatInst((IntToFloatInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof FloatToIntInst) {
                        ResolveFloatToIntInst((FloatToIntInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof BrInst) {
                        ResolveBrInst((BrInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof BrCondInst) {
                        ResolveBrCondInst((BrCondInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof CmpInst) {
                        continue;
                    } else if (inst instanceof BoolToIntInst) {
                        continue;
                    } else if (inst instanceof PhiInst) {
                        continue;
                    } else {

                    }
                }
            }

            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                var phiIt = block.iterPhis();
                while (phiIt.hasNext()) {
                    var phi = phiIt.next();
                    var incomingInfoIt = phi.getIncomingInfos().iterator();
                    var phiReg = ResolveOperand(phi, armBlock, armFunc.getFuncInfo());
                    while (incomingInfoIt.hasNext()) {
                        var incomingInfo = incomingInfoIt.next();
                        var src = incomingInfo.getValue();
                        var incomingBlock = blockMap.get(incomingInfo.getBlock());
                        var srcReg = ResolveOperand(src, incomingBlock, armFunc.getFuncInfo());
                        new ArmInstMove(incomingBlock, phiReg, srcReg);
                    }
                }
            }

        }
    }

    public static boolean checkEncodeIImm(int imm) {
        int n = imm;
        for (int i = 0; i < 32; i++) {
            if ((n & ~0xFF) == 0) {
                return true;
            }
            n = (n << 2) | (n >>> 30);
        }
        return false;
    }

    private Operand ResolveIImmOperand(IntConst val, ArmBlock block, FunctionInfo funcinfo) {
        if (checkEncodeIImm(val.getValue())) {
            return new IImm(val.getValue());
        } else {
            var vr = new IVirtualReg();
            var addr = new addr(val.getValue());
            funcinfo.addAddr(addr);
            new ArmInstLoad(block, vr, addr);
            return vr;
        }
    }

    private Operand ResolveIImmOperand(int val, ArmBlock block, FunctionInfo funcinfo) {
        if (checkEncodeIImm(val)) {
            return new IImm(val);
        } else {
            var vr = new IVirtualReg();
            var addr = new addr(val);
            funcinfo.addAddr(addr);
            new ArmInstLoad(block, vr, addr);
            return vr;
        }
    }

    private Operand ResolveFImmOperand(FloatConst val, ArmBlock block, FunctionInfo funcinfo) {
        var vr = new IVirtualReg();
        var addr = new addr(val.getValue());
        funcinfo.addAddr(addr);
        new ArmInstLoad(block, vr, addr);
        return vr;
    }

    private Operand ResolveImmOperand(Constant val, ArmBlock block, FunctionInfo funcinfo) {
        if (val instanceof IntConst) {
            return ResolveIImmOperand((IntConst) val, block, funcinfo);
        } else if (val instanceof FloatConst) {
            return ResolveFImmOperand((FloatConst) val, block, funcinfo);
        }
        return null;
    }

    private Operand ResolveParameter(Parameter val, ArmBlock block, FunctionInfo funcinfo) {
        if (!valMap.containsKey(val)) {
            Operand vr;
            if (val.getParamType().isFloat()) {
                vr = new FVirtualReg();
            } else {
                vr = new IVirtualReg();
            }
            valMap.put(val, vr);
            var params = funcinfo.getParameter();
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i) == val) {
                    if (i < 4) {
                        new ArmInstMove(funcinfo.getStartBlock(), vr, new IPhyReg(i));
                    } else {
                        var offset = ResolveIImmOperand((i - 4) * 2, block, funcinfo);
                        new ArmInstLoad(funcinfo.getStartBlock(), vr, new IPhyReg("sp"), offset);
                    }
                    break;
                }
            }
            return vr;
        } else {
            return valMap.get(val);
        }
    }

    private Operand ResolveLhsOperand(Value val, ArmBlock block, FunctionInfo funcinfo) {
        if (val instanceof IntConst) {
            var vr = new IVirtualReg();
            var addr = new addr(((IntConst) val).getValue());
            funcinfo.addAddr(addr);
            new ArmInstLoad(block, vr, addr);
            return vr;
        } else if (val instanceof FloatConst) {
            return ResolveFImmOperand((FloatConst) val, block, funcinfo);
        } else {
            return ResolveOperand(val, block, funcinfo);
        }
    }

    private Operand ResolveGlobalVar(GlobalVar val, ArmBlock block, FunctionInfo funcinfo) {
        Log.ensure(valMap.containsKey(val));
        return valMap.get(val);
    }

    private Operand ResolveOperand(Value val, ArmBlock block, FunctionInfo funcinfo) {
        if (valMap.containsKey(val)) {
            return valMap.get(val);
        } else if (val instanceof Constant) {
            return ResolveImmOperand((Constant) val, block, funcinfo);
        } else if (val instanceof Parameter && funcinfo.getParameter().contains(val)) {
            return ResolveParameter((Parameter) val, block, funcinfo);
        } else if (val instanceof GlobalVar) {
            return ResolveGlobalVar((GlobalVar) val, block, funcinfo);
        } else {
            Operand vr;
            if (val.getType().isFloat()) {
                vr = new FVirtualReg();
            } else {
                vr = new IVirtualReg();
            }
            valMap.put(val, vr);
            return vr;
        }
    }

    private void ResolveBinaryInst(BinaryOpInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var lhs = inst.getLHS();
        var rhs = inst.getRHS();
        var dst = inst;
        Operand lhsReg, rhsReg, dstReg;

        switch (inst.getKind()) {
            case IAdd: {
                if (lhs instanceof Constant) {
                    lhsReg = ResolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = ResolveOperand(lhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = ResolveOperand(rhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                }
                new ArmInstBinary(block, ArmInstKind.IAdd, dstReg, lhsReg, rhsReg);
                break;
            }
            case ISub: {
                if (lhs instanceof Constant) {
                    lhsReg = ResolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = ResolveOperand(lhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                    new ArmInstBinary(block, ArmInstKind.IRsb, dstReg, lhsReg, rhsReg);
                } else {
                    lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = ResolveOperand(rhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                    new ArmInstBinary(block, ArmInstKind.ISub, dstReg, lhsReg, rhsReg);
                }
                break;
            }
            case IMul: {
                if (lhs instanceof Constant) {
                    lhsReg = ResolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = ResolveOperand(lhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = ResolveOperand(rhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                }
                new ArmInstBinary(block, ArmInstKind.IMul, dstReg, lhsReg, rhsReg);
                break;
            }
            case IDiv: {
                lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                rhsReg = ResolveOperand(rhs, block, funcinfo);
                dstReg = ResolveOperand(dst, block, funcinfo);
                new ArmInstBinary(block, ArmInstKind.IDiv, dstReg, lhsReg, rhsReg);
                break;
            }
            case IMod: {
                // x % y == x - (x / y) *y
                lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                rhsReg = ResolveOperand(rhs, block, funcinfo);
                dstReg = ResolveOperand(dst, block, funcinfo);
                var vr = new IVirtualReg();
                new ArmInstBinary(block, ArmInstKind.IDiv, vr, lhsReg, rhsReg);
                new ArmInstTernay(block, ArmInstKind.IMulSub, dstReg, vr, rhsReg, lhsReg);
            }
            case FAdd: {
                if (lhs instanceof Constant) {
                    lhsReg = ResolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = ResolveOperand(lhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = ResolveOperand(rhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                }
                new ArmInstBinary(block, ArmInstKind.FAdd, dstReg, lhsReg, rhsReg);
            }
            case FSub: {
                lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                rhsReg = ResolveOperand(rhs, block, funcinfo);
                dstReg = ResolveOperand(dst, block, funcinfo);
                new ArmInstBinary(block, ArmInstKind.FSub, dstReg, lhsReg, rhsReg);
            }
            case FMul: {
                if (lhs instanceof Constant) {
                    lhsReg = ResolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = ResolveOperand(lhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = ResolveOperand(rhs, block, funcinfo);
                    dstReg = ResolveOperand(dst, block, funcinfo);
                }
                new ArmInstBinary(block, ArmInstKind.FMul, dstReg, lhsReg, rhsReg);
            }
            case FDiv: {
                lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
                rhsReg = ResolveOperand(rhs, block, funcinfo);
                dstReg = ResolveOperand(dst, block, funcinfo);
                new ArmInstBinary(block, ArmInstKind.FDiv, dstReg, lhsReg, rhsReg);
            }
            default: {
                Log.ensure(false, "binary not implement");
            }
        }
    }

    private void ResolveUnaryInst(UnaryOpInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var src = inst.getArg();
        var dst = inst;
        var srcReg = ResolveOperand(src, block, funcinfo);
        var dstReg = ResolveOperand(dst, block, funcinfo);

        if (inst.getKind() == InstKind.INeg) {
            // new ArmInstBinary(block, ArmInstKind.IRsb, dstReg, srcReg, new IImm(0));
            new ArmInstUnary(block, ArmInstKind.INeg, dstReg, srcReg);
        } else if (inst.getKind() == InstKind.FNeg) {
            new ArmInstUnary(block, ArmInstKind.FNeg, dstReg, srcReg);
        }
    }

    private void ResolveLoadInst(LoadInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var addr = inst.getPtr();
        var dst = inst;

        var addrReg = ResolveOperand(addr, block, funcinfo);
        var dstReg = ResolveOperand(dst, block, funcinfo);
        new ArmInstLoad(block, dstReg, addrReg);
    }

    private void ResolveStoreInst(StoreInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var addr = inst.getPtr();
        var var = inst.getVal();

        var addrReg = ResolveOperand(addr, block, funcinfo);
        var varReg = ResolveOperand(var, block, funcinfo);
        new ArmInstLoad(block, varReg, addrReg);
    }

    private void ResolveGEPInst(GEPInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var p = inst.getType().getBaseType();
        var indices = inst.getIndices();
        ArrayList<Integer> dim = new ArrayList<>();

        while (p.isArray()) {
            dim.add(p.getSize());
            p = ((ArrayIRTy) p).getElementType();
        }

        var arr = ResolveOperand(inst.getPtr(), block, funcinfo);
        var ret = ResolveOperand(inst, block, funcinfo);
        var tot = 0;
        for (int i = 0; i < indices.size(); i++) {
            var offset = ResolveOperand(indices.get(i), block, funcinfo);
            var length = dim.get(i);

            if (offset.IsIImm()) {
                tot += ((IImm) offset).getImm() * length;
                if (i == indices.size() - 1) {
                    if (tot == 0) {
                        new ArmInstMove(block, ret, arr);
                    } else {
                        var imm = ResolveIImmOperand(tot, block, funcinfo);
                        new ArmInstBinary(block, ArmInstKind.IAdd, ret, arr, imm);
                    }
                }
            } else {
                if (tot != 0) {
                    var imm = ResolveIImmOperand(tot, block, funcinfo);
                    var vr = new IVirtualReg();
                    new ArmInstBinary(block, ArmInstKind.IAdd, vr, arr, imm);
                    tot = 0;
                    arr = vr;
                }
                var imm = ResolveIImmOperand(length, block, funcinfo);
                var vr = new IVirtualReg();
                new ArmInstTernay(block, ArmInstKind.IMulAdd, vr, offset, imm, arr);
                arr = vr;
            }
        }
    }

    private void ResolveCallInst(CallInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var args = inst.getArgList();
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (i < 4) {
                var src = ResolveOperand(arg, block, funcinfo);
                new ArmInstMove(block, new IPhyReg(i), src);
            } else {
                var src = ResolveLhsOperand(arg, block, funcinfo);
                var offset = ResolveIImmOperand(-(args.size() - i) * 4, block, funcinfo);
                new ArmInstStroe(block, src, new IPhyReg("sp"), offset);
            }
        }
        if (args.size() > 4) {
            var rhs = ResolveIImmOperand((args.size() - 4) * 4, block, funcinfo);
            new ArmInstBinary(block, ArmInstKind.IAdd, new IPhyReg("sp"), new IPhyReg("sp"), rhs);
        }
        new ArmInstCall(block, funcMap.get(inst.getCallee()));
        if (!inst.getType().isVoid()) {
            var dst = ResolveOperand(inst, block, funcinfo);
            new ArmInstMove(block, dst, new IPhyReg("r0"));
        }
    }

    private void ResolveReturnInst(ReturnInst inst, ArmBlock block, FunctionInfo funcinfo) {
        if (inst.getReturnValue().isPresent()) {
            var src = ResolveLhsOperand(inst.getReturnValue().get(), block, funcinfo);
            new ArmInstMove(block, new IPhyReg("r0"), src);
        }
        new ArmInstReturn(block);
    }

    // 需要先转到浮点寄存器 才能使用 vcvt
    private void ResolveIntToFloatInst(IntToFloatInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var vr = new FVirtualReg();
        var src = ResolveOperand(inst.getFrom(), block, funcinfo);
        var dst = ResolveOperand(inst, block, funcinfo);
        new ArmInstMove(block, vr, src);
        new ArmInstIntToFloat(block, dst, vr);
    }

    // 先使用 vcvt 再转到整型寄存器中
    private void ResolveFloatToIntInst(FloatToIntInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var vr = new FVirtualReg();
        var src = ResolveOperand(inst.getFrom(), block, funcinfo);
        new ArmInstFloatToInt(block, vr, src);
        var dst = ResolveOperand(inst, block, funcinfo);
        new ArmInstMove(block, dst, vr);
    }

    private void ResolveCAllocInst(CAllocInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var offset = ResolveIImmOperand(funcinfo.getStackSize(), block, funcinfo);
        var dst = ResolveOperand(inst, block, funcinfo);
        new ArmInstBinary(block, ArmInstKind.IAdd, dst, new IPhyReg("sp"), offset);
        funcinfo.addStackSize(inst.getAllocSize());
    }

    private void ResolveBrInst(BrInst inst, ArmBlock block, FunctionInfo funcinfo) {
        new ArmInstBranch(block, blockMap.get(inst.getNextBB()));
    }

    private void ResolveBrCondInst(BrCondInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var cond = inst.getCond();
        if (cond instanceof BoolConst) {
            var boolConst = (BoolConst) cond;
            if (boolConst.getValue()) {
                new ArmInstBranch(block, blockMap.get(inst.getTrueBB()));
            } else {
                new ArmInstBranch(block, blockMap.get(inst.getFalseBB()));
            }
        } else if (cond instanceof CmpInst) {
            var Armcond = ResolveCmpInst((CmpInst) cond, block, funcinfo);
            new ArmInstBranch(block, blockMap.get(inst.getTrueBB()), Armcond);
            new ArmInstBranch(block, blockMap.get(inst.getFalseBB()));
        } else {
            Log.ensure(false);
        }
    }

    private ArmCondType ResolveCmpInst(CmpInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var lhs = inst.getLHS();
        var rhs = inst.getRHS();
        var cond = condMap.get(inst.getKind());

        for (var ch : Arrays.asList(lhs, rhs)) {
            if (ch instanceof BoolToIntInst) {
                ResolveBoolToIntInst((BoolToIntInst) ch, block, funcinfo);
            }
        }

        Operand lhsReg, rhsReg;
        if (lhs instanceof Constant) {
            lhsReg = ResolveLhsOperand(rhs, block, funcinfo);
            rhsReg = ResolveOperand(lhs, block, funcinfo);
            cond = cond.getOppCondType();
        } else {
            lhsReg = ResolveLhsOperand(lhs, block, funcinfo);
            rhsReg = ResolveOperand(rhs, block, funcinfo);
        }

        new ArmInstCmp(block, lhsReg, rhsReg, cond);
        return cond;
    }

    private void ResolveBoolToIntInst(BoolToIntInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var src = inst.getFrom();
        var dstReg = ResolveOperand(inst, block, funcinfo);
        if (src instanceof BoolConst) {
            var bc = (BoolConst) src;
            if (bc.getValue()) {
                new ArmInstMove(block, dstReg, new IImm(1));
            } else {
                new ArmInstMove(block, dstReg, new IImm(0));
            }
        } else if (src instanceof CmpInst) {
            var cond = ResolveCmpInst((CmpInst) src, block, funcinfo);
            new ArmInstMove(block, dstReg, new IImm(1), cond);
            new ArmInstMove(block, dstReg, new IImm(0), cond.getOppCondType());
        } else {
            Log.ensure(false);
        }
    }

    // code gen arm

    public StringBuilder CodeGenArm() {
        var arm = new StringBuilder();
        arm.append(".arch armv7ve\n");
        if (!globalvars.isEmpty()) {
            arm.append("\n\n.data\n.align 4\n");
        }
        for (var entry : globalvars.entrySet()) {
            var key = entry.getKey();
            var val = entry.getValue().getInit();
            arm.append(".global\t" + key + "\n" + key + ":\n");
            if (val instanceof IntConst) {
                arm.append(CodeGenIntConst((IntConst) val));
            } else if (val instanceof FloatConst) {
                arm.append(CodeGenFloatConst((FloatConst) val));
            } else if (val instanceof ArrayConst) {
                arm.append(CodeGenArrayConst((ArrayConst) val));
            }
        }

        return arm;
    }

    private String CodeGenIntConst(IntConst val) {
        return "\t" + ".word" + "\t" + Integer.toString(val.getValue()) + "\n";
    }

    private String CodeGenIntConst(int val) {
        return "\t" + ".word" + "\t" + Integer.toString(val) + "\n";
    }

    private String CodeGenFloatConst(FloatConst val) {
        return "\t" + ".word" + "\t" + Integer.toHexString(Float.floatToIntBits(val.getValue())) + "\n";
    }

    private String CodeGenFloatConst(Float val) {
        return "\t" + ".word" + "\t" + Integer.toHexString(Float.floatToIntBits(val)) + "\n";
    }

    private String CodeGenArrayConst(ArrayConst val) {
        var sb = new StringBuilder();
        for (var elem : val.getRawElements()) {
            if (elem instanceof IntConst) {
                return CodeGenIntArrayConst(val);
                // sb.append(CodeGenIntConst((IntConst) elem));
            } else if (elem instanceof FloatConst) {
                return CodeGenFloatArrayConst(val);
                // sb.append(CodeGenFloatConst((FloatConst) elem));
            } else if (elem instanceof ArrayConst) {
                sb.append(CodeGenArrayConst((ArrayConst) elem));
            }
        }
        return sb.toString();
    }

    private String CodeGenIntArrayConst(ArrayConst arr) {
        var sb = new StringBuilder();
        int cnt = 0, val = 0;
        boolean frist = true;
        for (var elem : arr.getRawElements()) {
            Log.ensure(elem instanceof IntConst);
            var ic = (IntConst) elem;
            if (val == ic.getValue() && frist == false) {
                cnt++;
            } else {
                if (cnt == 1) {
                    sb.append(CodeGenIntConst(val));
                } else if (cnt > 1) {
                    if (val == 0) {
                        sb.append("\t" + ".zero" + "\t" + Integer.toString(4 * cnt) + "\n");
                    } else {
                        sb.append("\t" + ".fill" + "\t" + Integer.toString(cnt) + ",\t4,\t"
                                + Integer.toString(val) + "\n");
                    }
                }
                cnt = 1;
                val = ic.getValue();
                frist = false;
            }
        }
        if (cnt == 1) {
            sb.append(CodeGenIntConst(val));
        } else if (cnt > 1) {
            if (val == 0) {
                sb.append("\t" + ".zero" + "\t" + Integer.toString(4 * cnt) + "\n");
            } else {
                sb.append("\t" + ".fill" + "\t" + Integer.toString(cnt) + ",\t4,\t"
                        + Integer.toString(val) + "\n");
            }
        }
        return sb.toString();
    }

    private String CodeGenFloatArrayConst(ArrayConst arr) {
        var sb = new StringBuilder();
        int cnt = 0;
        float val = 0;
        boolean frist = true;
        for (var elem : arr.getRawElements()) {
            Log.ensure(elem instanceof FloatConst);
            var fc = (FloatConst) elem;
            if (val == fc.getValue() && frist == false) {
                cnt++;
            } else {
                if (cnt == 1) {
                    sb.append(CodeGenFloatConst(val));
                } else if (cnt > 1) {
                    if (val == 0) {
                        sb.append("\t" + ".zero" + "\t" + Integer.toString(4 * cnt) + "\n");
                    } else {
                        sb.append("\t" + ".fill" + "\t" + Integer.toString(cnt) + ",\t4,\t"
                                + Float.toString(val) + "\n");
                    }
                }
                cnt = 1;
                val = fc.getValue();
                frist = false;
            }
        }
        if (cnt == 1) {
            sb.append(CodeGenFloatConst(val));
        } else if (cnt > 1) {
            if (val == 0) {
                sb.append("\t" + ".zero" + "\t" + Integer.toString(4 * cnt) + "\n");
            } else {
                sb.append("\t" + ".fill" + "\t" + Integer.toString(cnt) + ",\t4,\t"
                        + Float.toString(val) + "\n");
            }
        }
        return sb.toString();
    }
}
