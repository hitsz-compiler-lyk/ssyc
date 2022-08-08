package backend.codegen;

import java.net.ContentHandler;
import java.net.FileNameMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import backend.arm.ArmBlock;
import backend.arm.ArmFunction;
import backend.arm.ArmInst;
import backend.arm.ArmInstBinary;
import backend.arm.ArmInstBranch;
import backend.arm.ArmInstCall;
import backend.arm.ArmInstCmp;
import backend.arm.ArmInstFloatToInt;
import backend.arm.ArmInstIntToFloat;
import backend.arm.ArmInstLoad;
import backend.arm.ArmInstLtorg;
import backend.arm.ArmFunction.FunctionInfo;
import backend.arm.ArmInst.ArmCondType;
import backend.arm.ArmInst.ArmInstKind;
import backend.arm.ArmInstMove;
import backend.arm.ArmInstReturn;
import backend.arm.ArmInstStore;
import backend.arm.ArmInstTernay;
import backend.arm.ArmInstUnary;
import backend.operand.FImm;
import backend.operand.FPhyReg;
import backend.operand.FVirtualReg;
import backend.operand.IImm;
import backend.operand.IPhyReg;
import backend.operand.Operand;
import backend.regallocator.RegAllocator;
import backend.regallocator.SimpleGraphColoring;
import backend.operand.Addr;
import backend.operand.IVirtualReg;
import ir.BasicBlock;
import ir.Function;
import ir.GlobalVar;
import ir.Module;
import ir.Parameter;
import ir.Value;
import ir.constant.ArrayConst;
import ir.constant.BoolConst;
import ir.constant.Constant;
import ir.constant.FloatConst;
import ir.constant.IntConst;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.inst.BinaryOpInst;
import ir.inst.BoolToIntInst;
import ir.inst.BrCondInst;
import ir.inst.BrInst;
import ir.inst.CAllocInst;
import ir.inst.CallInst;
import ir.inst.CmpInst;
import ir.inst.FloatToIntInst;
import ir.inst.GEPInst;
import ir.inst.InstKind;
import ir.inst.IntToFloatInst;
import ir.inst.LoadInst;
import ir.inst.MemInitInst;
import ir.inst.PhiInst;
import ir.inst.ReturnInst;
import ir.inst.StoreInst;
import ir.inst.UnaryOpInst;
import ir.type.ArrayIRTy;
import ir.type.PointerIRTy;
import utils.Log;

public class CodeGenManager {
    private List<ArmFunction> functions;
    private Map<Value, Operand> valMap;
    private Map<Function, ArmFunction> funcMap;
    private Map<BasicBlock, ArmBlock> blockMap;
    private Map<String, GlobalVar> globalvars;
    private Map<ArrayConst, String> arrayConstMap;
    private List<ArmFunction> externalFunctions;
    private Map<GlobalVar, Operand> globalMap;
    private boolean isResolvePhi;
    private RegAllocator regAllocator;
    private ArmInstMove globalMove;
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
        arrayConstMap = new HashMap<>();
        externalFunctions = new ArrayList<>();
        globalMap = new HashMap<>();
        isResolvePhi = false;
        regAllocator = new SimpleGraphColoring();
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

        int acCnt = 0;
        for (var val : irModule.getArrayConstants()) {
            arrayConstMap.put(val, "meminit_array_" + acCnt);
            valMap.put(val, new Addr("meminit_array_" + acCnt++, true));
        }

        // 添加Global信息
        for (var val : globalvars.values()) {
            valMap.put(val, new Addr(val.getSymbol().getName(), true));
        }

