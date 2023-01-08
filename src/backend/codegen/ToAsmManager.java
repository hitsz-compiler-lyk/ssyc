package backend.codegen;

import backend.lir.ArmFunction;
import backend.lir.ArmModule;
import backend.lir.inst.ArmInst.ArmCondType;
import backend.lir.inst.ArmInst.ArmInstKind;
import backend.lir.inst.*;
import backend.lir.operand.*;
import backend.lir.visitor.ArmInstVisitor;
import ir.GlobalVar;
import ir.constant.*;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.visitor.ConstantVisitor;
import utils.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 负责 LIR -> 汇编文本 的处理
 */
public class ToAsmManager {
    private final ArmModule module;
    private final AsmBuilder asm = new AsmBuilder();
    private final InstToAsm instVisitor = new InstToAsm();
    private final InitConstToAsm initConstToAsmVisitor = new InitConstToAsm();

    public ToAsmManager(ArmModule module) {
        this.module = module;
    }

    /**
     * 生成汇编文本
     * @return 生成的汇编文本
     */
    public StringBuilder codeGenArm() {
        // 指示汇编器我们使用的是 ARMv7
        asm.directive("arch", "armv7ve");

        // 生成数据段

        // 生成全局变量的汇编
        // 全局变量就是名字对外部可见的存放初始化器的内存块
        for (final var entry : module.getGlobalVariables().entrySet()) {
            final var name = entry.getKey();
            final var init = entry.getValue().getInit();

            asm.directive("global", name);
            codeGenInit(name, init);
        }

        // 生成存放局部变量的初始化器的内存块
        // 获得全局变量的初始化器, 防止重复生成了全局变量
        final var initsForGlobalVar = module.getGlobalVariables().values().stream()
            .map(GlobalVar::getInit).collect(Collectors.toUnmodifiableSet());
        for (final var entry : module.getArrayConstants().entrySet()) {
            final var name = entry.getKey();
            final var init = entry.getValue();

            if (!initsForGlobalVar.contains(init)) {
                codeGenInit(name, init);
            }
        }

        asm.newline();

        // 生成代码段
        asm.directive("text");

        for (final var func : module.getFunctions()) {
            // 每个函数都是外部可见的
            asm.directive("global", func.getName());

            // 生成函数的起始块
            // 这个块中的代码将负责开栈和保存 callee-save 的寄存器
            asm.block(func.getName());
            codeGenRegPushAtFuncBegin(func);
            codeGenStackAllocAtFuncBegin(func);

            placeLiteralPool(func);

            for (final var block : func) {
                // 对函数内每个基本块生成汇编文本
                asm.block(block);
                block.forEach(instVisitor::visit);
            }
        }

        return asm.getBuilder();
    }

    /**
     * 在合适且必要的地方插入 ArmInstLiteralPoolPlacement 指令, 以便在后面的汇编生成过程中插入 LiteralPool <br>
     *
     * ARMv7 指令是定长 32 位的, 所以放不下完整的 32 位立即数. 哪怕是 mov 指令, 也只有 16 位的地方可以用来放立即数. 为了能把一个 32 位
     * 的立即数放到寄存器里, ARM 创造了一个叫 LiteralPool 的概念. 它是一小块嵌入到 text 段里的内存空间, 存着各个指令放不下的字面量
     * <br>
     * 通过使用 {@code ldr <reg>, =<const>} 的格式, 汇编器会把 const 放到 LiteralPool 里, 然后把这条指令变成
     * {@code ldr <reg>, [pc, <offset>]}, 也就是一个相对于 pc 带点偏移的间接取址 load.
     * <br>
     * 但是, ldr 也只有 32 位长, 所以这个 offset 只能是一个小整数. 也就是说, 如果我们用了这种形式的指令, 我们就必须保证
     * 在距离这条指令 256 条指令的范围之内, 有一个 LiteralPool 的存放点. LiteralPool 的存放点一般位于无条件跳转之后, 下一个块开始之前,
     * 以避免 LiteralPool 被当作指令执行.
     * <br>
     * 所以如果块内有使用了这种形式的 ldr 指令, 必须保证在 256 条汇编指令的范围之内有一个无条件跳转, 要不然没地方放
     * LiteralPool, 汇编器就会报错. 我们通过插入 ArmInstLiteralPoolPlacement 的方式, 来表示 "这里需要有一个地方来放 LiteralPool",
     * 在后面的汇编文本生成中, 这条指令会被翻译成一个无条件跳转到下一个指令的块去的指令, 然后在两个块之间就可以放 LiteralPool 了
     * @param func 待调整的函数
     * @link https://developer.arm.com/documentation/dui0473/m/dom1359731147760
     */
    private void placeLiteralPool(ArmFunction func) {
        // 目前我们的实现里只有浮点数 load (vldr) 会需要 LiteralPool, 所以我们检测这个东西
        boolean haveLoadFImm = false;
        int offset = 0;
        int cnt = 0;
        for (final var block : func) {
            for (final var inst : block) {
                if (inst.needLtorg()) {
                    haveLoadFImm = true;
                }
                if (inst.haveLtorg()) {
                    haveLoadFImm = false;
                    offset = 0;
                }
                if (haveLoadFImm) {
                    offset += estimator.visit(inst);
                }
                // 预留一些余量
                if (offset > 250) {
                    final var pool = new ArmInstLiteralPoolPlacement(func.getName() + "_ltorg_" + cnt++);
                    inst.insertAfterCO(pool);
                    haveLoadFImm = false;
                    offset = 0;
                }
            }
        }
    }

