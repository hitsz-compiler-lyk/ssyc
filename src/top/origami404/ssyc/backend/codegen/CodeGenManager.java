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
import top.origami404.ssyc.backend.operand.FImm;
import top.origami404.ssyc.backend.operand.FPhyReg;
import top.origami404.ssyc.backend.operand.FVirtualReg;
import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.IPhyReg;
import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.backend.operand.Addr;
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
    private static final Map<InstKind, ArmCondType> condMap = new HashMap<>() {
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
        functions = new ArrayList<>();
        valMap = new HashMap<>();
        funcMap = new HashMap<>();
        blockMap = new HashMap<>();
    }

    public List<ArmFunction> getFunctions() {
        return functions;
    }

    public void addFunction(ArmFunction func) {
        functions.add(func);
    }

    public void genArm(Module irModule) {
        globalvars = new HashMap<>();
        for (final var gv : irModule.getVariables()) {
            globalvars.put(gv.getSymbol().getName(), gv);
        }

        // 添加Global信息
        for (var val : globalvars.values()) {
            valMap.put(val, new Addr(val.getSymbol().getName(), true));
        }

        for (var func : irModule.getFunctions()) {
            var armFunc = new ArmFunction(func.getFunctionSourceName());
            armFunc.getFuncInfo().setParameter(func.getParameters());
            functions.add(armFunc);
            funcMap.put(func, armFunc);

            for (var block : func.asElementView()) {
                var armblock = new ArmBlock(armFunc, block.getLabelName());
                blockMap.put(block, armblock);
            }

            // 处理起始块的后驱和第一个基本块的前驱
            if (func.asElementView().size() > 0) {
                var armblock = blockMap.get(func.asElementView().get(0));
                armFunc.getFuncInfo().getStartBlock().setTrueSuccBlock(armblock);
                armblock.addPred(armFunc.getFuncInfo().getStartBlock());
            }

            // 处理Arm基本块的前后驱
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
                        resolveBinaryInst((BinaryOpInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof UnaryOpInst) {
                        resolveUnaryInst((UnaryOpInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof LoadInst) {
                        resolveLoadInst((LoadInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof StoreInst) {
                        resolveStoreInst((StoreInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof CAllocInst) {
                        resolveCAllocInst((CAllocInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof GEPInst) {
                        resolveGEPInst((GEPInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof CallInst) {
                        resolveCallInst((CallInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof ReturnInst) {
                        resolveReturnInst((ReturnInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof IntToFloatInst) {
                        resolveIntToFloatInst((IntToFloatInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof FloatToIntInst) {
                        resolveFloatToIntInst((FloatToIntInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof BrInst) {
                        resolveBrInst((BrInst) inst, armBlock, armFunc.getFuncInfo());
                    } else if (inst instanceof BrCondInst) {
                        resolveBrCondInst((BrCondInst) inst, armBlock, armFunc.getFuncInfo());
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

            // Phi 处理, 相当于在每个基本块最后都添加一条MOVE指令 将incoming基本块里面的Value Move到当前基本块的Value
            // MOVE Phi Incoming.Value
            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                var phiIt = block.iterPhis();
                while (phiIt.hasNext()) {
                    var phi = phiIt.next();
                    var incomingInfoIt = phi.getIncomingInfos().iterator();
                    var phiReg = resolveOperand(phi, armBlock, armFunc.getFuncInfo());
                    while (incomingInfoIt.hasNext()) {
                        var incomingInfo = incomingInfoIt.next();
                        var src = incomingInfo.getValue();
                        var incomingBlock = blockMap.get(incomingInfo.getBlock());
                        var srcReg = resolveOperand(src, incomingBlock, armFunc.getFuncInfo());
                        new ArmInstMove(incomingBlock, phiReg, srcReg);
                    }
                }
            }

        }
    }

    public static boolean checkEncodeImm(int imm) {
        int n = imm;
        for (int i = 0; i < 32; i += 2) {
            if ((n & ~0xFF) == 0) {
                return true;
            }
            n = (n << 2) | (n >>> 30);
        }
        return false;
    }

    private Operand resolveIImmOperand(IntConst val, ArmBlock block, FunctionInfo funcinfo) {
        if (checkEncodeImm(val.getValue())) {
            // 可以直接表示 直接返回一个IImm
            return new IImm(val.getValue());
        } else {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new IImm(val.getValue());
            // MOV32 VR #imm32
            new ArmInstMove(block, vr, addr);
            return vr;
        }
    }

    private Operand resolveIImmOperand(int val, ArmBlock block, FunctionInfo funcinfo) {
        if (checkEncodeImm(val)) {
            // 可以直接表示 直接返回一个IImm
            return new IImm(val);
        } else {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new IImm(val);
            // MOV32 VR #imm32
            new ArmInstMove(block, vr, addr);
            return vr;
        }
    }

    private Operand resolveFImmOperand(FloatConst val, ArmBlock block, FunctionInfo funcinfo) {
        if (checkEncodeImm(Float.floatToIntBits(val.getValue()))) {
            // 可以直接表示 直接返回一个FImm
            return new FImm(val.getValue());
        } else {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new FImm(val.getValue());
            // VlDR VR =imm32
            new ArmInstLoad(block, vr, addr);
            return vr;
        }
    }

    private Operand resolveImmOperand(Constant val, ArmBlock block, FunctionInfo funcinfo) {
        if (val instanceof IntConst) {
            return resolveIImmOperand((IntConst) val, block, funcinfo);
        } else if (val instanceof FloatConst) {
            return resolveFImmOperand((FloatConst) val, block, funcinfo);
        } else {
            Log.ensure(false, "Resolve Operand: Bool or Array Constant");
            return null;
        }
    }

    private Operand resolveParameter(Parameter val, ArmBlock block, FunctionInfo funcinfo) {
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
                if (params.get(i).equals(val)) {
                    if (i < 4) {
                        // MOVE VR Ri
                        // R0 - R3 在后续的基本块中会修改 因此需要在最前面的块当中就读取出来
                        new ArmInstMove(funcinfo.getStartBlock(), vr, new IPhyReg(i));
                    } else {
                        // LDR VR [SP, (i-4)*4]
                        // 寄存器分配后修改为 LDR VR [SP, (i-4)*4 + stackSize + push的大小]
                        var offset = resolveIImmOperand((i - 4) * 4, block, funcinfo);
                        // 保证这个MOVE语句一定在最前面
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

    // 字面量不能直接给予
    private Operand resolveLhsOperand(Value val, ArmBlock block, FunctionInfo funcinfo) {
        // 因为不是最后一个操作数, 不能直接给予一个立即数
        if (val instanceof IntConst) {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new IImm(((IntConst) val).getValue());
            // MOV32 VR #imm32
            new ArmInstMove(block, vr, addr);
            return vr;
        } else if (val instanceof FloatConst) {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new FImm(((FloatConst) val).getValue());
            // VlDR VR =imm32
            new ArmInstLoad(block, vr, addr);
            return vr;
        } else {
            return resolveOperand(val, block, funcinfo);
        }
    }

    private Operand resolveGlobalVar(GlobalVar val, ArmBlock block, FunctionInfo funcinfo) {
        // 全局变量应该事先处理
        Log.ensure(valMap.containsKey(val));
        return valMap.get(val);
    }

    private Operand resolveOperand(Value val, ArmBlock block, FunctionInfo funcinfo) {
        if (val instanceof Constant) {
            return resolveImmOperand((Constant) val, block, funcinfo);
        } else if (val instanceof Parameter && funcinfo.getParameter().contains(val)) {
            return resolveParameter((Parameter) val, block, funcinfo);
        } else if (val instanceof GlobalVar) {
            return resolveGlobalVar((GlobalVar) val, block, funcinfo);
        } else {
            if (valMap.containsKey(val)) {
                return valMap.get(val);
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
    }

    private void resolveBinaryInst(BinaryOpInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var lhs = inst.getLHS();
        var rhs = inst.getRHS();
        var dst = inst;
        Operand lhsReg, rhsReg, dstReg;

        switch (inst.getKind()) {
            case IAdd: {
                // 这里其实可以加一个判断逻辑 如果 ~imm是合法条件 是不是可以变成减法 从而减少一个MOV32
                if (lhs instanceof Constant) {
                    lhsReg = resolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = resolveOperand(lhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = resolveOperand(rhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                }
                // ADD inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IAdd, dstReg, lhsReg, rhsReg);
                break;
            }
            case ISub: {
                if (lhs instanceof Constant) {
                    // 操作数交换 使用反向减法
                    lhsReg = resolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = resolveOperand(lhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                    // RSB inst inst.getRHS() inst.getLHS()
                    new ArmInstBinary(block, ArmInstKind.IRsb, dstReg, lhsReg, rhsReg);
                } else {
                    lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = resolveOperand(rhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                    // SUB inst inst.getLHS() inst.getRHS()
                    new ArmInstBinary(block, ArmInstKind.ISub, dstReg, lhsReg, rhsReg);
                }
                break;
            }
            case IMul: {
                if (lhs instanceof Constant) {
                    lhsReg = resolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = resolveOperand(lhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = resolveOperand(rhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                }
                // MUL inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IMul, dstReg, lhsReg, rhsReg);
                break;
            }
            case IDiv: {
                // 除法无法交换操作数
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveOperand(rhs, block, funcinfo);
                dstReg = resolveOperand(dst, block, funcinfo);
                // SDIV inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IDiv, dstReg, lhsReg, rhsReg);
                break;
            }
            case IMod: {
                // x % y == x - (x / y) *y
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveOperand(rhs, block, funcinfo);
                dstReg = resolveOperand(dst, block, funcinfo);
                var vr = new IVirtualReg();
                // SDIV VR inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IDiv, vr, lhsReg, rhsReg);
                // MLS inst VR inst.getRHS() inst.getLHS()
                // inst = inst.getLHS() - VR * inst.getRHS()
                new ArmInstTernay(block, ArmInstKind.IMulSub, dstReg, vr, rhsReg, lhsReg);
            }
            case FAdd: {
                if (lhs instanceof Constant) {
                    lhsReg = resolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = resolveOperand(lhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = resolveOperand(rhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                }
                // VADD.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FAdd, dstReg, lhsReg, rhsReg);
            }
            case FSub: {
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveOperand(rhs, block, funcinfo);
                dstReg = resolveOperand(dst, block, funcinfo);
                // VSUB.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FSub, dstReg, lhsReg, rhsReg);
            }
            case FMul: {
                if (lhs instanceof Constant) {
                    lhsReg = resolveLhsOperand(rhs, block, funcinfo);
                    rhsReg = resolveOperand(lhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                } else {
                    lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                    rhsReg = resolveOperand(rhs, block, funcinfo);
                    dstReg = resolveOperand(dst, block, funcinfo);
                }
                // VMUL.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FMul, dstReg, lhsReg, rhsReg);
            }
            case FDiv: {
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveOperand(rhs, block, funcinfo);
                dstReg = resolveOperand(dst, block, funcinfo);
                // VDIV.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FDiv, dstReg, lhsReg, rhsReg);
            }
            default: {
                Log.ensure(false, "binary inst not implement");
            }
        }
    }

    private void resolveUnaryInst(UnaryOpInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var src = inst.getArg();
        var dst = inst;
        var srcReg = resolveOperand(src, block, funcinfo);
        var dstReg = resolveLhsOperand(dst, block, funcinfo);

        if (inst.getKind() == InstKind.INeg) {
            // new ArmInstBinary(block, ArmInstKind.IRsb, dstReg, srcReg, new IImm(0));
            // NEG inst inst.getArg()
            new ArmInstUnary(block, ArmInstKind.INeg, dstReg, srcReg);
        } else if (inst.getKind() == InstKind.FNeg) {
            // VNEG.F32 inst inst.getArg()
            new ArmInstUnary(block, ArmInstKind.FNeg, dstReg, srcReg);
        }
    }

    private void resolveLoadInst(LoadInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var addr = inst.getPtr();
        var dst = inst;

        var addrReg = resolveOperand(addr, block, funcinfo);
        var dstReg = resolveLhsOperand(dst, block, funcinfo);
        // LDR inst inst.getPtr()
        new ArmInstLoad(block, dstReg, addrReg);
    }

    private void resolveStoreInst(StoreInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var addr = inst.getPtr();
        var var = inst.getVal();

        var addrReg = resolveOperand(addr, block, funcinfo);
        var varReg = resolveLhsOperand(var, block, funcinfo);
        // STR inst.getVal() inst.getPtr()
        new ArmInstStroe(block, varReg, addrReg);
    }

    private void resolveGEPInst(GEPInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var p = inst.getType().getBaseType();
        var indices = inst.getIndices();
        ArrayList<Integer> dim = new ArrayList<>();

        while (p.isArray()) {
            dim.add(p.getSize());
            p = ((ArrayIRTy) p).getElementType();
        }
        // 加上最后一个基础类型 INT FLOAT的SIZE
        dim.add(p.getSize());

        // 原基地址
        var arr = resolveLhsOperand(inst.getPtr(), block, funcinfo);
        var ret = resolveLhsOperand(inst, block, funcinfo);
        var tot = 0;
        for (int i = 0; i < indices.size(); i++) {
            var offset = resolveOperand(indices.get(i), block, funcinfo);
            var length = dim.get(i);

            if (offset.IsIImm()) {
                tot += ((IImm) offset).getImm() * length;
                if (i == indices.size() - 1) {
                    if (tot == 0) {
                        // MOVR inst 当前地址
                        new ArmInstMove(block, ret, arr);
                    } else {
                        var imm = resolveIImmOperand(tot, block, funcinfo);
                        // ADD inst 当前地址 + 偏移量
                        new ArmInstBinary(block, ArmInstKind.IAdd, ret, arr, imm);
                    }
                }
            } else {
                if (tot != 0) {
                    var imm = resolveIImmOperand(tot, block, funcinfo);
                    var vr = new IVirtualReg();
                    // ADD VR 当前地址 + 偏移量
                    // 当前地址 = VR
                    new ArmInstBinary(block, ArmInstKind.IAdd, vr, arr, imm);
                    tot = 0;
                    arr = vr;
                }
                var imm = resolveIImmOperand(length, block, funcinfo);
                if (i == indices.size() - 1) {
                    // MLA inst dim.get(i) indices.get(i) 当前地址
                    // inst = dim.get(i)*indices.get(i) + 当前地址
                    new ArmInstTernay(block, ArmInstKind.IMulAdd, ret, offset, imm, arr);
                } else {
                    var vr = new IVirtualReg();
                    // MLA VR dim.get(i) indices.get(i) 当前地址
                    // VR = dim.get(i)*indices.get(i) + 当前地址
                    new ArmInstTernay(block, ArmInstKind.IMulAdd, vr, offset, imm, arr);
                    arr = vr;
                }
            }
        }
    }

    private void resolveCallInst(CallInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var args = inst.getArgList();
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (i < 4) {
                var src = resolveOperand(arg, block, funcinfo);
                // MOV Ri inst.args.get(i)
                new ArmInstMove(block, new IPhyReg(i), src);
            } else {
                var src = resolveLhsOperand(arg, block, funcinfo);
                var offset = resolveIImmOperand(-(args.size() - i) * 4, block, funcinfo);
                // STR inst.args.get(i) [SP, -(inst.args.size()-i)*4]
                // 越后面的参数越靠近栈顶
                new ArmInstStroe(block, src, new IPhyReg("sp"), offset);
            }
        }
        if (args.size() > 4) {
            var rhs = resolveIImmOperand((args.size() - 4) * 4, block, funcinfo);
            // SUB SP SP (inst.args.size() - 4) * 4
            new ArmInstBinary(block, ArmInstKind.ISub, new IPhyReg("sp"), new IPhyReg("sp"), rhs);
        }
        new ArmInstCall(block, funcMap.get(inst.getCallee()));
        if (args.size() > 4) {
            var rhs = resolveIImmOperand((args.size() - 4) * 4, block, funcinfo);
            // ADD SP SP (inst.args.size() - 4) * 4
            new ArmInstBinary(block, ArmInstKind.IAdd, new IPhyReg("sp"), new IPhyReg("sp"), rhs);
        }
        if (!inst.getType().isVoid()) {
            var dst = resolveLhsOperand(inst, block, funcinfo);
            if (inst.getType().isFloat()) {
                // 如果结果是一个浮点数 则直接用s0来保存数据
                // VMOV inst S0
                // atpcs 规范
                new ArmInstMove(block, dst, new FPhyReg("s0"));
            } else if (inst.getType().isInt()) {
                // 否则用r0来保存数据
                // MOV inst R0
                new ArmInstMove(block, dst, new IPhyReg("r0"));
            }
        }
    }

    private void resolveReturnInst(ReturnInst inst, ArmBlock block, FunctionInfo funcinfo) {
        // 如果返回值不为空
        if (inst.getReturnValue().isPresent()) {
            var src = inst.getReturnValue().get();
            var srcReg = resolveOperand(src, block, funcinfo);
            if(src.getType().isFloat()){
                // atpcs 规范
                // VMOV S0 inst.getReturnValue()
                new ArmInstMove(block, new FPhyReg("s0"), srcReg);
            }else{
                // VMOV R0 inst.getReturnValue()
                new ArmInstMove(block, new IPhyReg("r0"), srcReg);
            }

        }
        new ArmInstReturn(block);
    }

    // 需要先转到浮点寄存器 才能使用 vcvt
    private void resolveIntToFloatInst(IntToFloatInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var vr = new FVirtualReg();
        var src = resolveOperand(inst.getFrom(), block, funcinfo);
        var dst = resolveOperand(inst, block, funcinfo);
        // VMOV VR inst.getFrom()
        new ArmInstMove(block, vr, src);
        // VCVT.F32.S32 inst VR
        new ArmInstIntToFloat(block, dst, vr);
    }

    // 先使用 vcvt 再转到整型寄存器中
    private void resolveFloatToIntInst(FloatToIntInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var vr = new FVirtualReg();
        var src = resolveOperand(inst.getFrom(), block, funcinfo);
        var dst = resolveOperand(inst, block, funcinfo);
        // VCVT.F32.S32 inst VR
        new ArmInstFloatToInt(block, vr, src);
        // VMOV VR inst.getFrom()
        new ArmInstMove(block, dst, vr);
    }

    private void resolveCAllocInst(CAllocInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var offset = resolveIImmOperand(funcinfo.getStackSize(), block, funcinfo);
        var dst = resolveOperand(inst, block, funcinfo);
        // ADD inst [SP, 之前已用的栈大小]
        new ArmInstBinary(block, ArmInstKind.IAdd, dst, new IPhyReg("sp"), offset);
        // 增加栈大小
        funcinfo.addStackSize(inst.getAllocSize());
    }

    private void resolveBrInst(BrInst inst, ArmBlock block, FunctionInfo funcinfo) {
        // B inst.getNextBB()
        new ArmInstBranch(block, blockMap.get(inst.getNextBB()));
    }

    private void resolveBrCondInst(BrCondInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var cond = inst.getCond();
        if (cond instanceof BoolConst) {
            var boolConst = (BoolConst) cond;
            if (boolConst.getValue()) {
                // B inst.getTrueBB()
                new ArmInstBranch(block, blockMap.get(inst.getTrueBB()));
            } else {
                // B inst.getFalseBB()
                new ArmInstBranch(block, blockMap.get(inst.getFalseBB()));
            }
        } else if (cond instanceof CmpInst) {
            // 指令可能会因为左操作数为立即数而进行反向交换
            var Armcond = resolveCmpInst((CmpInst) cond, block, funcinfo);
            // B.{cond} inst.getTrueBB()
            new ArmInstBranch(block, blockMap.get(inst.getTrueBB()), Armcond);
            // B inst.getFalseBB()
            // 可以考虑在CodeGen的时候再添加 连续的基本块不需要该指令
            new ArmInstBranch(block, blockMap.get(inst.getFalseBB()));
        } else {
            Log.ensure(false,"BrCondInst Cond Illegal");
        }
    }

    private ArmCondType resolveCmpInst(CmpInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var lhs = inst.getLHS();
        var rhs = inst.getRHS();
        var cond = condMap.get(inst.getKind());

        for (var ch : Arrays.asList(lhs, rhs)) {
            if (ch instanceof BoolToIntInst) {
                resolveBoolToIntInst((BoolToIntInst) ch, block, funcinfo);
            }
        }

        Operand lhsReg, rhsReg;
        if (lhs instanceof Constant) {
            lhsReg = resolveLhsOperand(rhs, block, funcinfo);
            rhsReg = resolveOperand(lhs, block, funcinfo);
            // 反向交换
            cond = cond.getOppCondType();
        } else {
            lhsReg = resolveLhsOperand(lhs, block, funcinfo);
            rhsReg = resolveOperand(rhs, block, funcinfo);
        }

        // CMP( VCMP.F32 ) inst.getLHS() inst.getRHS() (可能交换LHS/RHS)
        // VMRS APSR_nzcv fpscr
        new ArmInstCmp(block, lhsReg, rhsReg, cond);
        return cond;
    }

    private void resolveBoolToIntInst(BoolToIntInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var src = inst.getFrom();
        var dstReg = resolveOperand(inst, block, funcinfo);
        if (src instanceof BoolConst) {
            var bc = (BoolConst) src;
            if (bc.getValue()) {
                // MOV inst #1
                new ArmInstMove(block, dstReg, new IImm(1));
            } else {
                // MOV inst #0
                new ArmInstMove(block, dstReg, new IImm(0));
            }
        } else if (src instanceof CmpInst) {
            var cond = resolveCmpInst((CmpInst) src, block, funcinfo);
            // MOV.{cond} inst #1
            new ArmInstMove(block, dstReg, new IImm(1), cond);
            // MOV.{OppCond} inst #0
            new ArmInstMove(block, dstReg, new IImm(0), cond.getOppCondType());
        } else {
            Log.ensure(false);
        }
    }

    // code gen arm

    public StringBuilder codeGenArm() {
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
                arm.append(codeGenIntConst((IntConst) val));
            } else if (val instanceof FloatConst) {
                arm.append(codeGenFloatConst((FloatConst) val));
            } else if (val instanceof ArrayConst) {
                arm.append(codeGenArrayConst((ArrayConst) val));
            }
        }

        return arm;
    }

    private String codeGenIntConst(IntConst val) {
        return "\t" + ".word" + "\t" + val + "\n";
    }

    private String codeGenFloatConst(FloatConst val) {
        return "\t" + ".word" + "\t" + val + "\n";
    }

    private String codeGenArrayConst(ArrayConst val) {
        var sb = new StringBuilder();
        for (var elem : val.getRawElements()) {
            if (elem instanceof IntConst) {
                return codeGenIntArrayConst(val);
                // sb.append(CodeGenIntConst((IntConst) elem));
            } else if (elem instanceof FloatConst) {
                return codeGenFloatArrayConst(val);
                // sb.append(CodeGenFloatConst((FloatConst) elem));
            } else if (elem instanceof ArrayConst) {
                sb.append(codeGenArrayConst((ArrayConst) elem));
            }
        }
        return sb.toString();
    }

    private String codeGenIntArrayConst(ArrayConst arr) {
        var sb = new StringBuilder();
        int cnt = 0;
        IntConst val = null;
        for (var elem : arr.getRawElements()) {
            Log.ensure(elem instanceof IntConst);
            var ic = (IntConst) elem;
            if (val != null && val.getValue() == ic.getValue()) {
                cnt++;
            } else {
                if (cnt == 1) {
                    sb.append(codeGenIntConst(val));
                } else if (cnt > 1) {
                    if (val.getValue() == 0) {
                        sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
                    } else {
                        sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
                    }
                }
                cnt = 1;
                val = ic;
            }
        }
        if (cnt == 1) {
            sb.append(codeGenIntConst(val));
        } else if (cnt > 1) {
            if (val.getValue() == 0) {
                sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
            } else {
                sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t"
                        + val + "\n");
            }
        }
        return sb.toString();
    }

    private String codeGenFloatArrayConst(ArrayConst arr) {
        var sb = new StringBuilder();
        int cnt = 0;
        FloatConst val = null;
        for (var elem : arr.getRawElements()) {
            Log.ensure(elem instanceof FloatConst);
            var fc = (FloatConst) elem;
            if (val != null && (val.getValue() == fc.getValue())) {
                cnt++;
            } else {
                if (cnt == 1) {
                    sb.append(codeGenFloatConst(val));
                } else if (cnt > 1) {
                    if (val.getValue() == 0) {
                        sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
                    } else {
                        sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
                    }
                }
                cnt = 1;
                val = fc;
            }
        }
        if (cnt == 1) {
            sb.append(codeGenFloatConst(val));
        } else if (cnt > 1) {
            if (val.getValue() == 0) {
                sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
            } else {
                sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
            }
        }
        return sb.toString();
    }
}
