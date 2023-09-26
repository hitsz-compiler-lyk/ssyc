package backend.codegen;

import pass.ir.ClearUnreachableBlock;
import pass.ir.GCM;
import utils.ImmUtils;
import backend.lir.ArmBlock;
import backend.lir.ArmFunction;
import backend.lir.ArmModule;
import backend.lir.ArmShift;
import backend.lir.inst.*;
import backend.lir.inst.ArmInst.ArmCondType;
import backend.lir.inst.ArmInst.ArmInstKind;
import backend.lir.operand.*;
import ir.Module;
import ir.*;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.constant.BoolConst;
import ir.constant.Constant;
import ir.constant.FloatConst;
import ir.constant.IntConst;
import ir.inst.*;
import ir.type.ArrayIRTy;
import ir.type.IRType;
import ir.type.PointerIRTy;
import ir.visitor.InstructionVisitor;
import utils.Log;
import utils.Pair;

import java.util.*;

/**
 * 在考虑各种底层信息的情况下, 将 SSA IR 转为 TAC 形式的 LIR
 * <p>
 * 本转换会考虑:
 * <ul>
 *      <li>立即数的编码问题</li>
 *      <li>函数的调用约定问题</li>
 *      <li>一些简单的优化, 比如乘加融合</li>
 *      <li> 一些强度削减, 比如特定乘除法换成位移和加法</li>
 * </ul>
 * 考虑拆分转换代码, 拆成最简单的 SSA -> TAC 转换 (只做 Phi Destruction 和映射) 和若干个优化与底层相关转换 pass 的形式
 * <p>
 */
