package top.origami404.ssyc.backend.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.origami404.ssyc.backend.arm.ArmBlock;
import top.origami404.ssyc.backend.arm.ArmFunction;
import top.origami404.ssyc.backend.arm.ArmInstBinary;
import top.origami404.ssyc.backend.arm.ArmInstLoad;
import top.origami404.ssyc.backend.arm.ArmFunction.FunctionInfo;
import top.origami404.ssyc.backend.arm.ArmInst.ArmInstKind;
import top.origami404.ssyc.backend.arm.ArmInstMove;
import top.origami404.ssyc.backend.arm.ArmInstUnary;
import top.origami404.ssyc.backend.operand.FImm;
import top.origami404.ssyc.backend.operand.FVirtualReg;
import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.IPhyReg;
import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.backend.operand.IVirtualReg;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.Parameter;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.FloatConst;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.AllocInst;
import top.origami404.ssyc.ir.inst.BinaryOpInst;
import top.origami404.ssyc.ir.inst.InstKind;
import top.origami404.ssyc.ir.inst.LoadInst;
import top.origami404.ssyc.ir.inst.UnaryOpInst;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

public class CodeGenManager {
    private List<ArmFunction> functions;
    private Map<Value, Operand> valMap;
    private Map<Function, ArmFunction> funcMap;
    private Map<BasicBlock, ArmBlock> blockMap;
    private Map<String, AllocInst> globalvars;
    private Set<AllocInst> globalSet;

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
        globalSet = new HashSet<>();
        for (var val : globalvars.values()) {
            globalSet.add(val);
            valMap.put(val, new IVirtualReg(val.getName(), true));
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
        if (checkEncodeIImm(val.getValue()) || checkEncodeIImm(~val.getValue())) {
            return new IImm(val.getValue());
        } else {
            var vr = new IVirtualReg();
            var imm = new IImm(val.getValue());
            funcinfo.addImm(imm);
            new ArmInstLoad(block, vr, imm);
            return vr;
        }
    }

    private Operand ResolveFImmOperand(FloatConst val, ArmBlock block, FunctionInfo funcinfo) {
        var vr = new IVirtualReg();
        var imm = new FImm(val.getValue());
        funcinfo.addImm(imm);
        new ArmInstLoad(block, vr, imm);
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
            if (val.getParamType() == IRType.FloatTy) {
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
                        new ArmInstLoad(funcinfo.getStartBlock(), vr, new IPhyReg(13), new IImm((i - 4) << 2));
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
            var imm = new IImm(((IntConst) val).getValue());
            funcinfo.addImm(imm);
            new ArmInstLoad(block, vr, imm);
            return vr;
        } else if (val instanceof FloatConst) {
            return ResolveFImmOperand((FloatConst) val, block, funcinfo);
        } else {
            return ResolveOperand(val, block, funcinfo);
        }
    }

    private Operand ResolveOperand(Value val, ArmBlock block, FunctionInfo funcinfo) {
        if (val instanceof Constant) {
            return ResolveImmOperand((Constant) val, block, funcinfo);
        } else if (val instanceof Parameter && funcinfo.getParameter().contains(val)) {
            return ResolveParameter((Parameter) val, block, funcinfo);
        } else if (globalSet.contains(val)) {
            Log.ensure(valMap.containsKey(val));
            return valMap.get(val);
        } else {
            if (valMap.containsKey(val)) {
                return valMap.get(val);
            } else {
                Operand vr;
                if (val.getType() == IRType.FloatTy) {
                    vr = new FVirtualReg();
                } else {
                    vr = new IVirtualReg();
                }
                valMap.put(val, vr);
                return vr;
            }
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
}
