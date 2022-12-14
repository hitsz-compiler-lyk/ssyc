package backend.codegen;

import backend.arm.*;
import backend.arm.ArmInst.ArmCondType;
import backend.arm.ArmInst.ArmInstKind;
import backend.operand.*;
import backend.regallocator.RegAllocator;
import backend.regallocator.SimpleGraphColoring;
import ir.Module;
import ir.*;
import ir.constant.*;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.inst.*;
import ir.type.ArrayIRTy;
import ir.type.PointerIRTy;
import utils.Log;
import utils.Pair;

import java.util.*;

public class CodeGenManager {
    private List<ArmFunction> functions;
    private Map<Value, Operand> valMap;
    private Map<Function, ArmFunction> funcMap;
    private Map<BasicBlock, ArmBlock> blockMap;
    private Map<String, GlobalVar> globalvars;
    private Map<ArrayConst, String> arrayConstMap;
    private List<ArmFunction> externalFunctions;
    private Map<GlobalVar, Operand> globalMap;
    private RegAllocator regAllocator;
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

        // ??????Global??????
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
            Set<Integer> paramIdx = new HashSet<>();
            List<Parameter> finalParams = new ArrayList<>();
            int fcnt = 0, icnt = 0;
            var params = func.getParameters();
            for (int i = 0; i < params.size(); i++) {
                var param = params.get(i);
                if (!param.getType().isFloat()) {
                    finalParams.add(param);
                    paramIdx.add(i);
                    icnt++;
                }
                if (icnt >= 4) {
                    break;
                }
            }
            for (int i = 0; i < params.size(); i++) {
                var param = params.get(i);
                if (param.getType().isFloat()) {
                    finalParams.add(param);
                    paramIdx.add(i);
                    fcnt++;
                }
                if (fcnt >= 16) {
                    break;
                }
            }
            for (int i = 0; i < params.size(); i++) {
                var param = params.get(i);
                if (paramIdx.contains(i)) {
                    continue;
                }
                finalParams.add(param);
            }
            functions.add(armFunc);
            armFunc.setParameter(finalParams);
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

            // ??????????????????????????????????????????????????????
            if (func.asElementView().size() > 0) {
                var armblock = blockMap.get(func.asElementView().get(0));
                armFunc.getPrologue().setTrueSuccBlock(armblock);
                armblock.addPred(armFunc.getPrologue());
            }

            // ??????Arm?????????????????????
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

            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
                // ??????globalMap ???????????????????????????Global??????
                globalMap.clear();