public class ToLIRManager {
    private final Map<Value, Operand> valMap = new HashMap<>();
    private final Map<Function, ArmFunction> funcMap = new HashMap<>();
    private final Map<BasicBlock, ArmBlock> blockMap = new HashMap<>();
    private final Map<GlobalVar, Operand> globalMap = new HashMap<>();
    private final ArmModule armModule = new ArmModule();
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
            put(InstKind.FCmpLt, ArmCondType.Mi);
            put(InstKind.FCmpLe, ArmCondType.Ls);
        }
    };

    private final Module irModule;

    public ToLIRManager(Module irModule) {
        this.irModule = irModule;
    }

    public ArmModule codeGenLIR() {
        int acCnt = 0;
        // 数组初始化信息
        for (var val : irModule.getArrayConstants()) {
            armModule.getArrayConstants().put("meminit_array_" + acCnt, val);
            valMap.put(val, new Addr("meminit_array_" + acCnt++));
        }

        for (final var gv : irModule.getVariables()) {
            armModule.getGlobalVariables().put(gv.getSymbol().getName(), gv);
        }

        // 添加Global信息
        for (var val : armModule.getGlobalVariables().values()) {
            valMap.put(val, new Addr(val.getSymbol().getName()));
        }

        for (var func : irModule.getFunctions()) {
            final var funcType = func.getType();
            final var paramTypes = funcType.getParamTypes();

            final var armFunc = new ArmFunction(func.getFunctionSourceName(), paramTypes.size());

            if (func.isExternal()) {
                final var fcnt = (int) paramTypes.stream().filter(IRType::isFloat).count();
                final var icnt = (int) paramTypes.stream().filter(IRType::isInt).count();

                armFunc.setFparamsCnt(Integer.min(fcnt, 16));
                armFunc.setIparamsCnt(Integer.min(icnt, 4));
                armFunc.setReturnFloat(funcType.getReturnType().isFloat());
                armFunc.setExternal(true);
                funcMap.put(func, armFunc);
                continue;
            }

            List<Parameter> finalParams = new ArrayList<>();

            Set<Integer> paramIdx = new HashSet<>();
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

            armModule.getFunctions().add(armFunc);
            armFunc.setParameter(finalParams);
            armFunc.setFparamsCnt(fcnt);
            armFunc.setIparamsCnt(icnt);
            armFunc.setReturnFloat(funcType.getReturnType().isFloat());
            funcMap.put(func, armFunc);
            String funcName = func.getFunctionSourceName();


            var gcm = new GCM();
            ClearUnreachableBlock.run(func);
            gcm.runOnFunction(func);
            for (int i = 0; i < func.size(); i++) {
                final var block = func.get(i);
                final var armBlock = new ArmBlock(armFunc, block.getSymbol().getName() + "_" + funcName + "_" + i);
                armBlock.setLoopDepth(gcm.loopDepth(block));
                blockMap.put(block, armBlock);
            }

            // 处理起始块的后驱和第一个基本块的前驱
            if (func.size() > 0) {
                var armBlock = blockMap.get(func.get(0));
                armFunc.getPrologue().setTrueSuccBlock(armBlock);
                armBlock.addPred(armFunc.getPrologue());
            }

            // 处理Arm基本块的前后驱
            for (final var block : func) {
                final var armBlock = blockMap.get(block);
                for (var pred : block.getPredecessors()) {
                    armBlock.addPred(blockMap.get(pred));
                }

                final var succ = block.getSuccessors();
                if (succ.size() == 1) {
                    armBlock.setTrueSuccBlock(blockMap.get(succ.get(0)));
                }
                if (succ.size() == 2) {
                    armBlock.setTrueSuccBlock(blockMap.get(succ.get(0)));
                    armBlock.setFalseSuccBlock(blockMap.get(succ.get(1)));
                }
            }
        }

        for (var func : irModule.getFunctions()) {
            if (func.isExternal()) {
                continue;
            }
            final var armFunc = funcMap.get(func);

            for (final var block : func) {
                final var armBlock = blockMap.get(block);
                // 清空globalMap 不允许跨基本块读取Global地址
                globalMap.clear();

                final var visitor = new InstructionResolver(armFunc, armBlock);
                block.forEach(visitor::visit);
            }

            // phi 处理
            // 相当于在每个基本块最后都添加一条move指令 将incoming基本块里面的value move到一个临时的寄存器
            // 然后在phi所在的基本块将这个临时寄存器move到phi中
            // in incoming block:
            // move temp value
            // in phi block:
            // move phi temp
            final var firstBranch = new HashMap<ArmBlock, ArmInst>();
            for (var block : func) {
                var armBlock = blockMap.get(block);
                for (var inst : armBlock) {
                    if (inst instanceof ArmInstBranch) {
                        firstBranch.put(armBlock, inst);
                        break;
                    }
                }
            }

            for (var block : func) {
                var armBlock = blockMap.get(block);
                for (final var phi : block.phis()) {
                    var incomingInfoIt = phi.getIncomingInfos().iterator();
                    var phiReg = resolveOperand(phi, armBlock, armFunc);
                    var temp = phiReg.isInt() ? new IVirtualReg() : new FVirtualReg();
                    armBlock.add(0, new ArmInstMove(phiReg, temp));
                    while (incomingInfoIt.hasNext()) {
                        var incomingInfo = incomingInfoIt.next();
                        var src = incomingInfo.value();
                        var incomingBlock = blockMap.get(incomingInfo.block());
                        var srcReg = resolvePhiOperand(src, incomingBlock, armFunc);
                        var move = new ArmInstMove(temp, srcReg);
                        if (firstBranch.containsKey(incomingBlock)) {
                            var branch = firstBranch.get(incomingBlock);
                            branch.insertBeforeCO(move);
                        } else {
                            incomingBlock.add(move);
                        }
                    }
                }
            }
        }

        return armModule;
    }

    class InstructionResolver implements InstructionVisitor<Void> {
        private final ArmBlock block;
        private final ArmFunction func;

        InstructionResolver(ArmFunction func, ArmBlock block) {
            this.block = block;
            this.func = func;
        }

        @Override
        public Void visitBinaryOpInst(BinaryOpInst inst) {
            var lhs = inst.getLHS();
            var rhs = inst.getRHS();
            Operand lhsReg, rhsReg, dstReg;

            switch (inst.getKind()) {
                case IAdd -> {
                    var instKind = ArmInstKind.IAdd;
                    // 如果 lhs 是一个立即数的话，则交换立即数
                    if (lhs instanceof IntConst) {
                        int imm = ((IntConst) lhs).getValue();
                        // 如果 -imm 是合法条件 变成减法 从而减少一个 mov32
                        if (ImmUtils.checkEncodeImm(-imm)) {
                            rhsReg = resolveIImm(-imm, block, func);
                            instKind = ArmInstKind.ISub;
                        } else {
                            rhsReg = resolveOperand(lhs, block, func);
                        }
                        lhsReg = resolveLhsOperand(rhs, block, func);
                    } else {
                        // 如果 -imm 是合法条件 变成减法 从而减少一个 mov32
                        if (rhs instanceof IntConst && ImmUtils.checkEncodeImm(-((IntConst) rhs).getValue())) {
                            rhsReg = resolveIImm(-((IntConst) rhs).getValue(), block, func);
                            instKind = ArmInstKind.ISub;
                        } else {
                            rhsReg = resolveOperand(rhs, block, func);
                        }
                        lhsReg = resolveLhsOperand(lhs, block, func);
                    }
                    dstReg = resolveOperand(inst, block, func);
                    // add inst inst.lhs inst.rhs
                    new ArmInstBinary(block, instKind, dstReg, lhsReg, rhsReg);
                }
                case ISub -> {
                    if (lhs instanceof IntConst) {
                        // 操作数交换 使用反向减法
                        lhsReg = resolveLhsOperand(rhs, block, func);
                        rhsReg = resolveOperand(lhs, block, func);
                        dstReg = resolveOperand(inst, block, func);
                        // rsb inst inst.rhs inst.lhs
                        new ArmInstBinary(block, ArmInstKind.IRsb, dstReg, lhsReg, rhsReg);
                    } else {
                        var instKind = ArmInstKind.ISub;
                        // 如果 -imm 是合法条件 变成加法 从而减少一个 mov32
                        if (rhs instanceof IntConst && ImmUtils.checkEncodeImm(-((IntConst) rhs).getValue())) {
                            rhsReg = resolveIImm(-((IntConst) rhs).getValue(), block, func);
                            instKind = ArmInstKind.IAdd;
                        } else {
                            rhsReg = resolveOperand(rhs, block, func);
                        }
                        lhsReg = resolveLhsOperand(lhs, block, func);
                        dstReg = resolveOperand(inst, block, func);
                        // SUB inst inst.getLHS() inst.getRHS()
                        new ArmInstBinary(block, instKind, dstReg, lhsReg, rhsReg);
                    }
                }
                case IMul -> {
                    dstReg = resolveOperand(inst, block, func);
                    if (lhs instanceof IntConst || rhs instanceof IntConst) {
                        Operand src = null;
                        int imm = 0;
                        if (lhs instanceof IntConst && ImmUtils.canOptimizeMul(((IntConst) lhs).getValue())) {
                            src = resolveLhsOperand(rhs, block, func);
                            imm = ((IntConst) lhs).getValue();
                        }
                        if (rhs instanceof IntConst && ImmUtils.canOptimizeMul(((IntConst) rhs).getValue())) {
                            src = resolveLhsOperand(lhs, block, func);
                            imm = ((IntConst) rhs).getValue();
                        }
                        if (src != null) {
                            // 乘法优化
                            // 只会优化以下的情况
                            // |x| = 2^x +- 2^y / 0 (x >= y)
                            resolveConstMuL(dstReg, src, imm, block);
                            break;
                        }
                    }
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    rhsReg = resolveLhsOperand(rhs, block, func);
                    // mul inst inst.lhs inst.rhs
                    new ArmInstBinary(block, ArmInstKind.IMul, dstReg, lhsReg, rhsReg);
                }
                case IDiv -> {
                    // 除法无法交换操作数
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    dstReg = resolveOperand(inst, block, func);
                    if (rhs instanceof IntConst) {
                        var imm = ((IntConst) rhs).getValue();
                        resolveConstDiv(dstReg, lhsReg, imm, block, func);
                    } else {
                        rhsReg = resolveLhsOperand(rhs, block, func); // sdiv 不允许立即数
                        // sdiv inst inst.lhs inst.rhs
                        new ArmInstBinary(block, ArmInstKind.IDiv, dstReg, lhsReg, rhsReg);
                    }
                }
                case IMod -> {
                    // x % y == x - (x / y) * y
                    // %0 %1 %2^n 特殊判断
                    if (rhs instanceof IntConst) {
                        var imm = ((IntConst) rhs).getValue();
                        if (imm == 0) {
                            // %0 特殊判断
                            dstReg = resolveOperand(inst, block, func);
                            lhsReg = resolveOperand(lhs, block, func);
                            // mov inst inst.lhs
                            new ArmInstMove(block, dstReg, lhsReg);
                            break;
                        } else if (Math.abs(imm) == 1) {
                            // %1 特殊判断
                            dstReg = resolveOperand(inst, block, func);
                            // mov inst 0
                            new ArmInstMove(block, dstReg, new IImm(0));
                            break;
                        } else if (ImmUtils.is2Power(Math.abs(imm))) {
                            // %2^n 特殊判断
                            // %2
                            // add  vr, lhs, lhs, lsr #31
                            // bic  vr2, vr, #1
                            // sub  inst, lhs, vr2
                            // %2^n (n >= 2)
                            // asr  vr, lhs, #31
                            // add  vr2, lhs, vr, lsr #(32 - n)
                            // bic  vr3, vr, #2^n - 1
                            // sub  inst, lhs, vr3
                            int abs = Math.abs(imm);
                            int l = ImmUtils.countTailingZeros(abs);
                            dstReg = resolveOperand(inst, block, func);
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
                            var bicImm = resolveIImm(abs - 1, block, func);
                            var vr3 = new IVirtualReg();
                            new ArmInstBinary(block, ArmInstKind.Bic, vr3, vr2, bicImm);
                            new ArmInstBinary(block, ArmInstKind.ISub, dstReg, src, vr3);
                            break;
                        }
                    }
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    dstReg = resolveOperand(inst, block, func);
                    var vr = new IVirtualReg();
                    // sdiv vr inst.lhs inst.rhs
                    if (rhs instanceof IntConst) {
                        var imm = ((IntConst) rhs).getValue();
                        resolveConstDiv(vr, lhsReg, imm, block, func);
                    } else {
                        rhsReg = resolveLhsOperand(rhs, block, func); // 实际上rhs 也会在 Ternay 指令中 变成 lhs
                        new ArmInstBinary(block, ArmInstKind.IDiv, vr, lhsReg, rhsReg);
                    }
                    // mls inst vr inst.rhs inst.lhs
                    // inst = inst.lhs - vr * inst.rhs
                    if (rhs instanceof IntConst && ImmUtils.canOptimizeMul(((IntConst) rhs).getValue())) {
                        var imm = ((IntConst) rhs).getValue();
                        var vr2 = new IVirtualReg();
                        resolveConstMuL(vr2, vr, imm, block);
                        new ArmInstBinary(block, ArmInstKind.ISub, dstReg, lhsReg, vr2);
                    } else {
                        rhsReg = resolveLhsOperand(rhs, block, func); // 实际上rhs 也会在 Ternay 指令中 变成 lhs
                        new ArmInstTernary(block, ArmInstKind.IMulSub, dstReg, vr, rhsReg, lhsReg);
                    }
                }
                case FAdd -> {
                    if (lhs instanceof Constant) {
                        lhsReg = resolveLhsOperand(rhs, block, func);
                        rhsReg = resolveOperand(lhs, block, func);
                    } else {
                        lhsReg = resolveLhsOperand(lhs, block, func);
                        rhsReg = resolveOperand(rhs, block, func);
                    }
                    dstReg = resolveOperand(inst, block, func);
                    // vadd.f32 inst inst.lhs inst.rhs
                    new ArmInstBinary(block, ArmInstKind.FAdd, dstReg, lhsReg, rhsReg);
                }
                case FSub -> {
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    rhsReg = resolveOperand(rhs, block, func);
                    dstReg = resolveOperand(inst, block, func);
                    // vsub.f32 inst inst.lhs inst.rhs
                    new ArmInstBinary(block, ArmInstKind.FSub, dstReg, lhsReg, rhsReg);
                }
                case FMul -> {
                    if (lhs instanceof Constant) {
                        lhsReg = resolveLhsOperand(rhs, block, func);
                        rhsReg = resolveOperand(lhs, block, func);
                    } else {
                        lhsReg = resolveLhsOperand(lhs, block, func);
                        rhsReg = resolveOperand(rhs, block, func);
                    }
                    dstReg = resolveOperand(inst, block, func);
                    // vmul.f32 inst inst.lhs inst.rhs
                    new ArmInstBinary(block, ArmInstKind.FMul, dstReg, lhsReg, rhsReg);
                }
                case FDiv -> {
                    lhsReg = resolveLhsOperand(lhs, block, func);
                    rhsReg = resolveOperand(rhs, block, func);
                    dstReg = resolveOperand(inst, block, func);
                    // vdiv.f32 inst inst.lhs inst.rhs
                    new ArmInstBinary(block, ArmInstKind.FDiv, dstReg, lhsReg, rhsReg);
                }
                default -> Log.ensure(false, "binary inst not implement");
            }
            return null;
        }

        @Override
        public Void visitUnaryOpInst(UnaryOpInst inst) {
            var src = inst.getArg();
            var srcReg = resolveOperand(src, block, func);
            var dstReg = resolveLhsOperand(inst, block, func);

            if (inst.getKind() == InstKind.INeg) {
                // neg inst inst.arg
                new ArmInstUnary(block, ArmInstKind.INeg, dstReg, srcReg);
            } else if (inst.getKind() == InstKind.FNeg) {
                // vneg.f32 inst inst.arg
                new ArmInstUnary(block, ArmInstKind.FNeg, dstReg, srcReg);
            }
            return null;
        }

        @Override
        public Void visitLoadInst(LoadInst inst) {
            var addr = inst.getPtr();
            Operand addrReg;
            var dstReg = resolveLhsOperand(inst, block, func);
            if (addr instanceof GlobalVar && inst.getType().isPtr()) {
                Log.ensure(valMap.containsKey(addr));
                addrReg = valMap.get(addr);
            } else {
                addrReg = resolveOperand(addr, block, func);
            }
            // ldr inst inst.ptr
            var load = new ArmInstLoad(block, dstReg, addrReg);
            if (addr instanceof GlobalVar && inst.getType().isPtr()) {
                func.getAddrLoadMap().put(dstReg, load);
            }
            return null;
        }

        @Override
        public Void visitStoreInst(StoreInst inst) {
            var addr = inst.getPtr();
            var var = inst.getVal();

            var varReg = resolveLhsOperand(var, block, func);
            var addrReg = resolveOperand(addr, block, func);
            // str inst.val inst.ptr
            new ArmInstStore(block, varReg, addrReg);
            return null;
        }

        @Override
        public Void visitCAllocInst(CAllocInst inst) {
            var dst = resolveOperand(inst, block, func);
            // add inst [sp, 之前已用的栈大小]
            var alloc = new ArmInstStackAddr(block, dst, new IImm(func.getStackSize()));
            alloc.setCAlloc(true);
            func.getStackAddrMap().put(dst, alloc);
            // 增加栈大小
            func.addStackSize(inst.getAllocSize());
            return null;
        }

        @Override
        public Void visitGEPInst(GEPInst inst) {
            var p = ((PointerIRTy) inst.getPtr().getType()).getBaseType();
            var indices = inst.getIndices();
            List<Integer> dim = new ArrayList<>();

            while (p.isArray()) {
                dim.add(p.getSize());
                p = ((ArrayIRTy) p).getElementType();
            }
            // 加上最后一个基础类型 int/float的size
            dim.add(p.getSize());

            // 原基地址
            var arr = resolveLhsOperand(inst.getPtr(), block, func);
            var ret = resolveLhsOperand(inst, block, func);
            var tot = 0;
            for (int i = 0; i < indices.size(); i++) {
                var offset = resolveOperand(indices.get(i), block, func);
                var length = dim.get(i);

                if (offset instanceof IImm offsetImm) {
                    tot += offsetImm.getImm() * length;
                    if (i == indices.size() - 1) {
                        if (tot == 0) {
                            // mov inst 当前地址
                            new ArmInstMove(block, ret, arr);
                        } else {
                            var imm = resolveIImm(tot, block, func);
                            // add inst 当前地址 + 偏移量
                            new ArmInstBinary(block, ArmInstKind.IAdd, ret, arr, imm);
                        }
                    }
                } else {
                    if (tot != 0) {
                        var imm = resolveIImm(tot, block, func);
                        var vr = new IVirtualReg();
                        // add vr 当前地址 + 偏移量
                        // 当前地址 = vr
                        new ArmInstBinary(block, ArmInstKind.IAdd, vr, arr, imm);
                        tot = 0;
                        arr = vr;
                    }
                    Operand dst = ret;
                    if (i != indices.size() - 1) {
                        dst = new IVirtualReg();
                    }
                    if (ImmUtils.canOptimizeMul(length)) {
                        var vr = new IVirtualReg();
                        resolveConstMuL(vr, offset, length, block);
                        new ArmInstBinary(block, ArmInstKind.IAdd, dst, arr, vr);
                    } else {
                        var imm = resolveIImmInReg(length, block, func);
                        // mla inst dim[i] indices[i] 当前地址
                        // inst = dim[i] * indices[i] + 当前地址
                        new ArmInstTernary(block, ArmInstKind.IMulAdd, dst, offset, imm, arr);
                    }

                    if (i != indices.size() - 1) {
                        arr = dst;
                    }
                }
            }
            return null;
        }

        @Override
        public Void visitCallInst(CallInst inst) {
            Set<Integer> argsIdx = new HashSet<>();
            List<Value> finalArg = new ArrayList<>();
            int fcnt = 0, icnt = 0;
            var args = inst.getArgList();
            // 找到前4个整型的参数
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
            // 找到前16个浮点型的参数
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
            // 计算得到
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
                // str inst.args[i] [sp, -(inst.args.size-i)*4]
                // 越后面的参数越靠近栈顶
                Operand addr = IPhyReg.SP;
                // 如果offset不符合范围
                if (!ImmUtils.checkOffsetRange(nowOffset, src)) {
                    // 先寻找一个stack addr 指令
                    for (var entry : stackAddrSet) {
                        var op = entry.key;
                        var instOffset = entry.value.getIntOffset();
                        if (ImmUtils.checkOffsetRange(nowOffset - instOffset, src)) {
                            addr = op;
                            finalOffset = nowOffset - instOffset;
                            break;
                        }
                    }
                    // 如果没找到，则创建一个新的stack addr指令
                    if (addr.equals(IPhyReg.SP)) {
                        int instOffset = nowOffset / 1024 * 1024;// 负值 因此是往更大的方向
                        var vr = new IVirtualReg();
                        var stackAddr = new ArmInstStackAddr(block, vr, new IImm(instOffset));
                        stackAddr.setFix(true);
                        addr = vr;
                        finalOffset = nowOffset - instOffset;
                        Log.ensure(ImmUtils.checkOffsetRange(finalOffset, src), "check offset is illegal");
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
                new ArmInstMove(block, FPhyReg.S(i - icnt), src);
            }
            Operand offsetOp = null;
            if (finalArg.size() > icnt + fcnt) {
                offsetOp = resolveIImm(offset, block, func);
            }
            for (int i = icnt - 1; i >= 0; i--) {
                new ArmInstMove(block, IPhyReg.R(i), argOp.get(i));
            }
            if (finalArg.size() > icnt + fcnt) {
                // sub sp sp (inst.args.size - 4) * 4
                new ArmInstBinary(block, ArmInstKind.ISub, IPhyReg.SP, IPhyReg.SP, offsetOp);
            }
            new ArmInstCall(block, funcMap.get(inst.getCallee()));
            if (finalArg.size() > icnt + fcnt) {
                // add sp sp (inst.args.size - 4) * 4
                new ArmInstBinary(block, ArmInstKind.IAdd, IPhyReg.SP, IPhyReg.SP, offsetOp);
            }
            if (!inst.getType().isVoid()) {
                var dst = resolveLhsOperand(inst, block, func);
                if (inst.getType().isFloat()) {
                    // 如果结果是一个浮点数 则直接用s0来保存数据
                    // vmov inst s0
                    // atpcs 规范
                    new ArmInstMove(block, dst, FPhyReg.S(0));
                } else if (inst.getType().isInt()) {
                    // 否则用r0来保存数据
                    // mov inst r0
                    new ArmInstMove(block, dst, IPhyReg.R(0));
                }
            }
            return null;
        }

        @Override
        public Void visitReturnInst(ReturnInst inst) {
            // 如果返回值不为空
            if (inst.getReturnValue().isPresent()) {
                var src = inst.getReturnValue().get();
                var srcReg = resolveOperand(src, block, func);
                if (src.getType().isFloat()) {
                    // atpcs 规范
                    // vmov S0 inst.getReturnValue()
                    new ArmInstMove(block, FPhyReg.S(0), srcReg);
                } else {
                    // vmov R0 inst.getReturnValue()
                    new ArmInstMove(block, IPhyReg.R(0), srcReg);
                }

            }
            new ArmInstReturn(block);
            return null;
        }

        @Override
        public Void visitIntToFloatInst(IntToFloatInst inst) {
            // 需要先转到浮点寄存器 才能使用 vcvt
            if (inst.getFrom() instanceof BoolToIntInst) {
                resolveBoolToIntInst((BoolToIntInst) inst.getFrom(), block, func);
            }
            var vr = new FVirtualReg();
            var src = resolveOperand(inst.getFrom(), block, func);
            var dst = resolveOperand(inst, block, func);
            // vmov vr inst.getFrom()
            new ArmInstMove(block, vr, src);
            // vcvt.f32.s32 inst vr
            new ArmInstIntToFloat(block, dst, vr);
            return null;
        }

        @Override
        public Void visitFloatToIntInst(FloatToIntInst inst) {
            // 先使用 vcvt 再转到整型寄存器中
            var vr = new FVirtualReg();
            var src = resolveOperand(inst.getFrom(), block, func);
            var dst = resolveOperand(inst, block, func);
            // vcvt.f32.s32 inst vr
            new ArmInstFloatToInt(block, vr, src);
            // vmov vr inst.getFrom()
            new ArmInstMove(block, dst, vr);
            return null;
        }

        @Override
        public Void visitBrInst(BrInst inst) {
            // B inst.getNextBB()
            new ArmInstBranch(block, blockMap.get(inst.getNextBB()));
            return null;
        }

        @Override
        public Void visitBrCondInst(BrCondInst inst) {
            var cond = inst.getCond();
            if (cond instanceof BoolConst boolConst) {
                if (boolConst.getValue()) {
                    // B inst.getTrueBB()
                    new ArmInstBranch(block, blockMap.get(inst.getTrueBB()));
                } else {
                    // B inst.getFalseBB()
                    new ArmInstBranch(block, blockMap.get(inst.getFalseBB()));
                }
            } else if (cond instanceof CmpInst) {
                // 指令可能会因为左操作数为立即数而进行反向交换
                var Armcond = resolveCmpInst((CmpInst) cond, block, func);
                // B.{cond} inst.getTrueBB()
                new ArmInstBranch(block, blockMap.get(inst.getTrueBB()), Armcond);
                // B inst.getFalseBB()
                // 可以考虑在CodeGen的时候再添加 连续的基本块不需要该指令
                new ArmInstBranch(block, blockMap.get(inst.getFalseBB()));
            } else {
                Log.ensure(false, "BrCondInst Cond Illegal");
            }
            return null;
        }

        @Override
        public Void visitMemInitInst(MemInitInst inst) {
            var dst = resolveOperand(inst.getArrayPtr(), block, func);
            var ac = inst.getInit();
            int size = inst.getInit().getType().getSize();
            if (ac instanceof ZeroArrayConst) {
                // 如果是全0数组 直接调用memset
                var imm = resolveIImm(size, block, func);
                new ArmInstMove(block, IPhyReg.R(0), dst);
                new ArmInstMove(block, IPhyReg.R(1), new IImm(0));
                new ArmInstMove(block, IPhyReg.R(2), imm);
                new ArmInstCall(block, "memset", 3, 0);
            } else {
                // 否则调用memcpy
                var src = resolveOperand(ac, block, func);
                var imm = resolveIImm(size, block, func);
                new ArmInstMove(block, IPhyReg.R(0), dst);
                new ArmInstMove(block, IPhyReg.R(1), src);
                new ArmInstMove(block, IPhyReg.R(2), imm);
                new ArmInstCall(block, "memcpy", 3, 0);
            }
            return null;
        }

        @Override
        public Void visitBoolToIntInst(BoolToIntInst inst) {
            return null;
        }

        @Override
        public Void visitCmpInst(CmpInst inst) {
            return null;
        }

        @Override
        public Void visitPhiInst(PhiInst inst) {
            return null;
        }
    }

    private IVirtualReg loadImmToVReg(int val, ArmBlock block, ArmFunction func) {
        final var reg = new IVirtualReg();
        final var imm = new IImm(val);
        final var move = new ArmInstMove(block, reg, imm);
        func.getImmMap().put(reg, move);
        return reg;
    }

    private FVirtualReg loadImmToVReg(float val, ArmBlock block, ArmFunction func) {
        final var reg = new FVirtualReg();
        final var imm = new FImm(val);
        final var move = new ArmInstMove(block, reg, imm);
        func.getImmMap().put(reg, move);
        return reg;
    }

    private IVirtualReg resolveIImmInReg(int val, ArmBlock block, ArmFunction func) {
        return loadImmToVReg(val, block, func);
    }

    private Operand resolveIImm(int val, ArmBlock block, ArmFunction func) {
        if (ImmUtils.checkEncodeImm(val)) {
            // 可以直接编码在普通指令里的立即数, 就直接返回一个IImm
            return new IImm(val);
        } else {
            // 不可以直接编码的, 先用 (v)mov 指令加载到一个寄存器里, 后面使用那个寄存器
            return loadImmToVReg(val, block, func);
        }
    }

    private Operand resolveImm(Constant val, ArmBlock block, ArmFunction func) {
        if (val instanceof IntConst ic) {
            return resolveIImm(ic.getValue(), block, func);
        } else if (val instanceof FloatConst fc) {
            // 浮点数必须放在寄存器里, 不可编码于指令
            return loadImmToVReg(fc.getValue(), block, func);
        } else {
            Log.ensure(false, "Resolve Operand: Bool or Array Constant");
            throw new RuntimeException("Dead code");
        }
    }

    private Operand resolveParameter(Parameter val, ArmFunction func) {
        if (!valMap.containsKey(val)) {
            Operand vr = val.getParamType().isFloat() ? new FVirtualReg() : new IVirtualReg();
            var params = func.getParameter();
            int fcnt = func.getFparamsCnt();
            int icnt = func.getIparamsCnt();
            valMap.put(val, vr);
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).equals(val)) {
                    if (i < icnt) {
                        // mov vr ri
                        // r0 - r3 在后续的基本块中会修改 因此需要在最前面的块当中就读取出来
                        // 加到最前面防止后续load修改了r0 - r3
                        var move = new ArmInstMove(vr, IPhyReg.R(i));
                        func.getPrologue().add(0, move);
                    } else if (i < icnt + fcnt) {
                        var move = new ArmInstMove(vr, FPhyReg.S(i - icnt));
                        func.getPrologue().add(0, move);
                    } else {
                        // ldr vr [sp, (i-4)*4]
                        // 寄存器分配后修改为 ldr vr [sp, (i-4)*4 + stackSize + push的大小]
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

    private Operand resolveLhsOperand(Value val, ArmBlock block, ArmFunction func) {
        // 因为不是最后一个操作数, 不能直接给予一个立即数
        if (val instanceof IntConst ic) {
            return loadImmToVReg(ic.getValue(), block, func);
        } else {
            return resolveOperand(val, block, func);
        }
    }

    // Phi直接把Op返回 不用中转任何指令
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
        // 全局变量应该事先处理
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
        if (val instanceof Parameter parameter && func.getParameter().contains(parameter)) {
            return resolveParameter(parameter, func);
        } else if (val instanceof GlobalVar gv) {
            return resolveGlobalVar(gv, block, func);
        } else if (valMap.containsKey(val)) {
            return valMap.get(val);
        } else if (val instanceof Constant c) {
            return resolveImm(c, block, func);
        } else {
            Operand vr = val.getType().isFloat() ? new FVirtualReg() : new IVirtualReg();
            valMap.put(val, vr);
            return vr;
        }
    }

    private ArmCondType resolveCmpInst(CmpInst inst, ArmBlock block, ArmFunction func) {
        var lhs = inst.getLHS();
        var rhs = inst.getRHS();
        var cond = condMap.get(inst.getKind());

        for (var ch : Arrays.asList(lhs, rhs)) {
            // 将Bool比较的结果转换为Int
            if (ch instanceof BoolToIntInst) {
                resolveBoolToIntInst((BoolToIntInst) ch, block, func);
            }
        }

        Operand lhsReg, rhsReg;
        // 是否和负值比较
        boolean isCmn = false;
        if (lhs instanceof Constant) {
            if (lhs instanceof IntConst ic) {
                if (ImmUtils.checkEncodeImm(ic.getValue())) {
                    rhsReg = resolveIImm(ic.getValue(), block, func);
                } else if (ImmUtils.checkEncodeImm(-ic.getValue())) {
                    rhsReg = resolveIImm(-ic.getValue(), block, func);
                    isCmn = true;
                } else {
                    rhsReg = resolveOperand(lhs, block, func);
                }
            } else {
                rhsReg = resolveOperand(lhs, block, func);
            }
            lhsReg = resolveLhsOperand(rhs, block, func);
            // 反向交换
            cond = cond.getEqualOppCondType();
        } else {
            if (rhs instanceof IntConst ic) {
                if (ImmUtils.checkEncodeImm(ic.getValue())) {
                    rhsReg = resolveIImm(ic.getValue(), block, func);
                } else if (ImmUtils.checkEncodeImm(-ic.getValue())) {
                    rhsReg = resolveIImm(-ic.getValue(), block, func);
                    isCmn = true;
                } else {
                    rhsReg = resolveOperand(rhs, block, func);
                }
            } else {
                rhsReg = resolveOperand(rhs, block, func);
            }
            lhsReg = resolveLhsOperand(lhs, block, func);
        }

        // cmp( vcmp.f32 ) inst.getLHS() inst.getRHS() (可能交换LHS/RHS)
        // vmrs APSR_nzcv fpscr
        var cmp = new ArmInstCmp(block, lhsReg, rhsReg, cond);
        cmp.setCmn(isCmn);
        return cond;
    }

    private void resolveBoolToIntInst(BoolToIntInst inst, ArmBlock block, ArmFunction func) {
        var src = inst.getFrom();
        var dstReg = resolveOperand(inst, block, func);
        if (src instanceof BoolConst bc) {
            if (bc.getValue()) {
                // mov inst #1
                new ArmInstMove(block, dstReg, new IImm(1));
            } else {
                // mov inst #0
                new ArmInstMove(block, dstReg, new IImm(0));
            }
        } else if (src instanceof CmpInst) {
            var cond = resolveCmpInst((CmpInst) src, block, func);
            // mov.{cond} inst #1
            new ArmInstMove(block, dstReg, new IImm(1), cond);
            // mov.{OppCond} inst #0
            new ArmInstMove(block, dstReg, new IImm(0), cond.getOppCondType());
        } else {
            Log.ensure(false);
        }
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
        } else if (ImmUtils.is2Power(abs)) {
            // 如果不除2
            // asr  vr, src, #31
            // add vr2, vr2,  vr, lsr #32 - ctz(imm)
            // asr dst, vr2, ctz(imm)
            // 如果除2
            // add  vr, vr, src, lsr #32 - ctz(imm)
            // asr dst, vr, ctz(imm)
            int l = ImmUtils.countTailingZeros(abs);
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
            var vn = resolveIImmInReg(n, block, func);
            var vr = new IVirtualReg();
            if (m >= 2147483648L) {
                new ArmInstTernary(block, ArmInstKind.ILMulAdd, vr, src, vn, src);
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

    private void resolveConstMuL(Operand dst, Operand src, int imm, ArmBlock block) {
        Log.ensure(ImmUtils.canOptimizeMul(imm), "optimize mul failde");
        int abs = Math.abs(imm);
        int l = ImmUtils.countTailingZeros(abs);
        if (abs == 0) {
            new ArmInstMove(block, dst, new IImm(0));
        } else if (abs == 1) {
            if (imm > 0) {
                new ArmInstMove(block, dst, src);
            } else {
                new ArmInstUnary(block, ArmInstKind.INeg, dst, src);
            }
        } else if (ImmUtils.is2Power(abs)) {
            if (imm > 0) {
                // lsl dst, src, #ctz(imm)
                var move = new ArmInstMove(block, dst, src);
                move.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            } else {
                // mov  vr, #0
                // sub dst, vr, src, lsl #ctz(imm)
                var vr = new IVirtualReg();
                new ArmInstMove(block, vr, new IImm(0));
                var sub = new ArmInstBinary(block, ArmInstKind.ISub, dst, vr, src);
                sub.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            }
        } else if (ImmUtils.is2Power(abs - 1)) {
            // imm > 0
            // add dst, src, src, lsl #ctz(abs-1)
            // imm < 0
            // add  vr, src, src, lsl #ctz(abs-1)
            // rsb dst,  vr, #0
            l = ImmUtils.countTailingZeros(abs - 1);
            var dst2 = dst;
            if (imm < 0) {
                dst2 = new IVirtualReg();
            }
            var add = new ArmInstBinary(block, ArmInstKind.IAdd, dst2, src, src);
            add.setShift(new ArmShift(ArmShift.ShiftType.Lsl, l));
            if (imm < 0) {
                new ArmInstUnary(block, ArmInstKind.INeg, dst, dst2);
            }
        } else if (ImmUtils.is2Power(abs + 1)) {
            l = ImmUtils.countTailingZeros(abs + 1);
            // imm > 0
            // rsb dst, src, src, lsl #ctz(abs+1)
            // imm < 0
            // sub dst, src, src, lsl #ctz(abs+1)
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
                if (ImmUtils.is2Power(abs + (1 << p))) {
                    IsAdd = true;
                    nowAbs = abs + (1 << p);
                    break;
                }
                if (ImmUtils.is2Power(abs - (1 << p))) {
                    nowAbs = abs - (1 << p);
                    break;
                }
            }
            l = ImmUtils.countTailingZeros(nowAbs);
            // imm = (1<<l) + (1<<p) (l > p)
            // add  vr, src, src, lsl #l-p
            // lsl dst,  vr. #p
            // imm = (1<<l) - (1<<p) (l > p)
            // rsb  vr, src, src, lsl #l-p
            // lsl dst,  vr. #p
            // imm = -((1<<l) + (1<<p)) (l > p)
            // add  vr, src, src, lsl #l-p
            // lsl vr2,  vr. #p
            // rsb dst, vr2, #0
            // imm = -((1<<l) - (1<<p)) (l > p)
            // sub  vr, src, src, lsl #l-p
            // lsl dst,  vr. #p
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

}