        for (var func : irModule.getFunctions()) {
            if (func.isExternal()) {
                var armFunc = new ArmFunction(func.getFunctionSourceName(), func.getType().getParamTypes().size());
                externalFunctions.add(armFunc);
                var params = func.getType().getParamTypes();
                int fcnt = 0, icnt = 0;
                for (var param : params) {
                    if (param.isFloat()) {
                        fcnt++;
                    } else {
                        icnt++;
                    }
                }
                armFunc.setFparamsCnt(Integer.min(fcnt, 16));
                armFunc.setIparamsCnt(Integer.min(icnt, 4));
                armFunc.setReturnFloat(func.getType().getReturnType().isFloat());
                armFunc.setExternal(true);
                funcMap.put(func, armFunc);
                continue;
            }

            var armFunc = new ArmFunction(func.getFunctionSourceName(), func.getParameters().size());
            Set<Parameter> paramIdx = new HashSet<>();
            List<Parameter> finalParams = new ArrayList<>();
            int fcnt = 0, icnt = 0;
            for (var param : func.getParameters()) {
                if (!param.getType().isFloat()) {
                    finalParams.add(param);
                    paramIdx.add(param);
                    icnt++;
                }
                if (icnt >= 4) {
                    break;
                }
            }
            for (var param : func.getParameters()) {
                if (param.getType().isFloat()) {
                    finalParams.add(param);
                    paramIdx.add(param);
                    fcnt++;
                }
                if (fcnt >= 16) {
                    break;
                }
            }
            for (var param : func.getParameters()) {
                if (paramIdx.contains(param)) {
                    continue;
                }
                finalParams.add(param);
            }
            functions.add(armFunc);
            armFunc.getFuncInfo().setParameter(finalParams);
            armFunc.setFparamsCnt(fcnt);
            armFunc.setIparamsCnt(icnt);
            armFunc.setReturnFloat(func.getType().getReturnType().isFloat());
            funcMap.put(func, armFunc);
            String funcName = func.getFunctionSourceName();

            var blocks = func.asElementView();
            for (int i = 0; i < blocks.size(); i++) {
                var block = blocks.get(i);
                var armblock = new ArmBlock(armFunc, block.getSymbol().getName() + "_" + funcName + "_" + i);
                blockMap.put(block, armblock);
            }

            // 处理起始块的后驱和第一个基本块的前驱
            if (func.asElementView().size() > 0) {
                var armblock = blockMap.get(func.asElementView().get(0));
                armFunc.getFuncInfo().getPrologue().setTrueSuccBlock(armblock);
                armblock.addPred(armFunc.getFuncInfo().getPrologue());
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
        }

        for (var func : irModule.getFunctions()) {
            if (func.isExternal()) {
                continue;
            }
            var armFunc = funcMap.get(func);
            var funcInfo = armFunc.getFuncInfo();

            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                // 清空globalMap 不允许跨基本块读取Global地址
                globalMap.clear();

                for (var inst : block.asElementView()) {
                    if (inst instanceof BinaryOpInst) {
                        resolveBinaryInst((BinaryOpInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof UnaryOpInst) {
                        resolveUnaryInst((UnaryOpInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof LoadInst) {
                        resolveLoadInst((LoadInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof StoreInst) {
                        resolveStoreInst((StoreInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof CAllocInst) {
                        resolveCAllocInst((CAllocInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof GEPInst) {
                        resolveGEPInst((GEPInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof CallInst) {
                        resolveCallInst((CallInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof ReturnInst) {
                        resolveReturnInst((ReturnInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof IntToFloatInst) {
                        resolveIntToFloatInst((IntToFloatInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof FloatToIntInst) {
                        resolveFloatToIntInst((FloatToIntInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof BrInst) {
                        resolveBrInst((BrInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof BrCondInst) {
                        resolveBrCondInst((BrCondInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof MemInitInst) {
                        resolveMemInitInst((MemInitInst) inst, armBlock, funcInfo);
                    } else if (inst instanceof BoolToIntInst) {
                        continue;
                    } else if (inst instanceof CmpInst) {
                        continue;
                    } else if (inst instanceof PhiInst) {
                        continue;
                    } else {
                        Log.ensure(false, "not implement");
                    }
                }
            }

            // Phi 处理, 相当于在每个基本块最后都添加一条MOVE指令 将incoming基本块里面的Value Move到当前基本块的Value
            // MOVE Phi Incoming.Value
            isResolvePhi = true;
            Map<ArmBlock, ArmInst> fristBranch = new HashMap<>();
            Map<ArmBlock, List<ArmInstMove>> phiMoveLists = new HashMap<>();
            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                phiMoveLists.put(armBlock, new ArrayList<>());
                for (var inst : armBlock.asElementView()) {
                    if (inst instanceof ArmInstBranch) {
                        fristBranch.put(armBlock, inst);
                        break;
                    }
                }
            }

            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                var phiIt = block.iterPhis();
                while (phiIt.hasNext()) {
                    var phi = phiIt.next();
                    var incomingInfoIt = phi.getIncomingInfos().iterator();
                    var phiReg = resolveOperand(phi, armBlock, funcInfo);
                    while (incomingInfoIt.hasNext()) {
                        var incomingInfo = incomingInfoIt.next();
                        var src = incomingInfo.getValue();
                        var incomingBlock = blockMap.get(incomingInfo.getBlock());
                        var srcReg = resolvePhiOperand(src, incomingBlock, funcInfo);
                        var incomingPhiList = phiMoveLists.get(incomingBlock);
                        if (srcReg.IsImm()) {
                            var move = new ArmInstMove(incomingBlock, phiReg, srcReg);
                            incomingPhiList.add(move);
                        } else {
                            Operand vr;
                            if (phiReg.IsInt()) {
                                vr = new IVirtualReg();
                            } else {
                                vr = new FVirtualReg();
                            }
                            var move = new ArmInstMove(vr, srcReg);
                            incomingPhiList.add(0, move);
                            move = new ArmInstMove(phiReg, vr);
                            incomingPhiList.add(move);
                        }
                    }
                }
            }

            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                var phiList = phiMoveLists.get(armBlock);
                if (fristBranch.containsKey(armBlock)) {
                    var branch = fristBranch.get(armBlock);
                    for (var move : phiList) {
                        branch.insertBeforeCO(move);
                    }
                } else {
                    for (var move : phiList) {
                        armBlock.asElementView().add(move);
                    }
                }
            }
            isResolvePhi = false;
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
        globalMove = null;
        if (checkEncodeImm(val.getValue())) {
            // 可以直接表示 直接返回一个IImm
            return new IImm(val.getValue());
        } else {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new IImm(val.getValue());
            // MOV32 VR #imm32
            globalMove = new ArmInstMove(block, vr, addr);
            return vr;
        }
    }

    private Operand resolveIImmOperand(int val, ArmBlock block, FunctionInfo funcinfo) {
        globalMove = null;
        if (checkEncodeImm(val)) {
            // 可以直接表示 直接返回一个IImm
            return new IImm(val);
        } else {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new IImm(val);
            // MOV32 VR #imm32
            globalMove = new ArmInstMove(block, vr, addr);
            return vr;
        }
    }

    private Operand resolveOffset(Operand dst, int val, ArmBlock block, FunctionInfo funcinfo) {
        globalMove = null;
        if (checkOffsetRange(dst, val)) {
            // 可以直接表示 直接返回一个IImm
            return new IImm(val);
        } else {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new IImm(val);
            // MOV32 VR #imm32
            globalMove = new ArmInstMove(block, vr, addr);
            return vr;
        }
    }

    private Operand resolveLhsIImmOperand(int val, ArmBlock block, FunctionInfo funcinfo) {
        globalMove = null;
        // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
        var vr = new IVirtualReg();
        var addr = new IImm(val);
        // MOV32 VR #imm32
        globalMove = new ArmInstMove(block, vr, addr);
        return vr;
    }

    private Operand resolveFImmOperand(FloatConst val, ArmBlock block, FunctionInfo funcinfo) {
        globalMove = null;
        // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
        var vr = new FVirtualReg();
        var addr = new FImm(val.getValue());
        // VlDR VR =imm32
        globalMove = new ArmInstMove(block, vr, addr);
        return vr;
    }

    private Operand resolveImmOperand(Constant val, ArmBlock block, FunctionInfo funcinfo) {
        globalMove = null;
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
            int fcnt = funcinfo.getFparamsCnt();
            int icnt = funcinfo.getIparamsCnt();
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).equals(val)) {
                    if (i < icnt) {
                        // MOVE VR Ri
                        // R0 - R3 在后续的基本块中会修改 因此需要在最前面的块当中就读取出来
                        // 加到最前面防止后续load修改了r0 - r3
                        var move = new ArmInstMove(vr, new IPhyReg(i));
                        funcinfo.getPrologue().asElementView().add(0, move);
                    } else if (i < icnt + fcnt) {
                        var move = new ArmInstMove(vr, new FPhyReg(i - icnt));
                        funcinfo.getPrologue().asElementView().add(0, move);
                    } else {
                        // LDR VR [SP, (i-4)*4]
                        // 寄存器分配后修改为 LDR VR [SP, (i-4)*4 + stackSize + push的大小]
                        var offset = resolveOffset(vr, (i - icnt - fcnt) * 4, funcinfo.getPrologue(), funcinfo);
                        // 保证这个MOVE语句一定在最前面
                        var load = new ArmInstLoad(funcinfo.getPrologue(), vr, new IPhyReg("sp"), offset);
                        if (globalMove != null) {
                            Log.ensure(offset.IsVirtual(), "must be a virtual reg when global move is not null");
                            load.setOffsetMove(globalMove);
                        }
                        load.setParamsLoad(true);
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
        globalMove = null;
        // 因为不是最后一个操作数, 不能直接给予一个立即数
        if (val instanceof IntConst) {
            // 因为无法直接表示 需要先MOVE到一个虚拟寄存器当中, 再返回这个虚拟寄存器
            var vr = new IVirtualReg();
            var addr = new IImm(((IntConst) val).getValue());
            // MOV32 VR #imm32
            globalMove = new ArmInstMove(block, vr, addr);
            return vr;
        } else {
            return resolveOperand(val, block, funcinfo);
        }
    }

    // Phi直接把Op返回 不用中转任何指令
    private Operand resolvePhiOperand(Value val, ArmBlock block, FunctionInfo funcinfo) {
        globalMove = null;
        if (val instanceof IntConst) {
            return new IImm(((IntConst) val).getValue());
        } else if (val instanceof FloatConst) {
            return new FImm(((FloatConst) val).getValue());
        } else {
            return resolveOperand(val, block, funcinfo);
        }
    }

    private Operand resolveGlobalVar(GlobalVar val, ArmBlock block, FunctionInfo funcinfo) {
        // 全局变量应该事先处理
        // 后续可以加一个优化, 在一个基本块内一定的虚拟寄存器序号内直接返回虚拟寄存器
        if (globalMap.containsKey(val) && !isResolvePhi) {
            var last = (IVirtualReg) globalMap.get(val);
            if (IVirtualReg.nowId() - last.getId() <= 10) {
                return last;
            }
        }
        Log.ensure(valMap.containsKey(val));
        var vr = new IVirtualReg();
        new ArmInstLoad(block, vr, valMap.get(val));
        globalMap.put(val, vr);
        return vr;
    }

    private Operand resolveOperand(Value val, ArmBlock block, FunctionInfo funcinfo) {
        globalMove = null;
        if (val instanceof Parameter && funcinfo.getParameter().contains(val)) {
            return resolveParameter((Parameter) val, block, funcinfo);
        } else if (val instanceof GlobalVar) {
            return resolveGlobalVar((GlobalVar) val, block, funcinfo);
        } else if (valMap.containsKey(val)) {
            return valMap.get(val);
        } else if (val instanceof Constant) {
            return resolveImmOperand((Constant) val, block, funcinfo);
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
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveLhsOperand(rhs, block, funcinfo);
                dstReg = resolveOperand(dst, block, funcinfo);
                // MUL inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IMul, dstReg, lhsReg, rhsReg);
                break;
            }
            case IDiv: {
                // 除法无法交换操作数
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveLhsOperand(rhs, block, funcinfo); // sdiv 不允许立即数
                dstReg = resolveOperand(dst, block, funcinfo);
                // SDIV inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IDiv, dstReg, lhsReg, rhsReg);
                break;
            }
            case IMod: {
                // x % y == x - (x / y) *y
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveLhsOperand(rhs, block, funcinfo); // 实际上rhs 也会再Ternay变成 lhs
                dstReg = resolveOperand(dst, block, funcinfo);
                var vr = new IVirtualReg();
                // SDIV VR inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IDiv, vr, lhsReg, rhsReg);
                // MLS inst VR inst.getRHS() inst.getLHS()
                // inst = inst.getLHS() - VR * inst.getRHS()
                new ArmInstTernay(block, ArmInstKind.IMulSub, dstReg, vr, rhsReg, lhsReg);
                break;
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
                break;
            }
            case FSub: {
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveOperand(rhs, block, funcinfo);
                dstReg = resolveOperand(dst, block, funcinfo);
                // VSUB.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FSub, dstReg, lhsReg, rhsReg);
                break;
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
                break;
            }
            case FDiv: {
                lhsReg = resolveLhsOperand(lhs, block, funcinfo);
                rhsReg = resolveOperand(rhs, block, funcinfo);
                dstReg = resolveOperand(dst, block, funcinfo);
                // VDIV.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FDiv, dstReg, lhsReg, rhsReg);
                break;
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
        Operand addrReg = null;
        var dstReg = resolveLhsOperand(dst, block, funcinfo);
        if (addr instanceof GlobalVar && inst.getType().isPtr()) {
            Log.ensure(valMap.containsKey(addr));
            addrReg = valMap.get(addr);
        } else {
            addrReg = resolveOperand(addr, block, funcinfo);
        }
        // LDR inst inst.getPtr()
        var load = new ArmInstLoad(block, dstReg, addrReg);
        if (globalMove != null) {
            Log.ensure(addrReg.IsVirtual(), "must be a virtual reg when global move is not null");
            load.setOffsetMove(globalMove);
        }
    }

    private void resolveStoreInst(StoreInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var addr = inst.getPtr();
        var var = inst.getVal();

        var varReg = resolveLhsOperand(var, block, funcinfo);
        var addrReg = resolveOperand(addr, block, funcinfo);
        // STR inst.getVal() inst.getPtr()
        var store = new ArmInstStore(block, varReg, addrReg);
        if (globalMove != null) {
            Log.ensure(addrReg.IsVirtual(), "must be a virtual reg when global move is not null");
            store.setOffsetMove(globalMove);
        }
    }

    private void resolveGEPInst(GEPInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var p = ((PointerIRTy) inst.getPtr().getType()).getBaseType();
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
                var imm = resolveLhsIImmOperand(length, block, funcinfo);
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
        Set<Value> argsIdx = new HashSet<>();
        List<Value> finalArg = new ArrayList<>();
        int fcnt = 0, icnt = 0;
        for (var arg : inst.getArgList()) {
            if (!arg.getType().isFloat()) {
                finalArg.add(arg);
                argsIdx.add(arg);
                icnt++;
            }
            if (icnt >= 4) {
                break;
            }
        }
        for (var arg : inst.getArgList()) {
            if (arg.getType().isFloat()) {
                finalArg.add(arg);
                argsIdx.add(arg);
                fcnt++;
            }
            if (fcnt >= 16) {
                break;
            }
        }
        for (var arg : inst.getArgList()) {
            if (argsIdx.contains(arg)) {
                continue;
            }
            finalArg.add(arg);
        }
        int offset = ((finalArg.size() - icnt - fcnt) + 1) / 2 * 8;
        for (int i = icnt + fcnt; i < finalArg.size(); i++) {
            var arg = finalArg.get(i);
            var src = resolveLhsOperand(arg, block, funcinfo);
            var offsetOp = resolveOffset(src, -offset + (i - icnt - fcnt) * 4, block, funcinfo);
            // STR inst.args.get(i) [SP, -(inst.args.size()-i)*4]
            // 越后面的参数越靠近栈顶
            var store = new ArmInstStore(block, src, new IPhyReg("sp"), offsetOp);
            store.setStack(false);
        }
        var argOp = new ArrayList<Operand>();
        for (int i = 0; i < icnt; i++) {
            var arg = finalArg.get(i);
            var src = resolveOperand(arg, block, funcinfo);
            argOp.add(src);
        }
        for (int i = icnt + fcnt - 1; i >= icnt; i--) {
            var src = resolveOperand(finalArg.get(i), block, funcinfo);
            new ArmInstMove(block, new FPhyReg(i - icnt), src);
        }
        Operand offsetOp = null;
        if (finalArg.size() > icnt + fcnt) {
            offsetOp = resolveIImmOperand(offset, block, funcinfo);
        }
        for (int i = icnt - 1; i >= 0; i--) {
            new ArmInstMove(block, new IPhyReg(i), argOp.get(i));
        }
        if (finalArg.size() > icnt + fcnt) {
            // SUB SP SP (inst.args.size() - 4) * 4
            new ArmInstBinary(block, ArmInstKind.ISub, new IPhyReg("sp"), new IPhyReg("sp"), offsetOp);
        }
        new ArmInstCall(block, funcMap.get(inst.getCallee()));
        if (finalArg.size() > icnt + fcnt) {
            // ADD SP SP (inst.args.size() - 4) * 4
            new ArmInstBinary(block, ArmInstKind.IAdd, new IPhyReg("sp"), new IPhyReg("sp"), offsetOp);
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
            if (src.getType().isFloat()) {
                // atpcs 规范
                // VMOV S0 inst.getReturnValue()
                new ArmInstMove(block, new FPhyReg("s0"), srcReg);
            } else {
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
        var dst = resolveOperand(inst, block, funcinfo);
        // ADD inst [SP, 之前已用的栈大小]
        var binary = new ArmInstBinary(block, ArmInstKind.IAdd, dst, new IPhyReg("sp"),
                new IImm(funcinfo.getStackSize()));
        binary.setStack(true);
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
            Log.ensure(false, "BrCondInst Cond Illegal");
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
        boolean isCmn = false;
        if (lhs instanceof Constant) {
            if (lhs instanceof IntConst) {
                var ic = (IntConst) lhs;
                if (checkEncodeImm(ic.getValue())) {
                    rhsReg = resolveIImmOperand(ic.getValue(), block, funcinfo);
                } else if (checkEncodeImm(-ic.getValue())) {
                    rhsReg = resolveIImmOperand(-ic.getValue(), block, funcinfo);
                    isCmn = true;
                } else {
                    rhsReg = resolveOperand(lhs, block, funcinfo);
                }
            } else {
                rhsReg = resolveOperand(lhs, block, funcinfo);
            }
            lhsReg = resolveLhsOperand(rhs, block, funcinfo);
            // 反向交换
            cond = cond.getEqualOppCondType();
        } else {
            if (rhs instanceof IntConst) {
                var ic = (IntConst) rhs;
                if (checkEncodeImm(ic.getValue())) {
                    rhsReg = resolveIImmOperand(ic.getValue(), block, funcinfo);
                } else if (checkEncodeImm(-ic.getValue())) {
                    rhsReg = resolveIImmOperand(-ic.getValue(), block, funcinfo);
                    isCmn = true;
                } else {
                    rhsReg = resolveOperand(rhs, block, funcinfo);
                }
            } else {
                rhsReg = resolveOperand(rhs, block, funcinfo);
            }
            lhsReg = resolveLhsOperand(lhs, block, funcinfo);
        }

        // CMP( VCMP.F32 ) inst.getLHS() inst.getRHS() (可能交换LHS/RHS)
        // VMRS APSR_nzcv fpscr
        var cmp = new ArmInstCmp(block, lhsReg, rhsReg, cond);
        cmp.setCmn(isCmn);
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

    private void resolveMemInitInst(MemInitInst inst, ArmBlock block, FunctionInfo funcinfo) {
        var dst = resolveOperand(inst.getArrayPtr(), block, funcinfo);
        var ac = inst.getInit();
        int size = inst.getInit().getType().getSize();
        if (ac instanceof ZeroArrayConst) {
            var imm = resolveIImmOperand(size, block, funcinfo);
            new ArmInstMove(block, new IPhyReg("r0"), dst);
            new ArmInstMove(block, new IPhyReg("r1"), new IImm(0));
            new ArmInstMove(block, new IPhyReg("r2"), imm);
            new ArmInstCall(block, "memset", 3, 0, false);
        } else {
            var src = resolveOperand(ac, block, funcinfo);
            var imm = resolveIImmOperand(size, block, funcinfo);
            new ArmInstMove(block, new IPhyReg("r0"), dst);
            new ArmInstMove(block, new IPhyReg("r1"), src);
            new ArmInstMove(block, new IPhyReg("r2"), imm);
            new ArmInstCall(block, "memcpy", 3, 0, false);
        }
    }

    // code gen arm

    public StringBuilder codeGenArm() {
        var arm = new StringBuilder();
        arm.append(".arch armv7ve\n");
        if (!globalvars.isEmpty() || !arrayConstMap.isEmpty()) {
            arm.append("\n.data\n.align 4\n\n");
        }
        Set<ArrayConst> acSet = new HashSet<>();
        for (var entry : globalvars.entrySet()) {
            var key = entry.getKey();
            var val = entry.getValue().getInit();
            if (val instanceof ZeroArrayConst) {
                arm.append("\n.bss\n.align 4\n");
            } else {
                arm.append("\n.data\n.align 4\n");
            }
            arm.append(".global\t" + key + "\n" + key + ":\n");
            if (val instanceof IntConst) {
                arm.append(codeGenIntConst((IntConst) val));
            } else if (val instanceof FloatConst) {
                arm.append(codeGenFloatConst((FloatConst) val));
            } else if (val instanceof ArrayConst) {
                acSet.add((ArrayConst) val);
                arm.append(codeGenArrayConst((ArrayConst) val));
            }
            arm.append("\n");
        }
        for (var entry : arrayConstMap.entrySet()) {
            var key = entry.getValue();
            var val = entry.getKey();
            if (acSet.contains(val)) {
                continue;
            }
            if (val instanceof ZeroArrayConst) {
                arm.append("\n.bss\n.align 4\n");
            } else {
                arm.append("\n.data\n.align 4\n");
            }
            arm.append(key + ":\n");
            arm.append(codeGenArrayConst(val));
            arm.append("\n");
        }

        arm.append("\n.text\n");
        for (var func : functions) {
            fixStack(func);
            // arm.append("\n@.global\t" + func.getName() + "\n@" + func.getName() + ":\n");
            // for (var block : func.asElementView()) {
            // arm.append("@" + block.getLabel() + ":\n");
            // if (block.getTrueSuccBlock() != null) {
            // arm.append("@trueSuccBlock: " + block.getTrueSuccBlock().getLabel());
            // if (block.getFalseSuccBlock() == null) {
            // arm.append("\n");
            // } else {
            // arm.append("\tfalseSuccBlock: " + block.getFalseSuccBlock().getLabel() +
            // "\n");
            // }
            // }
            // for (var inst : block.asElementView()) {
            // inst.InitSymbol();
            // arm.append(inst.getSymbol());
            // }
            // }

            boolean isFix = true;
            while (isFix) {
                var allocatorMap = regAllocator.run(func);
                for (var kv : allocatorMap.entrySet()) {
                    Log.ensure(kv.getKey().IsVirtual(), "allocatorMap key not Virtual");
                    ;
                    Log.ensure(kv.getValue().IsPhy(), "allocatorMap value not Phy");
                }
                Set<IPhyReg> iPhyRegs = new HashSet<>();
                Set<FPhyReg> fPhyRegs = new HashSet<>();
                for (var block : func.asElementView()) {
                    for (var inst : block.asElementView()) {
                        for (var op : inst.getOperands()) {
                            if (allocatorMap.containsKey(op)) {
                                op = allocatorMap.get(op);
                            }
                            if (op instanceof IPhyReg) {
                                iPhyRegs.add((IPhyReg) op);
                            }
                            if (op instanceof FPhyReg) {
                                fPhyRegs.add((FPhyReg) op);
                            }
                        }
                    }
                }
                calcIUseRegs(func, iPhyRegs);
                calcFUseRegs(func, fPhyRegs);
                isFix = fixStack(func);

                var funcInfo = func.getFuncInfo();
                var stackSize = funcInfo.getFinalstackSize();
                String prologuePrint = "";

                var iuse = new StringBuilder();
                var first = true;
                for (var reg : funcInfo.getiUsedRegs()) {
                    if (!first) {
                        iuse.append(", ");
                    }
                    iuse.append(reg.print());
                    first = false;
                }

                var fuse1 = new StringBuilder();
                var fuse2 = new StringBuilder();
                var fusedList = funcInfo.getfUsedRegs();
                first = true;
                for (int i = 0; i < Integer.min(fusedList.size(), 16); i++) {
                    var reg = fusedList.get(i);
                    if (!first) {
                        fuse1.append(", ");
                    }
                    fuse1.append(reg.print());
                    first = false;
                }
                first = true;
                for (int i = 16; i < fusedList.size(); i++) {
                    var reg = fusedList.get(i);
                    if (!first) {
                        fuse2.append(", ");
                    }
                    fuse2.append(reg.print());
                    first = false;
                }

                if (!funcInfo.getiUsedRegs().isEmpty()) {
                    prologuePrint += "\tpush\t{" + iuse.toString() + "}\n";
                }

                if (fuse1.length() != 0) {
                    prologuePrint += "\tvpush\t{" + fuse1.toString() + "}\n";
                }

                if (fuse2.length() != 0) {
                    prologuePrint += "\tvpush\t{" + fuse2.toString() + "}\n";
                }

                if (stackSize > 0) {
                    if (CodeGenManager.checkEncodeImm(stackSize)) {
                        prologuePrint += "\tsub\tsp,\tsp,\t#" + stackSize + "\n";
                    } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                        prologuePrint += "\tadd\tsp,\tsp,\t#" + stackSize + "\n";
                    } else {
                        var move = new ArmInstMove(new IPhyReg("r4"), new IImm(stackSize));
                        prologuePrint += move.print();
                        prologuePrint += "\tsub\tsp,\tsp,\tr4\n";
                    }
                }

                if (isFix) {
                    // arm.append("\n@.global\t" + func.getName() + "\n@" + func.getName() + ":\n");
                    // arm.append(getSymbol(prologuePrint));
                    // for (var block : func.asElementView()) {
                    // arm.append("@" + block.getLabel() + ":\n");
                    // if (block.getTrueSuccBlock() != null) {
                    // arm.append("@trueSuccBlock: " + block.getTrueSuccBlock().getLabel());
                    // if (block.getFalseSuccBlock() == null) {
                    // arm.append("\n");
                    // } else {
                    // arm.append("\tfalseSuccBlock: " + block.getFalseSuccBlock().getLabel() +
                    // "\n");
                    // }
                    // }
                    // for (var inst : block.asElementView()) {
                    // inst.InitSymbol();
                    // arm.append(inst.getSymbol());
                    // }
                    // }
                    continue;
                } else {
                    for (var block : func.asElementView()) {
                        for (var inst : block.asElementView()) {
                            inst.InitSymbol();
                            String symbol = "@";
                            for (var op : inst.getOperands()) {
                                if (op.IsVirtual()) {
                                    Log.ensure(allocatorMap.containsKey(op),
                                            "virtual reg:" + op.print() + " not exist in allocator map");
                                    inst.replaceOperand(op, allocatorMap.get(op));
                                    symbol += op.print() + ":" + allocatorMap.get(op).print() + "\t";
                                }
                            }
                            symbol += "\n";
                            if (symbol.length() > 2) {
                                inst.addSymbol(symbol);
                            }
                        }
                    }
                }

                arm.append("\n.global\t" + func.getName() + "\n" + func.getName() + ":\n");
                arm.append(prologuePrint);
                fixLtorg(func);
                for (var block : func.asElementView()) {
                    arm.append(block.getLabel() + ":\n");
                    // if (block.getTrueSuccBlock() != null) {
                    // arm.append("@trueSuccBlock: " + block.getTrueSuccBlock().getLabel());
                    // if (block.getFalseSuccBlock() == null) {
                    // arm.append("\n");
                    // } else {
                    // arm.append("\tfalseSuccBlock: " + block.getFalseSuccBlock().getLabel() +
                    // "\n");
                    // }
                    // }
                    for (var inst : block.asElementView()) {
                        arm.append(inst.print());
                        // arm.append(inst.getSymbol());
                    }
                }
            }
        }

        return arm;
    }

    private String codeGenIntConst(IntConst val) {
        return "\t" + ".word" + "\t" + val.getValue() + "\n";
    }

    private String codeGenFloatConst(FloatConst val) {
        return "\t" + ".word" + "\t" + "0x" + Integer.toHexString(Float.floatToIntBits(val.getValue())) + "\n";
    }

    private String codeGenArrayConst(ArrayConst val) {
        if (val instanceof ZeroArrayConst) {
            return "\t" + ".zero" + "\t" + val.getType().getSize() + "\n";
        }
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

    public boolean checkOffsetRange(Operand dst, int offset) {
        if (dst.IsFloat()) {
            return checkOffsetVRange(offset);
        } else {
            return checkOffsetRange(offset);
        }
    }

    public boolean checkOffsetRange(int offset) {
        return offset >= -4095 && offset <= 4095;
    }

    public boolean checkOffsetVRange(int offset) {
        return offset >= -1020 && offset <= 1020;
    }

    private boolean fixStack(ArmFunction func) {
        var funcInfo = func.getFuncInfo();
        int regCnt = funcInfo.getfUsedRegs().size() + funcInfo.getiUsedRegs().size();
        int stackSize = (funcInfo.getStackSize() + 4 * regCnt + 4) / 8 * 8 - 4 * regCnt;
        funcInfo.setFinalstackSize(stackSize);
        stackSize = 0;
        Map<Integer, Integer> stackMap = new HashMap<>();
        var stackObject = funcInfo.getStackObject();
        var stackObjectOffset = funcInfo.getStackObjectOffset();
        for (int i = stackObjectOffset.size() - 1; i >= 0; i--) {
            stackMap.put(stackObjectOffset.get(i), stackSize);
            stackSize += stackObject.get(i);
        }

        boolean isFix = false;
        stackSize = funcInfo.getFinalstackSize();
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                int actualOffset = 0;
                if (inst.isStackParamsLoad()) {
                    var load = (ArmInstLoad) inst;
                    if (load.getOffset() instanceof IImm) {
                        var addr = (IImm) load.getOffset();
                        actualOffset = addr.getImm() + stackSize + 4 * regCnt;
                    } else if (load.getOffset() instanceof IVirtualReg) {
                        var move = load.getOffsetMove();
                        Log.ensure(move != null, "offset move is null");
                        Log.ensure(move.getSrc() instanceof IImm, "fix stack prev inst src not IImm");
                        actualOffset = ((IImm) move.getSrc()).getImm() + stackSize + 4 * regCnt;
                    } else {
                        Log.ensure(false, "stack store offset is not a IImm or IVirtualReg");
                    }
                    isFix |= fixStackInst(load, actualOffset);
                } else if (inst.isStackLoad()) {
                    var load = (ArmInstLoad) inst;
                    if (load.getOffset() instanceof IImm) {
                        var offset = (IImm) load.getOffset();
                        Log.ensure(stackMap.containsKey(offset.getImm()), "stack offset not present");
                        actualOffset = stackMap.get(offset.getImm());
                    } else if (load.getOffset() instanceof IVirtualReg) {
                        var move = load.getOffsetMove();
                        Log.ensure(move != null, "offset move is null");
                        Log.ensure(move.getSrc() instanceof IImm, "fix stack prev inst src not IImm");
                        var offset = ((IImm) move.getSrc()).getImm();
                        Log.ensure(stackMap.containsKey(offset), "stack offset not present");
                        actualOffset = stackMap.get(offset);
                    } else {
                        Log.ensure(false, "stack load offset is not a IImm or IVirtualReg");
                    }
                    isFix |= fixStackInst(load, actualOffset);
                } else if (inst.isStackStore()) {
                    var store = (ArmInstStore) inst;
                    if (store.getOffset() instanceof IImm) {
                        var offset = (IImm) store.getOffset();
                        Log.ensure(stackMap.containsKey(offset.getImm()), "stack offset not present");
                        actualOffset = stackMap.get(offset.getImm());
                    } else if (store.getOffset() instanceof IVirtualReg) {
                        var move = store.getOffsetMove();
                        Log.ensure(move != null, "offset move is null");
                        Log.ensure(move.getSrc() instanceof IImm, "fix stack prev inst src not IImm");
                        var offset = ((IImm) move.getSrc()).getImm();
                        Log.ensure(stackMap.containsKey(offset), "stack offset not present");
                        actualOffset = stackMap.get(offset);
                    } else {
                        Log.ensure(false, "stack store offset is not a IImm or IVirtualReg");
                    }
                    isFix |= fixStackInst(store, actualOffset);
                } else if (inst.isStackBinary()) {
                    var binary = (ArmInstBinary) inst;
                    if (binary.getRhs() instanceof IImm) {
                        var offset = (IImm) binary.getRhs();
                        Log.ensure(stackMap.containsKey(offset.getImm()), "stack offset not present");
                        actualOffset = stackMap.get(offset.getImm());
                    } else if (binary.getRhs() instanceof IVirtualReg) {
                        var move = binary.getOffsetMove();
                        Log.ensure(move != null, "offset move is null");
                        Log.ensure(move.getSrc() instanceof IImm, "fix stack prev inst src not IImm");
                        var offset = ((IImm) move.getSrc()).getImm();
                        Log.ensure(stackMap.containsKey(offset), "stack offset not present");
                        actualOffset = stackMap.get(offset);
                    } else {
                        Log.ensure(false, "stack binary offset is not a IImm or IVirtualReg");
                    }
                    isFix |= fixStackInst(binary, actualOffset);
                }
            }
        }

        return isFix;
    }

    private boolean fixStackInst(ArmInstLoad load, int actualOffset) {
        boolean isFix = false;
        if (!checkOffsetRange(load.getDst(), actualOffset)) {
            if (!load.isFixOffset()) {
                isFix = true;
                var vr = new IVirtualReg();
                var offset = load.getOffset();
                Log.ensure(offset instanceof IImm, "load offset is not IImm");
                var move = new ArmInstMove(vr, offset);
                move.setTrueOffset(new IImm(actualOffset));
                load.setFixOffset(true);
                load.insertBeforeCO(move);
                load.setOffsetMove(move);
                load.replaceOffset(vr);
                load.setTrueOffset(null);
            } else {
                var move = load.getOffsetMove();
                Log.ensure(move != null, "offset move is null");
                move.setTrueOffset(new IImm(actualOffset));
            }
        } else {
            load.setTrueOffset(new IImm(actualOffset));
        }
        return isFix;
    }

    private boolean fixStackInst(ArmInstStore store, int actualOffset) {
        boolean isFix = false;
        if (!checkOffsetRange(store.getSrc(), actualOffset)) {
            if (!store.isFixOffset()) {
                isFix = true;
                var vr = new IVirtualReg();
                var offset = store.getOffset();
                Log.ensure(offset instanceof IImm, "store offset is not IImm");
                var move = new ArmInstMove(vr, offset);
                move.setTrueOffset(new IImm(actualOffset));
                store.setFixOffset(true);
                store.insertBeforeCO(move);
                store.setOffsetMove(move);
                store.replaceOffset(vr);
                store.setTrueOffset(null);
            } else {
                var move = store.getOffsetMove();
                Log.ensure(move != null, "offset move is null");
                move.setTrueOffset(new IImm(actualOffset));
            }
        } else {
            store.setTrueOffset(new IImm(actualOffset));
        }
        return isFix;
    }

    private boolean fixStackInst(ArmInstBinary binary, int actualOffset) {
        boolean isFix = false;
        if (!checkEncodeImm(actualOffset)) {
            if (!binary.isFixOffset()) {
                isFix = true;
                var vr = new IVirtualReg();
                var offset = binary.getRhs();
                Log.ensure(offset instanceof IImm, "stack binary rhs is not IImm");
                var move = new ArmInstMove(vr, offset);
                move.setTrueOffset(new IImm(actualOffset));
                binary.setFixOffset(true);
                binary.insertBeforeCO(move);
                binary.setOffsetMove(move);
                binary.replaceRhs(vr);
                binary.setTrueOffset(null);
            } else {
                var move = binary.getOffsetMove();
                Log.ensure(move != null, "offset move is null");
                move.setTrueOffset(new IImm(actualOffset));
            }
        } else {
            binary.setTrueOffset(new IImm(actualOffset));
        }
        return isFix;
    }

    private void calcIUseRegs(ArmFunction func, Set<IPhyReg> regs) {
        var funcInfo = func.getFuncInfo();
        var iUseRegs = funcInfo.getiUsedRegs();
        iUseRegs.clear();
        for (int i = 4; i <= 14; i++) {
            if (i == 13) {
                continue;
            }
            if (regs.contains(new IPhyReg(i))) {
                iUseRegs.add(new IPhyReg(i));
            }
        }
    }

    private void calcFUseRegs(ArmFunction func, Set<FPhyReg> regs) {
        var funcInfo = func.getFuncInfo();
        var fUseRegs = funcInfo.getfUsedRegs();
        fUseRegs.clear();
        for (int i = 16; i <= 31; i++) {
            if (regs.contains(new FPhyReg(i))) {
                fUseRegs.add(new FPhyReg(i));
            }
        }
    }

    private void fixLtorg(ArmFunction func) {
        boolean haveLoadFImm = false;
        int offset = 0;
        int cnt = 0;
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                if (inst.needLtorg()) {
                    haveLoadFImm = true;
                }
                if (inst.haveLtorg()) {
                    haveLoadFImm = false;
                    offset = 0;
                }
                if (haveLoadFImm) {
                    offset += inst.getPrintCnt();
                }
                if (offset > 250) {
                    var ltorg = new ArmInstLtorg(func.getName() + "_ltorg_" + cnt++);
                    inst.insertAfterCO(ltorg);
                    haveLoadFImm = false;
                    offset = 0;
                }
            }
        }
    }

    private String getSymbol(String symbol) {
        var sb = new StringBuffer("@" + symbol);
        int p = sb.indexOf("\n");
        while (p != sb.length() - 1 && p != -1) {
            sb.insert(p + 1, "@");
            p = sb.indexOf("\n", p + 1);
        }
        return sb.toString();
    }
}