                for (var inst : block.asElementView()) {
                    if (inst instanceof BinaryOpInst) {
                        resolveBinaryInst((BinaryOpInst) inst, armBlock, armFunc);
                    } else if (inst instanceof UnaryOpInst) {
                        resolveUnaryInst((UnaryOpInst) inst, armBlock, armFunc);
                    } else if (inst instanceof LoadInst) {
                        resolveLoadInst((LoadInst) inst, armBlock, armFunc);
                    } else if (inst instanceof StoreInst) {
                        resolveStoreInst((StoreInst) inst, armBlock, armFunc);
                    } else if (inst instanceof CAllocInst) {
                        resolveCAllocInst((CAllocInst) inst, armBlock, armFunc);
                    } else if (inst instanceof GEPInst) {
                        resolveGEPInst((GEPInst) inst, armBlock, armFunc);
                    } else if (inst instanceof CallInst) {
                        resolveCallInst((CallInst) inst, armBlock, armFunc);
                    } else if (inst instanceof ReturnInst) {
                        resolveReturnInst((ReturnInst) inst, armBlock, armFunc);
                    } else if (inst instanceof IntToFloatInst) {
                        resolveIntToFloatInst((IntToFloatInst) inst, armBlock, armFunc);
                    } else if (inst instanceof FloatToIntInst) {
                        resolveFloatToIntInst((FloatToIntInst) inst, armBlock, armFunc);
                    } else if (inst instanceof BrInst) {
                        resolveBrInst((BrInst) inst, armBlock, armFunc);
                    } else if (inst instanceof BrCondInst) {
                        resolveBrCondInst((BrCondInst) inst, armBlock, armFunc);
                    } else if (inst instanceof MemInitInst) {
                        resolveMemInitInst((MemInitInst) inst, armBlock, armFunc);
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

            // Phi ??????, ????????????????????????????????????????????????MOVE?????? ???incoming??????????????????Value Move?????????????????????Value
            // MOVE Phi Incoming.Value
            Map<ArmBlock, ArmInst> fristBranch = new HashMap<>();
            for (var block : func.asElementView()) {
                var armBlock = blockMap.get(block);
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
                    var phiReg = resolveOperand(phi, armBlock, armFunc);
                    var temp = phiReg.IsInt() ? new IVirtualReg() : new FVirtualReg();
                    armBlock.asElementView().add(0, new ArmInstMove(phiReg, temp));
                    while (incomingInfoIt.hasNext()) {
                        var incomingInfo = incomingInfoIt.next();
                        var src = incomingInfo.value();
                        var incomingBlock = blockMap.get(incomingInfo.block());
                        var srcReg = resolvePhiOperand(src, incomingBlock, armFunc);
                        var move = new ArmInstMove(temp, srcReg);
                        if (fristBranch.containsKey(incomingBlock)) {
                            var branch = fristBranch.get(incomingBlock);
                            branch.insertBeforeCO(move);
                        } else {
                            incomingBlock.asElementView().add(move);
                        }
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

    private Operand resolveIImmOperand(IntConst val, ArmBlock block, ArmFunction func) {
        if (checkEncodeImm(val.getValue())) {
            // ?????????????????? ??????????????????IImm
            return new IImm(val.getValue());
        } else {
            // ???????????????????????? ?????????MOVE??????????????????????????????, ??????????????????????????????
            var vr = new IVirtualReg();
            var addr = new IImm(val.getValue());
            // MOV32 VR #imm32
            var move = new ArmInstMove(block, vr, addr);
            func.getImmMap().put(vr, move);
            return vr;
        }
    }

    private Operand resolveIImmOperand(int val, ArmBlock block, ArmFunction func) {
        if (checkEncodeImm(val)) {
            // ?????????????????? ??????????????????IImm
            return new IImm(val);
        } else {
            // ???????????????????????? ?????????MOVE??????????????????????????????, ??????????????????????????????
            var vr = new IVirtualReg();
            var addr = new IImm(val);
            // MOV32 VR #imm32
            var move = new ArmInstMove(block, vr, addr);
            func.getImmMap().put(vr, move);
            return vr;
        }
    }

    private Operand resolveLhsIImmOperand(int val, ArmBlock block, ArmFunction func) {
        // ???????????????????????? ?????????MOVE??????????????????????????????, ??????????????????????????????
        var vr = new IVirtualReg();
        var addr = new IImm(val);
        // MOV32 VR #imm32
        var move = new ArmInstMove(block, vr, addr);
        func.getImmMap().put(vr, move);
        return vr;
    }

    private Operand resolveFImmOperand(FloatConst val, ArmBlock block, ArmFunction func) {
        // ???????????????????????? ?????????MOVE??????????????????????????????, ??????????????????????????????
        var vr = new FVirtualReg();
        var addr = new FImm(val.getValue());
        // VlDR VR =imm32
        var move = new ArmInstMove(block, vr, addr);
        func.getImmMap().put(vr, move);
        return vr;
    }

    private Operand resolveImmOperand(Constant val, ArmBlock block, ArmFunction func) {
        if (val instanceof IntConst) {
            return resolveIImmOperand((IntConst) val, block, func);
        } else if (val instanceof FloatConst) {
            return resolveFImmOperand((FloatConst) val, block, func);
        } else {
            Log.ensure(false, "Resolve Operand: Bool or Array Constant");
            return null;
        }
    }

    private Operand resolveParameter(Parameter val, ArmBlock block, ArmFunction func) {
        if (!valMap.containsKey(val)) {
            Operand vr = val.getParamType().isFloat() ? new FVirtualReg() : new IVirtualReg();
            var params = func.getParameter();
            int fcnt = func.getFparamsCnt();
            int icnt = func.getIparamsCnt();
            valMap.put(val, vr);
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).equals(val)) {
                    if (i < icnt) {
                        // MOVE VR Ri
                        // R0 - R3 ????????????????????????????????? ???????????????????????????????????????????????????
                        // ???????????????????????????load?????????r0 - r3
                        var move = new ArmInstMove(vr, new IPhyReg(i));
                        func.getPrologue().asElementView().add(0, move);
                    } else if (i < icnt + fcnt) {
                        var move = new ArmInstMove(vr, new FPhyReg(i - icnt));
                        func.getPrologue().asElementView().add(0, move);
                    } else {
                        // LDR VR [SP, (i-4)*4]
                        // ??????????????????????????? LDR VR [SP, (i-4)*4 + stackSize + push?????????]
                        var load = new ArmInstParamLoad(func.getPrologue(), vr, new IImm((i - icnt - fcnt) * 4));
                        func.getParamLoadMap().put(vr, load);
                    }
                    break;
                }
            }
            return vr;
        } else {
            return valMap.get(val);
        }
    }

    // ???????????????????????????
    private Operand resolveLhsOperand(Value val, ArmBlock block, ArmFunction func) {
        // ?????????????????????????????????, ?????????????????????????????????
        if (val instanceof IntConst) {
            // ???????????????????????? ?????????MOVE??????????????????????????????, ??????????????????????????????
            var vr = new IVirtualReg();
            var addr = new IImm(((IntConst) val).getValue());
            // MOV32 VR #imm32
            var move = new ArmInstMove(block, vr, addr);
            func.getImmMap().put(vr, move);
            return vr;
        } else {
            return resolveOperand(val, block, func);
        }
    }

    // Phi?????????Op?????? ????????????????????????
    private Operand resolvePhiOperand(Value val, ArmBlock block, ArmFunction func) {
        if (val instanceof IntConst) {
            return new IImm(((IntConst) val).getValue());
        } else if (val instanceof FloatConst) {
            return new FImm(((FloatConst) val).getValue());
        } else {
            return resolveOperand(val, block, func);
        }
    }

    private Operand resolveGlobalVar(GlobalVar val, ArmBlock block, ArmFunction func) {
        // ??????????????????????????????
        // ???????????????????????????, ?????????????????????????????????????????????????????????????????????????????????
        if (globalMap.containsKey(val)) {
            return globalMap.get(val);
        }
        Log.ensure(valMap.containsKey(val));
        var vr = new IVirtualReg();
        var load = new ArmInstLoad(block, vr, valMap.get(val));
        func.getAddrLoadMap().put(vr, load);
        globalMap.put(val, vr);
        return vr;
    }

    private Operand resolveOperand(Value val, ArmBlock block, ArmFunction func) {
        if (val instanceof Parameter && func.getParameter().contains(val)) {
            return resolveParameter((Parameter) val, block, func);
        } else if (val instanceof GlobalVar) {
            return resolveGlobalVar((GlobalVar) val, block, func);
        } else if (valMap.containsKey(val)) {
            return valMap.get(val);
        } else if (val instanceof Constant) {
            return resolveImmOperand((Constant) val, block, func);
        } else {
            Operand vr = val.getType().isFloat() ? new FVirtualReg() : new IVirtualReg();
            valMap.put(val, vr);
            return vr;
        }
    }

    private void resolveBinaryInst(BinaryOpInst inst, ArmBlock block, ArmFunction func) {
        var lhs = inst.getLHS();
        var rhs = inst.getRHS();
        var dst = inst;
        Operand lhsReg, rhsReg, dstReg;

        switch (inst.getKind()) {
            case IAdd: {
                // ??????????????????????????????????????? ?????? -imm??????????????? ??????????????????????????? ??????????????????MOV32
                var instKind = ArmInstKind.IAdd;
                if (lhs instanceof IntConst) {
                    int imm = ((IntConst) lhs).getValue();
                    if (checkEncodeImm(-imm)) {
                        rhsReg = resolveIImmOperand(-imm, block, func);
                        instKind = ArmInstKind.ISub;
                    } else {
                        rhsReg = resolveOperand(lhs, block, func);
                    }
                    lhsReg = resolveLhsOperand(rhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                } else {
                    if (rhs instanceof IntConst && checkEncodeImm(-((IntConst) rhs).getValue())) {
                        rhsReg = resolveIImmOperand(-((IntConst) rhs).getValue(), block, func);
                        instKind = ArmInstKind.ISub;
                    } else {
                        rhsReg = resolveOperand(rhs, block, func);
                    }
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                }
                // ADD inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, instKind, dstReg, lhsReg, rhsReg);
                break;
            }
            case ISub: {
                if (lhs instanceof IntConst) {
                    // ??????????????? ??????????????????
                    lhsReg = resolveLhsOperand(rhs, block, func);
                    rhsReg = resolveOperand(lhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                    // RSB inst inst.getRHS() inst.getLHS()
                    new ArmInstBinary(block, ArmInstKind.IRsb, dstReg, lhsReg, rhsReg);
                } else {
                    var instKind = ArmInstKind.ISub;
                    if (rhs instanceof IntConst && checkEncodeImm(-((IntConst) rhs).getValue())) {
                        rhsReg = resolveIImmOperand(-((IntConst) rhs).getValue(), block, func);
                        instKind = ArmInstKind.IAdd;
                    } else {
                        rhsReg = resolveOperand(rhs, block, func);
                    }
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                    // SUB inst inst.getLHS() inst.getRHS()
                    new ArmInstBinary(block, instKind, dstReg, lhsReg, rhsReg);
                }
                break;
            }
            case IMul: {
                dstReg = resolveOperand(dst, block, func);
                if (lhs instanceof IntConst || rhs instanceof IntConst) {
                    Operand src = null;
                    int imm = 0;
                    if (lhs instanceof IntConst && canOptimizeMul(((IntConst) lhs).getValue())) {
                        src = resolveLhsOperand(rhs, block, func);
                        imm = ((IntConst) lhs).getValue();
                    }
                    if (rhs instanceof IntConst && canOptimizeMul(((IntConst) rhs).getValue())) {
                        src = resolveLhsOperand(lhs, block, func);
                        imm = ((IntConst) rhs).getValue();
                    }
                    if (src != null) {
                        resolveConstMuL(dstReg, src, imm, block, func);
                        break;
                    }
                }
                lhsReg = resolveLhsOperand(lhs, block, func);
                rhsReg = resolveLhsOperand(rhs, block, func);
                // MUL inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.IMul, dstReg, lhsReg, rhsReg);
                break;
            }
            case IDiv: {
                // ???????????????????????????
                lhsReg = resolveLhsOperand(lhs, block, func);
                dstReg = resolveOperand(dst, block, func);
                if (rhs instanceof IntConst) {
                    var imm = ((IntConst) rhs).getValue();
                    resolveConstDiv(dstReg, lhsReg, imm, block, func);
                } else {
                    rhsReg = resolveLhsOperand(rhs, block, func); // sdiv ??????????????????
                    // SDIV inst inst.getLHS() inst.getRHS()
                    new ArmInstBinary(block, ArmInstKind.IDiv, dstReg, lhsReg, rhsReg);
                }
                break;
            }
            case IMod: {
                // x % y == x - (x / y) *y
                // % 0 % 1 % 2^n ????????????
                if (rhs instanceof IntConst) {
                    var imm = ((IntConst) rhs).getValue();
                    if (imm == 0) {
                        dstReg = resolveOperand(dst, block, func);
                        lhsReg = resolveOperand(lhs, block, func);
                        new ArmInstMove(block, dstReg, lhsReg);
                        break;
                    } else if (Math.abs(imm) == 1) {
                        dstReg = resolveOperand(dst, block, func);
                        new ArmInstMove(block, dstReg, new IImm(0));
                        break;
                    } else if (is2Power(Math.abs(imm))) {
                        int abs = Math.abs(imm);
                        int l = ctz(abs);
                        dstReg = resolveOperand(dst, block, func);
                        var src = resolveLhsOperand(lhs, block, func);
                        var vr = src;
                        var vr2 = new IVirtualReg();
                        if (abs != 2) {
                            vr = new IVirtualReg();
                            var move = new ArmInstMove(block, vr, src);
                            move.setShift(new ArmShift(ArmShift.ShiftType.Asr, 31));
                        }
                        var add = new ArmInstBinary(block, ArmInstKind.IAdd, vr2, src, vr);
                        add.setShift(new ArmShift(ArmShift.ShiftType.Lsr, 32 - l));
                        var bicImm = resolveIImmOperand(abs - 1, block, func);
                        var vr3 = new IVirtualReg();
                        new ArmInstBinary(block, ArmInstKind.Bic, vr3, vr2, bicImm);
                        new ArmInstBinary(block, ArmInstKind.ISub, dstReg, src, vr3);
                        break;
                    }
                }
                lhsReg = resolveLhsOperand(lhs, block, func);
                dstReg = resolveOperand(dst, block, func);
                var vr = new IVirtualReg();
                // SDIV VR inst.getLHS() inst.getRHS()
                if (rhs instanceof IntConst) {
                    var imm = ((IntConst) rhs).getValue();
                    resolveConstDiv(vr, lhsReg, imm, block, func);
                } else {
                    rhsReg = resolveLhsOperand(rhs, block, func); // ?????????rhs ?????????Ternay?????? lhs
                    new ArmInstBinary(block, ArmInstKind.IDiv, vr, lhsReg, rhsReg);
                }
                // MLS inst VR inst.getRHS() inst.getLHS()
                // inst = inst.getLHS() - VR * inst.getRHS()
                if (rhs instanceof IntConst && canOptimizeMul(((IntConst) rhs).getValue())) {
                    var imm = ((IntConst) rhs).getValue();
                    var vr2 = new IVirtualReg();
                    resolveConstMuL(vr2, vr, imm, block, func);
                    new ArmInstBinary(block, ArmInstKind.ISub, dstReg, lhsReg, vr2);
                } else {
                    rhsReg = resolveLhsOperand(rhs, block, func); // ?????????rhs ?????????Ternay?????? lhs
                    new ArmInstTernay(block, ArmInstKind.IMulSub, dstReg, vr, rhsReg, lhsReg);
                }
                break;
            }
            case FAdd: {
                if (lhs instanceof Constant) {
                    lhsReg = resolveLhsOperand(rhs, block, func);
                    rhsReg = resolveOperand(lhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                } else {
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    rhsReg = resolveOperand(rhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                }
                // VADD.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FAdd, dstReg, lhsReg, rhsReg);
                break;
            }
            case FSub: {
                lhsReg = resolveLhsOperand(lhs, block, func);
                rhsReg = resolveOperand(rhs, block, func);
                dstReg = resolveOperand(dst, block, func);
                // VSUB.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FSub, dstReg, lhsReg, rhsReg);
                break;
            }
            case FMul: {
                if (lhs instanceof Constant) {
                    lhsReg = resolveLhsOperand(rhs, block, func);
                    rhsReg = resolveOperand(lhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                } else {
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    rhsReg = resolveOperand(rhs, block, func);
                    dstReg = resolveOperand(dst, block, func);
                }
                // VMUL.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FMul, dstReg, lhsReg, rhsReg);
                break;
            }
            case FDiv: {
                lhsReg = resolveLhsOperand(lhs, block, func);
                rhsReg = resolveOperand(rhs, block, func);
                dstReg = resolveOperand(dst, block, func);
                // VDIV.F32 inst inst.getLHS() inst.getRHS()
                new ArmInstBinary(block, ArmInstKind.FDiv, dstReg, lhsReg, rhsReg);
                break;
            }
            default: {
                Log.ensure(false, "binary inst not implement");
            }
        }
    }

    private void resolveUnaryInst(UnaryOpInst inst, ArmBlock block, ArmFunction func) {
        var src = inst.getArg();
        var dst = inst;
        var srcReg = resolveOperand(src, block, func);
        var dstReg = resolveLhsOperand(dst, block, func);

        if (inst.getKind() == InstKind.INeg) {
            // new ArmInstBinary(block, ArmInstKind.IRsb, dstReg, srcReg, new IImm(0));
            // NEG inst inst.getArg()
            new ArmInstUnary(block, ArmInstKind.INeg, dstReg, srcReg);
        } else if (inst.getKind() == InstKind.FNeg) {
            // VNEG.F32 inst inst.getArg()
            new ArmInstUnary(block, ArmInstKind.FNeg, dstReg, srcReg);
        }
    }

    private void resolveLoadInst(LoadInst inst, ArmBlock block, ArmFunction func) {
        var addr = inst.getPtr();
        var dst = inst;
        Operand addrReg = null;
        var dstReg = resolveLhsOperand(dst, block, func);
        if (addr instanceof GlobalVar && inst.getType().isPtr()) {
            Log.ensure(valMap.containsKey(addr));
            addrReg = valMap.get(addr);
        } else {
            addrReg = resolveOperand(addr, block, func);
        }
        // LDR inst inst.getPtr()
        var load = new ArmInstLoad(block, dstReg, addrReg);
        if (addr instanceof GlobalVar && inst.getType().isPtr()) {
            func.getAddrLoadMap().put(dstReg, load);
        }
    }

    private void resolveStoreInst(StoreInst inst, ArmBlock block, ArmFunction func) {
        var addr = inst.getPtr();
        var var = inst.getVal();

        var varReg = resolveLhsOperand(var, block, func);
        var addrReg = resolveOperand(addr, block, func);
        // STR inst.getVal() inst.getPtr()
        new ArmInstStore(block, varReg, addrReg);
    }

    private void resolveGEPInst(GEPInst inst, ArmBlock block, ArmFunction func) {
        var p = ((PointerIRTy) inst.getPtr().getType()).getBaseType();
        var indices = inst.getIndices();
        ArrayList<Integer> dim = new ArrayList<>();

        while (p.isArray()) {
            dim.add(p.getSize());
            p = ((ArrayIRTy) p).getElementType();
        }
        // ?????????????????????????????? INT FLOAT???SIZE
        dim.add(p.getSize());

        // ????????????
        var arr = resolveLhsOperand(inst.getPtr(), block, func);
        var ret = resolveLhsOperand(inst, block, func);
        var tot = 0;
        for (int i = 0; i < indices.size(); i++) {
            var offset = resolveOperand(indices.get(i), block, func);
            var length = dim.get(i);

            if (offset.IsIImm()) {
                tot += ((IImm) offset).getImm() * length;
                if (i == indices.size() - 1) {
                    if (tot == 0) {
                        // MOVR inst ????????????
                        new ArmInstMove(block, ret, arr);
                    } else {
                        var imm = resolveIImmOperand(tot, block, func);
                        // ADD inst ???????????? + ?????????
                        new ArmInstBinary(block, ArmInstKind.IAdd, ret, arr, imm);
                    }
                }
            } else {
                if (tot != 0) {
                    var imm = resolveIImmOperand(tot, block, func);
                    var vr = new IVirtualReg();
                    // ADD VR ???????????? + ?????????
                    // ???????????? = VR
                    new ArmInstBinary(block, ArmInstKind.IAdd, vr, arr, imm);
                    tot = 0;
                    arr = vr;
                }
                Operand dst = ret;
                if (i != indices.size() - 1) {
                    dst = new IVirtualReg();
                }
                if (canOptimizeMul(length)) {
                    var vr = new IVirtualReg();
                    resolveConstMuL(vr, offset, length, block, func);
                    new ArmInstBinary(block, ArmInstKind.IAdd, dst, arr, vr);
                } else {
                    var imm = resolveLhsIImmOperand(length, block, func);
                    // MLA inst dim.get(i) indices.get(i) ????????????
                    // inst = dim.get(i)*indices.get(i) + ????????????
                    new ArmInstTernay(block, ArmInstKind.IMulAdd, dst, offset, imm, arr);
                }

                if (i != indices.size() - 1) {
                    arr = dst;
                }
            }
        }
    }

    private void resolveCallInst(CallInst inst, ArmBlock block, ArmFunction func) {
        Set<Integer> argsIdx = new HashSet<>();
        List<Value> finalArg = new ArrayList<>();
        int fcnt = 0, icnt = 0;
        var args = inst.getArgList();
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (!arg.getType().isFloat()) {
                finalArg.add(arg);
                argsIdx.add(i);
                icnt++;
            }
            if (icnt >= 4) {
                break;
            }
        }
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (arg.getType().isFloat()) {
                finalArg.add(arg);
                argsIdx.add(i);
                fcnt++;
            }
            if (fcnt >= 16) {
                break;
            }
        }
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (argsIdx.contains(i)) {
                continue;
            }
            finalArg.add(arg);
        }
        int offset = ((finalArg.size() - icnt - fcnt) + 1) / 2 * 8;
        Set<Pair<Operand, ArmInstStackAddr>> stackAddrSet = new HashSet<>();
        for (int i = finalArg.size() - 1; i >= icnt + fcnt; i--) {
            var arg = finalArg.get(i);
            var src = resolveLhsOperand(arg, block, func);
            int nowOffset = -offset + (i - icnt - fcnt) * 4;
            int finalOffset = nowOffset;
            // STR inst.args.get(i) [SP, -(inst.args.size()-i)*4]
            // ?????????????????????????????????
            Operand addr = new IPhyReg("sp");
            if (!checkOffsetRange(nowOffset, src)) {
                for (var entry : stackAddrSet) {
                    var op = entry.key;
                    var instOffset = entry.value.getIntOffset();
                    if (checkOffsetRange(nowOffset - instOffset, src)) {
                        addr = op;
                        finalOffset = nowOffset - instOffset;
                        break;
                    }
                }
                if (addr.equals(new IPhyReg("sp"))) {
                    int instOffset = nowOffset / 1024 * 1024;// ?????? ???????????????????????????
                    var vr = new IVirtualReg();
                    var stackAddr = new ArmInstStackAddr(block, vr, new IImm(instOffset));
                    stackAddr.setFix(true);
                    addr = vr;
                    finalOffset = nowOffset - instOffset;
                    Log.ensure(checkOffsetRange(finalOffset, src), "chang offset is illegal");
                    stackAddrSet.add(new Pair<>(vr, stackAddr));
                    func.getStackAddrMap().put(vr, stackAddr);
                }
            }
            new ArmInstStore(block, src, addr, new IImm(finalOffset));
        }
        var argOp = new ArrayList<Operand>();
        for (int i = 0; i < icnt; i++) {
            var arg = finalArg.get(i);
            var src = resolveOperand(arg, block, func);
            argOp.add(src);
        }
        for (int i = icnt + fcnt - 1; i >= icnt; i--) {
            var src = resolveOperand(finalArg.get(i), block, func);
            new ArmInstMove(block, new FPhyReg(i - icnt), src);
        }
        Operand offsetOp = null;
        if (finalArg.size() > icnt + fcnt) {
            offsetOp = resolveIImmOperand(offset, block, func);
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
            var dst = resolveLhsOperand(inst, block, func);
            if (inst.getType().isFloat()) {
                // ?????????????????????????????? ????????????s0???????????????
                // VMOV inst S0
                // atpcs ??????
                new ArmInstMove(block, dst, new FPhyReg("s0"));
            } else if (inst.getType().isInt()) {
                // ?????????r0???????????????
                // MOV inst R0
                new ArmInstMove(block, dst, new IPhyReg("r0"));
            }
        }
    }

    private void resolveReturnInst(ReturnInst inst, ArmBlock block, ArmFunction func) {
        // ????????????????????????
        if (inst.getReturnValue().isPresent()) {
            var src = inst.getReturnValue().get();
            var srcReg = resolveOperand(src, block, func);
            if (src.getType().isFloat()) {
                // atpcs ??????
                // VMOV S0 inst.getReturnValue()
                new ArmInstMove(block, new FPhyReg("s0"), srcReg);
            } else {
                // VMOV R0 inst.getReturnValue()
                new ArmInstMove(block, new IPhyReg("r0"), srcReg);
            }

        }
        new ArmInstReturn(block);
    }

    // ?????????????????????????????? ???????????? vcvt
    private void resolveIntToFloatInst(IntToFloatInst inst, ArmBlock block, ArmFunction func) {
        if (inst.getFrom() instanceof BoolToIntInst) {
            resolveBoolToIntInst((BoolToIntInst) inst.getFrom(), block, func);
        }
        var vr = new FVirtualReg();
        var src = resolveOperand(inst.getFrom(), block, func);
        var dst = resolveOperand(inst, block, func);
        // VMOV VR inst.getFrom()
        new ArmInstMove(block, vr, src);
        // VCVT.F32.S32 inst VR
        new ArmInstIntToFloat(block, dst, vr);
    }

    // ????????? vcvt ???????????????????????????
    private void resolveFloatToIntInst(FloatToIntInst inst, ArmBlock block, ArmFunction func) {
        var vr = new FVirtualReg();
        var src = resolveOperand(inst.getFrom(), block, func);
        var dst = resolveOperand(inst, block, func);
        // VCVT.F32.S32 inst VR
        new ArmInstFloatToInt(block, vr, src);
        // VMOV VR inst.getFrom()
        new ArmInstMove(block, dst, vr);
    }

    private void resolveCAllocInst(CAllocInst inst, ArmBlock block, ArmFunction func) {
        var dst = resolveOperand(inst, block, func);
        // ADD inst [SP, ????????????????????????]
        var alloc = new ArmInstStackAddr(block, dst, new IImm(func.getStackSize()));
        alloc.setCAlloc(true);
        func.getStackAddrMap().put(dst, alloc);
        // ???????????????
        func.addStackSize(inst.getAllocSize());
    }

    private void resolveBrInst(BrInst inst, ArmBlock block, ArmFunction func) {
        // B inst.getNextBB()
        new ArmInstBranch(block, blockMap.get(inst.getNextBB()));
    }

    private void resolveBrCondInst(BrCondInst inst, ArmBlock block, ArmFunction func) {
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
            // ??????????????????????????????????????????????????????????????????
            var Armcond = resolveCmpInst((CmpInst) cond, block, func);
            // B.{cond} inst.getTrueBB()
            new ArmInstBranch(block, blockMap.get(inst.getTrueBB()), Armcond);
            // B inst.getFalseBB()
            // ???????????????CodeGen?????????????????? ????????????????????????????????????
            new ArmInstBranch(block, blockMap.get(inst.getFalseBB()));
        } else {
            Log.ensure(false, "BrCondInst Cond Illegal");
        }
    }

    private ArmCondType resolveCmpInst(CmpInst inst, ArmBlock block, ArmFunction func) {
        var lhs = inst.getLHS();
        var rhs = inst.getRHS();
        var cond = condMap.get(inst.getKind());

        for (var ch : Arrays.asList(lhs, rhs)) {
            if (ch instanceof BoolToIntInst) {
                resolveBoolToIntInst((BoolToIntInst) ch, block, func);
            }
            // if(ch instanceof IntToFloatInst){
            // resolveIntToFloatInst((IntToFloatInst) ch, block, func);
            // }
        }

        Operand lhsReg, rhsReg;
        boolean isCmn = false;
        if (lhs instanceof Constant) {
            if (lhs instanceof IntConst) {
                var ic = (IntConst) lhs;
                if (checkEncodeImm(ic.getValue())) {
                    rhsReg = resolveIImmOperand(ic.getValue(), block, func);
                } else if (checkEncodeImm(-ic.getValue())) {
                    rhsReg = resolveIImmOperand(-ic.getValue(), block, func);
                    isCmn = true;
                } else {
                    rhsReg = resolveOperand(lhs, block, func);
                }
            } else {
                rhsReg = resolveOperand(lhs, block, func);
            }
            lhsReg = resolveLhsOperand(rhs, block, func);
            // ????????????
            cond = cond.getEqualOppCondType();
        } else {
            if (rhs instanceof IntConst) {
                var ic = (IntConst) rhs;
                if (checkEncodeImm(ic.getValue())) {
                    rhsReg = resolveIImmOperand(ic.getValue(), block, func);
                } else if (checkEncodeImm(-ic.getValue())) {
                    rhsReg = resolveIImmOperand(-ic.getValue(), block, func);
                    isCmn = true;
                } else {
                    rhsReg = resolveOperand(rhs, block, func);
                }
            } else {
                rhsReg = resolveOperand(rhs, block, func);
            }
            lhsReg = resolveLhsOperand(lhs, block, func);
        }

        // CMP( VCMP.F32 ) inst.getLHS() inst.getRHS() (????????????LHS/RHS)
        // VMRS APSR_nzcv fpscr
        var cmp = new ArmInstCmp(block, lhsReg, rhsReg, cond);
        cmp.setCmn(isCmn);
        return cond;
    }

    private void resolveBoolToIntInst(BoolToIntInst inst, ArmBlock block, ArmFunction func) {
        var src = inst.getFrom();
        var dstReg = resolveOperand(inst, block, func);
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
            var cond = resolveCmpInst((CmpInst) src, block, func);
            // MOV.{cond} inst #1
            new ArmInstMove(block, dstReg, new IImm(1), cond);
            // MOV.{OppCond} inst #0
            new ArmInstMove(block, dstReg, new IImm(0), cond.getOppCondType());
        } else {
            Log.ensure(false);
        }
    }

    private void resolveMemInitInst(MemInitInst inst, ArmBlock block, ArmFunction func) {
        var dst = resolveOperand(inst.getArrayPtr(), block, func);
        var ac = inst.getInit();
        int size = inst.getInit().getType().getSize();
        if (ac instanceof ZeroArrayConst) {
            var imm = resolveIImmOperand(size, block, func);
            new ArmInstMove(block, new IPhyReg("r0"), dst);
            new ArmInstMove(block, new IPhyReg("r1"), new IImm(0));
            new ArmInstMove(block, new IPhyReg("r2"), imm);
            new ArmInstCall(block, "memset", 3, 0, false);
        } else {
            var src = resolveOperand(ac, block, func);
            var imm = resolveIImmOperand(size, block, func);
            new ArmInstMove(block, new IPhyReg("r0"), dst);
            new ArmInstMove(block, new IPhyReg("r1"), src);
            new ArmInstMove(block, new IPhyReg("r2"), imm);
            new ArmInstCall(block, "memcpy", 3, 0, false);
        }
    }

    public void regAllocate() {
        for (var func : functions) {
            fixStack(func);
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
                isFix = recoverRegAllocate(func);
                isFix |= fixStack(func);

                if (isFix) {
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
            }
        }
    }

    // code gen arm

    public StringBuilder codeGenArm() {
        var arm = new StringBuilder();
        arm.append(".arch armv7ve\n");
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
            var stackSize = func.getFinalstackSize();
            String prologuePrint = "";

            var iuse = new StringBuilder();
            var first = true;
            for (var reg : func.getiUsedRegs()) {
                if (!first) {
                    iuse.append(", ");
                }
                iuse.append(reg.print());
                first = false;
            }

            var fuse1 = new StringBuilder();
            var fuse2 = new StringBuilder();
            var fusedList = func.getfUsedRegs();
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

            if (!func.getiUsedRegs().isEmpty()) {
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
            arm.append("\n.global\t" + func.getName() + "\n" + func.getName() + ":\n");
            arm.append(prologuePrint);
            fixLtorg(func);
            for (var block : func.asElementView()) {
                arm.append(block.getLabel() + ":\n");
                for (var inst : block.asElementView()) {
                    arm.append(inst.print());
                    // if (inst.getSymbol() == null) {
                    // inst.InitSymbol();
                    // }
                    // arm.append(inst.getSymbol());
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
                sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
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

    public static boolean checkOffsetRange(int offset, Operand dst) {
        if (dst.IsFloat()) {
            return checkOffsetVRange(offset);
        } else {
            return checkOffsetRange(offset);
        }
    }

    public static boolean checkOffsetRange(int offset, Boolean isFloat) {
        if (isFloat) {
            return checkOffsetVRange(offset);
        } else {
            return checkOffsetRange(offset);
        }
    }

    public static boolean checkOffsetRange(int offset) {
        return offset >= -4095 && offset <= 4095;
    }

    public static boolean checkOffsetVRange(int offset) {
        return offset >= -1020 && offset <= 1020;
    }

    private boolean fixStack(ArmFunction func) {
        boolean isFix = false;
        int regCnt = func.getfUsedRegs().size() + func.getiUsedRegs().size();
        int stackSize = (func.getStackSize() + 4 * regCnt + 4) / 8 * 8 - 4 * regCnt;
        func.setFinalstackSize(stackSize);
        stackSize = stackSize - func.getStackSize();
        Map<Integer, Integer> stackMap = new HashMap<>();
        var stackObject = func.getStackObject();
        var stackObjectOffset = func.getStackObjectOffset();
        for (int i = stackObjectOffset.size() - 1; i >= 0; i--) {
            stackMap.put(stackObjectOffset.get(i), stackSize);
            stackSize += stackObject.get(i);
        }
        Map<Integer, Operand> stackAddrMap = new HashMap<>();
        Map<Operand, Integer> addrStackMap = new HashMap<>();
        Log.ensure(stackSize == func.getFinalstackSize(), "stack size error");
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                if (inst instanceof ArmInstStackAddr) {
                    var stackAddr = (ArmInstStackAddr) inst;
                    if (stackAddr.isCAlloc()) {
                        Log.ensure(stackMap.containsKey(stackAddr.getOffset().getImm()), "stack offset not present");
                        stackAddr.setTrueOffset(new IImm(stackMap.get(stackAddr.getOffset().getImm())));
                    } else if (stackAddr.isFix()) {
                        continue;
                    } else {
                        int oldTrueOffset = stackSize - stackAddr.getOffset().getImm();
                        int nowTrueOffset = (oldTrueOffset + 1023) / 1024 * 1024;
                        stackAddr.replaceOffset(new IImm(stackSize - nowTrueOffset));
                        stackAddr.setTrueOffset(new IImm(nowTrueOffset));
                        stackAddrMap.put(nowTrueOffset, stackAddr.getDst());
                        addrStackMap.put(stackAddr.getDst(), nowTrueOffset);
                    }
                } else if (inst instanceof ArmInstParamLoad) {
                    var paramLoad = (ArmInstParamLoad) inst;
                    int trueOffset = paramLoad.getOffset().getImm() + stackSize + 4 * regCnt;
                    if (!checkOffsetRange(trueOffset, paramLoad.getDst())) {
                        if (paramLoad.getAddr().equals(new IPhyReg("sp"))) {
                            isFix = true;
                            for (var entry : stackAddrMap.entrySet()) {
                                var offset = entry.getKey();
                                var op = entry.getValue();
                                if (offset <= trueOffset && checkOffsetRange(trueOffset - offset, paramLoad.getDst())) {
                                    paramLoad.replaceAddr(op);
                                    paramLoad.setTrueOffset(new IImm(trueOffset - offset));
                                    func.getSpillNodes().remove(op);
                                    // ??????????????????????????????????????????
                                    break;
                                }
                            }
                            if (paramLoad.getAddr().equals(new IPhyReg("sp"))) {
                                int addrTrueOffset = trueOffset / 1024 * 1024;
                                var vr = new IVirtualReg();
                                int instOffset = stackSize - addrTrueOffset;
                                var stackAddr = new ArmInstStackAddr(vr, new IImm(instOffset));
                                stackAddr.setTrueOffset(new IImm(addrTrueOffset));
                                paramLoad.insertBeforeCO(stackAddr);
                                paramLoad.replaceAddr(vr);
                                Log.ensure(trueOffset >= addrTrueOffset, "chang offset is illegal");
                                Log.ensure(checkOffsetRange(trueOffset - addrTrueOffset, paramLoad.getDst()),
                                        "chang offset is illegal");
                                paramLoad.setTrueOffset(new IImm(trueOffset - addrTrueOffset));
                                stackAddrMap.put(addrTrueOffset, vr);
                                addrStackMap.put(vr, addrTrueOffset);
                                func.getStackAddrMap().put(vr, stackAddr);
                            }
                        } else {
                            var addrTrueOffset = addrStackMap.get(paramLoad.getAddr());
                            var nowTrueOffset = trueOffset - addrTrueOffset;
                            Log.ensure(checkOffsetRange(nowTrueOffset, paramLoad.getDst()), "chang offset is illegal");
                            paramLoad.setTrueOffset(new IImm(nowTrueOffset));
                        }
                    } else {
                        paramLoad.setTrueOffset(new IImm(trueOffset));
                    }
                } else if (inst instanceof ArmInstStackLoad) {
                    var stackLoad = (ArmInstStackLoad) inst;
                    Log.ensure(stackMap.containsKey(stackLoad.getOffset().getImm()), "stack offset not present");
                    int trueOffset = stackMap.get(stackLoad.getOffset().getImm());
                    if (!checkOffsetRange(trueOffset, stackLoad.getDst())) {
                        if (stackLoad.getAddr().equals(new IPhyReg("sp"))) {
                            isFix = true;
                            for (var entry : stackAddrMap.entrySet()) {
                                var offset = entry.getKey();
                                var op = entry.getValue();
                                if (offset <= trueOffset && checkOffsetRange(trueOffset - offset, stackLoad.getDst())) {
                                    stackLoad.replaceAddr(op);
                                    stackLoad.setTrueOffset(new IImm(trueOffset - offset));
                                    func.getSpillNodes().remove(op);
                                    // ??????????????????????????????????????????
                                    break;
                                }
                            }
                            if (stackLoad.getAddr().equals(new IPhyReg("sp"))) {
                                int addrTrueOffset = trueOffset / 1024 * 1024;
                                var vr = new IVirtualReg();
                                int instOffset = stackSize - addrTrueOffset;
                                var stackAddr = new ArmInstStackAddr(vr, new IImm(instOffset));
                                stackAddr.setTrueOffset(new IImm(addrTrueOffset));
                                stackLoad.insertBeforeCO(stackAddr);
                                stackLoad.replaceAddr(vr);
                                Log.ensure(trueOffset >= addrTrueOffset, "chang offset is illegal");
                                Log.ensure(checkOffsetRange(trueOffset - addrTrueOffset, stackLoad.getDst()),
                                        "chang offset is illegal");
                                stackLoad.setTrueOffset(new IImm(trueOffset - addrTrueOffset));
                                stackAddrMap.put(addrTrueOffset, vr);
                                addrStackMap.put(vr, addrTrueOffset);
                                func.getStackAddrMap().put(vr, stackAddr);
                            }
                        } else {
                            var addrTrueOffset = addrStackMap.get(stackLoad.getAddr());
                            var nowTrueOffset = trueOffset - addrTrueOffset;
                            Log.ensure(checkOffsetRange(nowTrueOffset, stackLoad.getDst()), "chang offset is illegal");
                            stackLoad.setTrueOffset(new IImm(nowTrueOffset));
                        }
                    } else {
                        stackLoad.setTrueOffset(new IImm(trueOffset));
                    }
                } else if (inst instanceof ArmInstStackStore) {
                    var stackStore = (ArmInstStackStore) inst;
                    Log.ensure(stackMap.containsKey(stackStore.getOffset().getImm()), "stack offset not present");
                    int trueOffset = stackMap.get(stackStore.getOffset().getImm());
                    if (!checkOffsetRange(trueOffset, stackStore.getDst())) {
                        if (stackStore.getAddr().equals(new IPhyReg("sp"))) {
                            isFix = true;
                            for (var entry : stackAddrMap.entrySet()) {
                                var offset = entry.getKey();
                                var op = entry.getValue();
                                if (offset <= trueOffset
                                        && checkOffsetRange(trueOffset - offset, stackStore.getDst())) {
                                    stackStore.replaceAddr(op);
                                    stackStore.setTrueOffset(new IImm(trueOffset - offset));
                                    func.getSpillNodes().remove(op);
                                    // ??????????????????????????????????????????
                                    break;
                                }
                            }
                            if (stackStore.getAddr().equals(new IPhyReg("sp"))) {
                                int addrTrueOffset = trueOffset / 1024 * 1024;
                                var vr = new IVirtualReg();
                                int instOffset = stackSize - addrTrueOffset;
                                var stackAddr = new ArmInstStackAddr(vr, new IImm(instOffset));
                                stackAddr.setTrueOffset(new IImm(addrTrueOffset));
                                stackStore.insertBeforeCO(stackAddr);
                                stackStore.replaceAddr(vr);
                                Log.ensure(trueOffset >= addrTrueOffset, "chang offset is illegal");
                                Log.ensure(checkOffsetRange(trueOffset - addrTrueOffset, stackStore.getDst()),
                                        "chang offset is illegal");
                                stackStore.setTrueOffset(new IImm(trueOffset - addrTrueOffset));
                                stackAddrMap.put(addrTrueOffset, vr);
                                addrStackMap.put(vr, addrTrueOffset);
                                func.getStackAddrMap().put(vr, stackAddr);
                            }
                        } else {
                            var addrTrueOffset = addrStackMap.get(stackStore.getAddr());
                            var nowTrueOffset = trueOffset - addrTrueOffset;
                            Log.ensure(checkOffsetRange(nowTrueOffset, stackStore.getDst()), "chang offset is illegal");
                            stackStore.setTrueOffset(new IImm(nowTrueOffset));
                        }
                    } else {
                        stackStore.setTrueOffset(new IImm(trueOffset));
                    }
                }
            }
        }
        return isFix;
    }

    private boolean recoverRegAllocate(ArmFunction func) {
        boolean isFix = false;
        Map<Operand, Operand> recoverMap = new HashMap<>();
        for (var block : func.asElementView()) {
            Map<Addr, Operand> addrMap = new HashMap<>();
            Map<IImm, Operand> offsetMap = new HashMap<>();
            Map<IImm, Operand> paramMap = new HashMap<>();
            Map<IImm, Operand> stackLoadMap = new HashMap<>();
            Map<Imm, Operand> immMap = new HashMap<>();
            var haveRecoverAddrs = block.getHaveRecoverAddrs();
            var haveRecoverOffset = block.getHaveRecoverOffset();
            var haveRecoveLoadParam = block.getHaveRecoveLoadParam();
            var haveRecoveImm = block.getHaveRecoveImm();
            var haveRecoveStackLoad = block.getHaveRecoveStackLoad();
            for (var inst : block.asElementView()) {
                if (inst instanceof ArmInstStackAddr) {
                    var stackAddr = (ArmInstStackAddr) inst;
                    var offset = stackAddr.getOffset();
                    if (!stackAddr.getDst().IsVirtual()) {
                        continue;
                    }
                    if (offsetMap.containsKey(offset)) {
                        recoverMap.put(stackAddr.getDst(), offsetMap.get(offset));
                        stackAddr.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoverOffset.contains(offset)) {
                        haveRecoverOffset.add(offset);
                        offsetMap.put(offset, stackAddr.getDst());
                        func.getSpillNodes().remove(stackAddr.getDst());
                    }
                }
                if (inst instanceof ArmInstLoad) {
                    var load = (ArmInstLoad) inst;
                    if (!load.getAddr().IsAddr()) {
                        continue;
                    }
                    if (!load.getDst().IsVirtual()) {
                        continue;
                    }
                    var addr = (Addr) load.getAddr();
                    if (addrMap.containsKey(addr)) {
                        recoverMap.put(load.getDst(), addrMap.get(addr));
                        load.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoverAddrs.contains(addr)) {
                        haveRecoverAddrs.add(addr);
                        addrMap.put(addr, load.getDst());
                        func.getSpillNodes().remove(load.getDst());
                    }
                }
                if (inst instanceof ArmInstParamLoad) {
                    var load = (ArmInstParamLoad) inst;
                    if (!load.getAddr().equals(new IPhyReg("sp"))) {
                        continue;
                    }
                    if (!load.getDst().IsVirtual()) {
                        continue;
                    }
                    var offset = load.getOffset();
                    if (paramMap.containsKey(offset)) {
                        recoverMap.put(load.getDst(), paramMap.get(offset));
                        load.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoveLoadParam.contains(offset)) {
                        haveRecoveLoadParam.add(offset);
                        paramMap.put(offset, load.getDst());
                        func.getSpillNodes().remove(load.getDst());
                    }
                }
                if (inst instanceof ArmInstMove) {
                    var move = (ArmInstMove) inst;
                    if (!move.getDst().IsVirtual()) {
                        continue;
                    }
                    if (!func.getImmMap().containsKey(move.getDst())) {
                        continue;
                    }
                    var imm = (Imm) move.getSrc();
                    if (immMap.containsKey(imm)) {
                        recoverMap.put(move.getDst(), immMap.get(imm));
                        move.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoveImm.contains(imm)) {
                        haveRecoveImm.add(imm);
                        immMap.put(imm, move.getDst());
                        func.getSpillNodes().remove(move.getDst());
                    }
                }
                if (inst instanceof ArmInstStackLoad) {
                    var load = (ArmInstStackLoad) inst;
                    if (!load.getAddr().equals(new IPhyReg("sp"))) {
                        continue;
                    }
                    if (!load.getDst().IsVirtual()) {
                        continue;
                    }
                    var offset = load.getOffset();
                    if (stackLoadMap.containsKey(offset)) {
                        recoverMap.put(load.getDst(), stackLoadMap.get(offset));
                        load.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoveStackLoad.contains(offset)) {
                        haveRecoveStackLoad.add(offset);
                        stackLoadMap.put(offset, load.getDst());
                        func.getSpillNodes().remove(load.getDst());
                    }
                }
                if (inst instanceof ArmInstStackStore) {
                    var store = (ArmInstStackStore) inst;
                    if (!store.getAddr().equals(new IPhyReg("sp"))) {
                        continue;
                    }
                    if (!store.getDst().IsVirtual()) {
                        continue;
                    }
                    var offset = store.getOffset();
                    if (!haveRecoveStackLoad.contains(offset)) {
                        haveRecoveStackLoad.add(offset);
                        stackLoadMap.put(offset, store.getDst());
                        func.getSpillNodes().remove(store.getDst());
                        func.getStackLoadMap().put(store.getDst(), new ArmInstStackLoad(store.getDst(), offset));
                    }
                }
            }
        }
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                var ops = new ArrayList<>(inst.getOperands());
                for (var op : ops) {
                    if (recoverMap.containsKey(op)) {
                        inst.replaceOperand(op, recoverMap.get(op));
                    }
                }
            }
        }
        return isFix;
    }

    private void calcIUseRegs(ArmFunction func, Set<IPhyReg> regs) {
        var iUseRegs = func.getiUsedRegs();
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
        var fUseRegs = func.getfUsedRegs();
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
                    ltorg.InitSymbol();
                    inst.insertAfterCO(ltorg);
                    haveLoadFImm = false;
                    offset = 0;
                }
            }
        }
    }

    private boolean is2Power(int val) {
        return (val & (val - 1)) == 0;
    }

    private boolean is2Power(long val) {
        return (val & (val - 1)) == 0;
    }

    private int ctz(int val) {
        int ret = 0;
        while (val != 0) {
            val >>>= 1;
            ret++;
            if ((val & 1) == 1) {
                return ret;
            }
        }
        return ret;
    }

    private void resolveConstDiv(Operand dst, Operand src, int imm, ArmBlock block, ArmFunction func) {
        int abs = Math.abs(imm);
        var dst2 = dst;
        if (imm < 0) {
            dst2 = new IVirtualReg();
        }
        if (abs == 1) {
            if (imm > 0) {
                new ArmInstMove(block, dst, src);
            } else {
                new ArmInstUnary(block, ArmInstKind.INeg, dst, src);
            }
            return;
        } else if (is2Power(abs)) {
            int l = ctz(abs);
            var vr = src;
            var vr2 = new IVirtualReg();
            if (abs != 2) {
                vr = new IVirtualReg();
                var move = new ArmInstMove(block, vr, src);
                move.setShift(new ArmShift(ArmShift.ShiftType.Asr, 31));
            }
            var add = new ArmInstBinary(block, ArmInstKind.IAdd, vr2, src, vr);
            add.setShift(new ArmShift(ArmShift.ShiftType.Lsr, 32 - l));
            var move = new ArmInstMove(block, dst2, vr2);
            move.setShift(new ArmShift(ArmShift.ShiftType.Asr, l));
        } else {
            long up = (1L << 31) - ((1L << 31) % abs) - 1;
            int p = 32;
            while ((1L << p) <= up * (abs - (1L << p) % abs)) {
                p++;
            }
            long m = (((1L << p) + (long) abs - (1L << p) % abs) / (long) abs);
            int n = (int) ((m << 32) >>> 32);
            int l = p - 32;
            var vn = resolveLhsIImmOperand(n, block, func);
            var vr = new IVirtualReg();
            if (m >= 2147483648L) {
                new ArmInstTernay(block, ArmInstKind.ILMulAdd, vr, src, vn, src);
            } else {
                new ArmInstBinary(block, ArmInstKind.ILMul, vr, src, vn);
            }
            var vr2 = new IVirtualReg();
            var move = new ArmInstMove(block, vr2, vr);
            move.setShift(new ArmShift(ArmShift.ShiftType.Asr, l));
            var add = new ArmInstBinary(block, ArmInstKind.IAdd, dst2, vr2, src);
            add.setShift(new ArmShift(ArmShift.ShiftType.Lsr, 31));
        }
        if (imm < 0) {
            new ArmInstUnary(block, ArmInstKind.INeg, dst, dst2);
        }
    }

    private boolean canOptimizeMul(int n) {
        long abs = (long) Math.abs(n);
        if (is2Power(abs)) {
            return true;
        }
        for (long i = 1; i <= abs; i <<= 1) {
            if (is2Power(abs + i) && abs + i <= 2147483647L) {
                return true;
            }
            if (is2Power(abs - i)) {
                return true;
            }
        }
        return false;
    }

    private void resolveConstMuL(Operand dst, Operand src, int imm, ArmBlock block, ArmFunction func) {
        Log.ensure(canOptimizeMul(imm), "optimize mul failde");
        int abs = Math.abs(imm);
        int l = ctz(abs);
        if (abs == 0) {
            new ArmInstMove(block, dst, new IImm(0));
        } else if (abs == 1) {
            if (imm > 0) {
                new ArmInstMove(block, dst, src);
            } else {
                new ArmInstUnary(block, ArmInstKind.INeg, dst, src);
            }
        } else if (is2Power(abs)) {
            if (imm > 0) {
                var move = new ArmInstMove(block, dst, src);
                move.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            } else {
                var vr = new IVirtualReg();
                new ArmInstMove(block, vr, new IImm(0));
                var sub = new ArmInstBinary(block, ArmInstKind.ISub, dst, vr, src);
                sub.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            }
        } else if (is2Power(abs - 1)) {
            l = ctz(abs - 1);
            var dst2 = dst;
            if (imm < 0) {
                dst2 = new IVirtualReg();
            }
            var add = new ArmInstBinary(block, ArmInstKind.IAdd, dst2, src, src);
            add.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            if (imm < 0) {
                new ArmInstUnary(block, ArmInstKind.INeg, dst, dst2);
            }
        } else if (is2Power(abs + 1)) {
            l = ctz(abs + 1);
            if (imm > 0) {
                var rsb = new ArmInstBinary(block, ArmInstKind.IRsb, dst, src, src);
                rsb.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            } else {
                var sub = new ArmInstBinary(block, ArmInstKind.ISub, dst, src, src);
                sub.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            }
        } else {
            int p = 0, nowAbs = 0;
            boolean IsAdd = false;
            for (; (1 << p) <= abs; p++) {
                if (is2Power(abs + (1 << p))) {
                    IsAdd = true;
                    nowAbs = abs + (1 << p);
                    break;
                }
                if (is2Power(abs - (1 << p))) {
                    IsAdd = false;
                    nowAbs = abs - (1 << p);
                    break;
                }
            }
            l = ctz(nowAbs);
            var vr = new IVirtualReg();
            if (IsAdd) {
                if (imm > 0) {
                    var rsb = new ArmInstBinary(block, ArmInstKind.IRsb, vr, src, src);
                    rsb.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l - p));
                } else {
                    var sub = new ArmInstBinary(block, ArmInstKind.ISub, vr, src, src);
                    sub.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l - p));
                }
                var mov = new ArmInstMove(block, dst, vr);
                mov.setShift(new ArmShift(ArmShift.ShiftType.Lsl, p));
            } else {
                var dst2 = dst;
                if (imm < 0) {
                    dst2 = new IVirtualReg();
                }
                var add = new ArmInstBinary(block, ArmInstKind.IAdd, vr, src, src);
                add.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l - p));
                var mov = new ArmInstMove(block, dst2, vr);
                mov.setShift(new ArmShift(ArmShift.ShiftType.Lsl, p));
                if (imm < 0) {
                    new ArmInstUnary(block, ArmInstKind.INeg, dst, dst2);
                }
            }
        }

    }

    // private String getSymbol(String symbol) {
    // var sb = new StringBuffer("@" + symbol);
    // int p = sb.indexOf("\n");
    // while (p != sb.length() - 1 && p != -1) {
    // sb.insert(p + 1, "@");
    // p = sb.indexOf("\n", p + 1);
    // }
    // return sb.toString();
    // }
}