    // TODO: 想办法把这个估计跟汇编文本生成整合一下
    private final EstimateTranslatedArmInst estimator = new EstimateTranslatedArmInst();
    private static class EstimateTranslatedArmInst implements ArmInstVisitor<Integer> {
        public Integer visitArmInstBinary(ArmInstBinary inst) { return 1; }
        public Integer visitArmInstBranch(ArmInstBranch inst) { return inst.getCond().isAny() ? 2 : 1; }
        public Integer visitArmInstCall(ArmInstCall inst) { return 1; }
        public Integer visitArmInstCmp(ArmInstCmp inst) { return inst.getLhs().isFloat() || inst.getRhs().isFloat() ? 2 : 1; }
        public Integer visitArmInstFloatToInt(ArmInstFloatToInt inst) { return 1; }
        public Integer visitArmInstIntToFloat(ArmInstIntToFloat inst) { return 1; }
        public Integer visitArmInstLoad(ArmInstLoad inst) { return inst.getAddr() instanceof Addr ? 2 : 1; }
        public Integer visitArmInstLiteralPoolPlacement(ArmInstLiteralPoolPlacement inst) { return 3; }
        public Integer visitArmInstMove(ArmInstMove inst) {
            final var src = inst.getSrc();
            if (src instanceof IImm iImm) {
                final var imm = iImm.getImm();
                return CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm) ? 1 : 2;
            } else {
                return src instanceof Addr ? 2 : 1;
            }
        }
        public Integer visitArmInstParamLoad(ArmInstParamLoad inst) { return 1; }
        public Integer visitArmInstReturn(ArmInstReturn inst) { return 7; }
        public Integer visitArmInstStackAddr(ArmInstStackAddr inst) {
            return CodeGenManager.checkEncodeImm(Math.abs(inst.getOffset().getImm())) ? 1 : 3;
        }
        public Integer visitArmInstStackLoad(ArmInstStackLoad inst) { return 1; }
        public Integer visitArmInstStackStore(ArmInstStackStore inst) { return 1; }
        public Integer visitArmInstStore(ArmInstStore inst) { return 1; }
        public Integer visitArmInstTernary(ArmInstTernary inst) { return 1; }
        public Integer visitArmInstUnary(ArmInstUnary inst) { return 1; }
    }

    //================================== 栈空间调整和寄存器存储/恢复部分 ==================================//

    private void codeGenStackAllocAtFuncBegin(ArmFunction func) {
        // 开栈是减
        genStackOpAroundFunc(func, "sub", ArmCondType.Any);
    }

    private void codeGenStackFreeAtFuncEnd(ArmCondType cond, ArmFunction func) {
        // 关栈是加, 因为这是在 return 时候生成的, 而 return 可能带条件, 所以要多传一个条件参数
        genStackOpAroundFunc(func, "add", cond);
    }

    private void genStackOpAroundFunc(ArmFunction func, String opForSP, ArmCondType cond) {
        final var stackSize = func.getFinalStackSize();
        if (stackSize > 0) {
            // 检查栈大小是否可以被编码为 add/sub 指令的立即数
            // 如果可以被编码, 就直接生成一条 add/sub 指令来调整 SP
            if (CodeGenManager.checkEncodeImm(stackSize)) {
                asm.instruction(opForSP).cond(cond)
                    .literal("sp")
                    .literal("sp")
                    .literal(stackSize)
                    .end();
            } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                final var negOpForSP = switch (opForSP) {
                    case "add" -> "sub";
                    case "sub" -> "add";
                    default -> throw new RuntimeException("Unsupported op: " + opForSP);
                };

                asm.instruction(negOpForSP).cond(cond)
                    .literal("sp")
                    .literal("sp")
                    .literal(stackSize)
                    .end();
            } else {
                // 否则就要想办法把这个立即数放进寄存器中, 再使用寄存器作为 add/sub 指令的参数
                // 因为这个立即数有可能能放进 mov 指令里, 也有可能放不进, 我们生成一条 ArmInstMove, 复用它的汇编生成逻辑
                final var move = new ArmInstMove(IPhyReg.R(4), new IImm(stackSize));
                instVisitor.visitArmInstMove(move);
                asm.instruction(opForSP).cond(cond)
                    .literal("sp")
                    .literal("sp")
                    .literal("r4")
                    .end();
            }
        }
    }

    private void codeGenRegPushAtFuncBegin(ArmFunction func) {
        final var usedRegs = spillUsedPhyRegsForPushOrPop(func);

        // 保存寄存器时是先 iRegs, 再 fRegsFront, 再 fRegBack
        // 恢复寄存器时应该顺序反过来
        if (!usedRegs.iRegs.isEmpty()) {
            asm.instruction("push")
                .group(usedRegs.iRegs.toArray(Operand[]::new))
                .end();
        }

        if (!usedRegs.fRegsFront.isEmpty()) {
            asm.instruction("vpush")
                .group(usedRegs.fRegsFront.toArray(Operand[]::new))
                .end();
        }

        if (!usedRegs.fRegBack.isEmpty()) {
            asm.instruction("vpush")
                .group(usedRegs.fRegBack.toArray(Operand[]::new))
                .end();
        }
    }

    private void codeGenRegPopAtFuncEnd(ArmCondType cond, ArmFunction func) {
        final var usedRegs = spillUsedPhyRegsForPushOrPop(func);

        // 恢复寄存器时是先 fRegBack, 再 fRegFront, 再 iRegs
        // 保存寄存器时应该顺序反过来
        if (!usedRegs.fRegBack.isEmpty()) {
            asm.instruction("vpop").cond(cond)
                .group(usedRegs.fRegBack.toArray(Operand[]::new))
                .end();
        }

        if (!usedRegs.fRegsFront.isEmpty()) {
            asm.instruction("vpop").cond(cond)
                .group(usedRegs.fRegsFront.toArray(Operand[]::new))
                .end();
        }

        if (!usedRegs.iRegs.isEmpty()) {
            // 如果有保存 lr 的话, 直接把 lr 的值恢复到 pc 里去, 省条 bx lr 指令
            // 可以看 InstToAsm#visitArmInstReturn 里的对应处理
            // FIXME: 这样 caller 会不会得到一个保存着任意值的 LR 寄存器?
            usedRegs.iRegs.replaceAll(reg -> reg.equals(IPhyReg.LR) ? IPhyReg.PC : reg);
            asm.instruction("pop").cond(cond)
                .group(usedRegs.iRegs.toArray(Operand[]::new))
                .end();
        }
    }

    // 因为一条 pop 伪指令最多只能放 16 个参数, 而浮点寄存器有 32 个, 所以要拆分一下
    private static final int MAX_REG_IN_ONE_POP = 16;
    record UsedPhyRegs(List<IPhyReg> iRegs, List<FPhyReg> fRegsFront, List<FPhyReg> fRegBack) {}
    private UsedPhyRegs spillUsedPhyRegsForPushOrPop(ArmFunction func) {
        final var usedIPhyReg = func.getIUsedRegs();
        final var usedFPhyReg = func.getFUsedRegs();

        final List<FPhyReg> usedFPhyRegFront;
        final List<FPhyReg> usedFPhyRegBack;

        if (usedFPhyReg.size() <= MAX_REG_IN_ONE_POP) {
            // 放不满一条指令的话, 直接全放一条指令里就可以了
            usedFPhyRegFront = usedFPhyReg;
            usedFPhyRegBack = List.of();
        } else {
            // 否则先放满第一个, 剩下的再放到第二个去
            usedFPhyRegFront = usedFPhyReg.subList(0, MAX_REG_IN_ONE_POP + 1);
            usedFPhyRegBack = usedFPhyReg.subList(MAX_REG_IN_ONE_POP + 1, usedFPhyReg.size());
        }

        return new UsedPhyRegs(usedIPhyReg, usedFPhyRegFront, usedFPhyRegBack);
    }

    //================================== 数据段生成部分 (data + bss) ==================================//

    private void codeGenInit(String name, Constant init) {
        if (init instanceof ZeroArrayConst) {
            // 把纯零的值放到 bss 段
            asm.directive("bss");
            asm.directive("align", "4");
        } else {
            // 把有数据的值放到 data 段
            asm.directive("data");
            asm.directive("align", "4");
        }

        asm.block(name);
        initConstToAsmVisitor.visit(init);
        asm.newline();
    }

    /**
     * 实现对常量的汇编生成. <br>
     * 特别地, 它负责对数组里连续的值相同的数据生成 .fill 指令而不是多个相同的 .word 指令, 以压缩生成的汇编文本的长度
     */
    private class InitConstToAsm implements ConstantVisitor<Void> {
        // 单个的数据直接使用 .word <数据> 生成即可
        @Override
        public Void visitIntConst(IntConst constant) {
            asm.indent().directive("word", constant.toString());
            return null;
        }

        @Override
        public Void visitFloatConst(FloatConst constant) {
            asm.indent().directive("word", constant.toString());
            return null;
        }

        @Override
        public Void visitArrayConst(ArrayConst constant) {
            if (constant instanceof ZeroArrayConst) {
                // 纯零数组使用 .zero <长度> 来生成
                final var text = String.valueOf(constant.getType().getSize());
                asm.indent().directive("zero", text);
            } else {
                final var elmType = constant.getType().getElementType();
                if (elmType.isArray()) {
                    // 如果子元素还是数组, 就当数组递归访问下去
                    constant.getRawElements().forEach(this::visit);
                } else {
                    // 否则, 如果自己是一维数组了, 就开始生成具体的每一个数据的生成指令
                    // 在生成过程中检测连续的相同元素, 插入 .fill
                    genScalarElementsCompressed(constant);
                }
            }
            return null;
        }

        private void genScalarElementsCompressed(ArrayConst arr) {
            // 使用两个变量来记录遇到了多少个相同的元素和元素的值
            // 随后在遇到不同的元素的时候根据我们的记录生成指令

            int sameElmCnt = 0;
            Constant lastElm = null;

            for (final var elm : arr.getRawElements()) {
                if (lastElm != null && lastElm.equals(elm)) {
                    sameElmCnt++;
                } else {
                    // 如果遇到了不同的元素, 就生成之前记录的 lastElm
                    // 然后切换 lastElm, 重置计数
                    genContinuousElements(sameElmCnt, lastElm);
                    sameElmCnt = 1;
                    lastElm = elm;
                }
            }

            // 最后会剩下最后一次遍历没有找到不同元素的 (因为遍历已经结束了)
            // 所以需要对剩下的 lastElm 再生成一次
            genContinuousElements(sameElmCnt, lastElm);
        }

        private void genContinuousElements(int sameElmCnt, Constant elm) {
            if (sameElmCnt == 1) {
                // 如果只有一个相同的元素, 就直接按类型放置 .word
                visit(elm);
            } else if (sameElmCnt > 1) {
                if (elm.isZero()) {
                    // 对于连续的 0, 放置 .zero
                    final var text = String.valueOf(4 * sameElmCnt);
                    asm.directive("zero", text);
                } else {
                    // 对于连续的其它值, 放置 .fill
                    asm.directive("fill", String.valueOf(sameElmCnt), "4", elm.toString());
                }
            }
        }

        @Override
        public Void visitBoolConst(BoolConst constant) {
            Log.ensure(false, "bool constant should not appear in init");
            return null;
        }
    }

    //================================== 指令生成部分 ==================================//

    private class InstToAsm implements ArmInstVisitor<Void> {
        @Override
        public Void visitArmInstBinary(ArmInstBinary inst) {
            final var op = binaryMap.get(inst.getKind());
            final var suffix = inst.getDst().isFloat() ? ".f32" : "";
            asm.instruction(op).cond(inst).add(suffix)
                .operand(inst.getDst())
                .operand(inst.getLhs())
                .operand(inst.getRhs(), inst.getShift())
                .end();
            return null;
        }

        @Override
        public Void visitArmInstBranch(ArmInstBranch inst) {
            asm.instruction("b").cond(inst)
                .label(inst.getTargetBlock())
                .end();

            // 无条件跳转后面可以放 LiteralPool
            // 不管我们需不需要都可以放, 反正放多了不亏, 不放白不放
            if (inst.getCond().isAny()) {
                asm.directive("ltorg");
            }
            return null;
        }

        @Override
        public Void visitArmInstCall(ArmInstCall inst) {
            asm.instruction("bl").cond(inst)
                .literal(inst.getFuncName())
                .end();
            return null;
        }

        @Override
        public Void visitArmInstCmp(ArmInstCmp inst) {
            final var lhs = inst.getLhs();
            final var rhs = inst.getRhs();

            final String op;
            if (lhs.isFloat() || rhs.isFloat()) {
                op = "vcmp.f32";
            } else if (inst.isCmn()) {
                op = "cmn";
            } else {
                op = "cmp";
            }

            asm.instruction(op)
                .operand(lhs)
                .operand(rhs)
                .end();

            // 对于浮点比较指令, 它的状态更新是更新 FPSCR 寄存器而不是更新 APSR (也就是常说的 CSPR) 寄存器的
            // 要使用 vmrs 指令把 FPSCR 的状态转移到 APSR 去, 这样后面依赖条件执行的语句才能正常执行
            if (lhs.isFloat() || rhs.isFloat()) {
                asm.instruction("vmrs")
                    .literal("APSR_nzcv")
                    .literal("fpscr")
                    .end();
            }

            return null;
        }

        @Override
        public Void visitArmInstFloatToInt(ArmInstFloatToInt inst) {
            asm.instruction("vcvt").cond(inst).add(".s32.f32")
                .operand(inst.getDst())
                .operand(inst.getSrc())
                .end();
            return null;
        }

        @Override
        public Void visitArmInstIntToFloat(ArmInstIntToFloat inst) {
            asm.instruction("vcvt").cond(inst).add(".f32.s32")
                .operand(inst.getDst())
                .operand(inst.getSrc())
                .end();
            return null;
        }

        @Override
        public Void visitArmInstLoad(ArmInstLoad inst) {
            final var dst = inst.getDst();
            final var addr = inst.getAddr();

            if (addr instanceof Addr) {
                Log.ensure(!dst.isFloat(), "load addr into vfp");
                asm.instruction("movw").cond(inst)
                    .operand(dst)
                    .literal(":lower16:" + addr)
                    .end();
                asm.instruction("movt").cond(inst)
                    .operand(dst)
                    .literal(":upper16:" + addr)
                    .end();

            } else {
                final var offset = inst.getOffset();
                if (!offset.equals(new IImm(0)) && inst.getShift() != null) {
                    Log.ensure(offset.isReg(), "offset must be reg when having shift");
                }

                final var op = dst.isFloat() ? "vldr" : "ldr";
                asm.instruction(op).cond(inst)
                    .operand(dst)
                    .indirect(addr, offset, inst.getShift())
                    .end();
            }
            return null;
        }

        @Override
        public Void visitArmInstLiteralPoolPlacement(ArmInstLiteralPoolPlacement inst) {
            asm.instruction("b").literal(inst.getLabel()).end();
            asm.directive("ltorg");
            asm.block(inst.getLabel());
            return null;
        }

        @Override
        public Void visitArmInstMove(ArmInstMove inst) {
            final var dst = inst.getDst();
            final var src = inst.getSrc();

            final var isVector = dst.isFloat() || src.isFloat() ? "v" : "";

            if (src instanceof IImm) {
                int imm = ((IImm) src).getImm();
                // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-immediate-values-using-mov-and-mvn?lang=en
                if (CodeGenManager.checkEncodeImm(~imm)) {
                    asm.instruction(isVector + "mvn").cond(inst)
                        .operand(dst)
                        .literal(~imm)
                        .end();

                } else if (CodeGenManager.checkEncodeImm(imm)) {
                    asm.instruction(isVector + "mov").cond(inst)
                        .operand(dst)
                        .literal(imm)
                        .end();

                } else {
                    // MOVW 把 16 位立即数放到寄存器的底16位，高16位清0
                    // MOVT 把 16 位立即数放到寄存器的高16位，低16位不影响
                    final var high = imm >>> 16;
                    final var low = (imm << 16) >>> 16;

                    asm.instruction("movw").cond(inst)
                        .operand(dst)
                        .literal(low)
                        .end();

                    if (high != 0) {
                        asm.instruction("movt").cond(inst)
                            .operand(dst)
                            .literal(high)
                            .end();
                    }
                }
            } else if (src instanceof FImm fImm) {
                // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-32-bit-immediate-values-to-a-register-using-ldr-rd---const?lang=en
                // VLDR Rn =Const
                asm.instruction("vldr").cond(inst)
                    .operand(dst)
                    .literal("=" + fImm.toHexString())
                    .end();

            } else if (src instanceof Addr) {
                Log.ensure(!dst.isFloat(), "can not load addr into vfp");
                asm.instruction("movw").cond(inst)
                    .operand(dst)
                    .literal(":lower16:" + src)
                    .end();

                asm.instruction("movt").cond(inst)
                    .operand(dst)
                    .literal(":upper16:" + src)
                    .end();

            } else {
                asm.instruction(isVector + "mov").cond(inst)
                    .operand(dst)
                    .operand(src, inst.getShift())
                    .end();
            }

            return null;
        }

        @Override
        public Void visitArmInstParamLoad(ArmInstParamLoad inst) {
            final var dst = inst.getDst();
            final var addr = inst.getAddr();
            final var offset = inst.getTrueOffset();
            Log.ensure(offset != null, "true offset must not be null"); assert offset != null;

            final var isFloat = dst.isFloat();
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), isFloat), "LoadParam offset illegal");

            final var op = isFloat ? "vldr" : "ldr";
            asm.instruction(op).cond(inst)
                .operand(dst)
                .indirect(addr, offset)
                .end();

            return null;
        }

        @Override
        public Void visitArmInstReturn(ArmInstReturn inst) {
            final var func = inst.getParent().getParent();
            final var cond = inst.getCond();

            codeGenStackFreeAtFuncEnd(cond, func);
            codeGenRegPopAtFuncEnd(cond, func);

            // 因为在恢复寄存器的时候, 如果检测到有保存 lr 的话, 会直接把 lr 恢复到 pc 去
            // 所以如果保存着的寄存器里有 lr 的话, 就不用生成最后一条 bx lr 了
            final var useLR = func.getIUsedRegs().contains(IPhyReg.LR);
            if (!useLR) {
                asm.instruction("bx").cond(inst)
                    .literal("lr")
                    .end();
            }

            // 只有无条件返回 (无条件跳转) 后面才能放 LiteralPool
            // 要不然就有可能意外把 LiteralPool 里的数据当指令执行掉
            if (cond.isAny()) {
                asm.directive("ltorg");
            }

            return null;
        }

        @Override
        public Void visitArmInstStackAddr(ArmInstStackAddr inst) {
            final var dst = inst.getDst();
            final var src = inst.getSrc();
            final var offset = Objects.requireNonNullElse(inst.getTrueOffset(), inst.getOffset());

            final var imm = Math.abs(offset.getImm());
            if (!inst.isCAlloc()) {
                Log.ensure((imm % 1024) == 0, "offset must be %1024 ==0");
            }

            final var op = offset.getImm() < 0 ? "sub" : "add";
            if (CodeGenManager.checkEncodeImm(imm)) {
                asm.instruction(op).cond(inst)
                    .operand(dst)
                    .operand(src)
                    .literal(imm)
                    .end();
            } else {
                final var move = new ArmInstMove(dst, new IImm(imm), inst.getCond());
                visitArmInstMove(move);
                asm.instruction(op).cond(inst)
                    .operand(dst)
                    .operand(src)
                    .operand(dst)
                    .end();
            }

            return null;
        }

        @Override
        public Void visitArmInstStackLoad(ArmInstStackLoad inst) {
            final var dst = inst.getDst();
            final var addr = inst.getAddr();
            final var offset = inst.getTrueOffset();
            Log.ensure(offset != null, "true offset must not be null"); assert offset != null;

            final var isFloat = dst.isFloat();
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), isFloat), "Load offset illegal");

            final var op = isFloat ? "vldr" : "ldr";
            asm.instruction(op).cond(inst)
                .operand(dst)
                .indirect(addr, offset)
                .end();

            return null;
        }

        @Override
        public Void visitArmInstStackStore(ArmInstStackStore inst) {
            final var dst = inst.getDst();
            final var addr = inst.getAddr();
            final var offset = inst.getTrueOffset();
            Log.ensure(offset != null, "true offset must not be null"); assert offset != null;

            final var isFloat = dst.isFloat();
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), isFloat), "Store offset illegal");

            final var op = isFloat ? "vstr" : "str";
            asm.instruction(op).cond(inst)
                .operand(dst)
                .indirect(addr, offset)
                .end();

            return null;
        }

        @Override
        public Void visitArmInstStore(ArmInstStore inst) {
            final var src = inst.getSrc();
            final var addr = inst.getAddr();
            final var offset = inst.getOffset();
            final var shift = inst.getShift();

            Log.ensure(!(addr instanceof Addr), "str a actual addr");
            Log.ensure(!(shift != null && !offset.isReg()), "offset must be reg when have a shift");

            final var op = src.isFloat() ? "vstr" : "str";
            asm.instruction(op).cond(inst)
                .operand(src)
                .indirect(addr, offset, inst.getShift())
                .end();
            return null;
        }

        @Override
        public Void visitArmInstTernary(ArmInstTernary inst) {
            final var op = ternaryMap.get(inst.getKind());
            asm.instruction(op).cond(inst)
                .operand(inst.getDst())
                .operand(inst.getOp1())
                .operand(inst.getOp2())
                .operand(inst.getOp3())
                .end();

            return null;
        }

        @Override
        public Void visitArmInstUnary(ArmInstUnary inst) {
            final var dst = inst.getDst();
            final var src = inst.getSrc();

            final var isFloat = dst.isFloat() || src.isFloat();
            final var op = isFloat ? "vneg" : "neg";
            final var suffix = isFloat ? ".f32" : "";

            asm.instruction(op).cond(inst).add(suffix)
                .operand(dst)
                .operand(src)
                .end();

            return null;
        }

        private static final Map<ArmInstKind, String> binaryMap = new HashMap<>() {
            {
                put(ArmInstKind.IAdd, "add");
                put(ArmInstKind.ISub, "sub");
                put(ArmInstKind.IRsb, "rsb");
                put(ArmInstKind.IMul, "mul");
                put(ArmInstKind.IDiv, "sdiv");
                put(ArmInstKind.ILMul, "smmul"); // smmul Rd Rm Rs : Rd = (Rm * Rs)[63:32]
                put(ArmInstKind.FAdd, "vadd");
                put(ArmInstKind.FSub, "vsub");
                put(ArmInstKind.FMul, "vmul");
                put(ArmInstKind.FDiv, "vdiv");
                put(ArmInstKind.Bic, "bic");
            }
        };

        private static final Map<ArmInstKind, String> ternaryMap = new HashMap<>() {
            {
                put(ArmInstKind.IMulAdd, "mla");
                put(ArmInstKind.IMulSub, "mls");
                put(ArmInstKind.ILMulAdd, "smmla");
                put(ArmInstKind.ILMulSub, "smmls");
            }
        };
    }
}