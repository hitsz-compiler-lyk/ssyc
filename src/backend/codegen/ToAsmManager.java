package backend.codegen;

import backend.lir.ArmBlock;
import backend.lir.ArmFunction;
import backend.lir.ArmModule;
import backend.lir.ArmShift;
import backend.lir.inst.*;
import backend.lir.inst.ArmInst.ArmCondType;
import backend.lir.inst.ArmInst.ArmInstKind;
import backend.lir.operand.*;
import backend.lir.visitor.ArmInstVisitor;
import ir.GlobalVar;
import ir.constant.*;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.visitor.ConstantVisitor;
import utils.Log;

import java.util.*;
import java.util.stream.Collectors;

public class ToAsmManager {
    private final ArmModule module;
    private final AsmBuilder asm;
    private final InstToAsm instVisitor = new InstToAsm();
    private final InitConstToAsm initConstToAsmVisitor = new InitConstToAsm();

    public ToAsmManager(ArmModule module) {
        this.module = module;
        this.asm = new AsmBuilder();
    }

    public StringBuilder codeGenArm() {
        asm.directive("arch", "armv7ve");

        final var initsForGlobalVar = module.getGlobalVariables().values().stream()
            .map(GlobalVar::getInit).collect(Collectors.toUnmodifiableSet());

        for (final var entry : module.getGlobalVariables().entrySet()) {
            final var name = entry.getKey();
            final var init = entry.getValue().getInit();

            asm.directive("global", name);
            codeGenInit(name, init);
        }

        for (final var entry : module.getArrayConstants().entrySet()) {
            final var name = entry.getKey();
            final var init = entry.getValue();

            if (!initsForGlobalVar.contains(init)) {
                codeGenInit(name, init);
            }
        }

        asm.newline();
        asm.directive("text");

        for (final var func : module.getFunctions()) {
            asm.directive("global", func.getName());
            asm.block(func.getName());

            codeGenRegPushAtFuncBegin(func);
            codeGenStackAllocAtFuncBegin(func);

            placeLiteralPool(func);
            for (final var block : func) {
                asm.block(block);
                block.forEach(instVisitor::visit);
            }
        }

        return asm.getBuilder();
    }

    private void codeGenStackAllocAtFuncBegin(ArmFunction func) {
        genStackOpAroundFunc(func, "sub", ArmCondType.Any);
    }

    private void codeGenStackFreeAtFuncEnd(ArmInstReturn inst, ArmFunction func) {
        genStackOpAroundFunc(func, "add", inst.getCond());
    }

    private void genStackOpAroundFunc(ArmFunction func, String opForSP, ArmCondType cond) {
        final var negOpForSP = switch (opForSP) {
            case "add" -> "sub";
            case "sub" -> "add";
            default -> throw new RuntimeException("Unsupported op: " + opForSP);
        };

        final var stackSize = func.getFinalstackSize();
        if (stackSize > 0) {
            if (CodeGenManager.checkEncodeImm(stackSize)) {
                asm.instruction(opForSP).cond(cond)
                    .literal("sp")
                    .literal("sp")
                    .literal(stackSize)
                    .end();
            } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                asm.instruction(negOpForSP).cond(cond)
                    .literal("sp")
                    .literal("sp")
                    .literal(stackSize)
                    .end();
            } else {
                var move = new ArmInstMove(IPhyReg.R(4), new IImm(stackSize));
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

    private void codeGenRegPopAtFuncEnd(ArmInstReturn inst, ArmFunction func) {
        final var usedRegs = spillUsedPhyRegsForPushOrPop(func);

        if (!usedRegs.fRegBack.isEmpty()) {
            asm.instruction("vpop").cond(inst)
                .group(usedRegs.fRegBack.toArray(Operand[]::new))
                .end();
        }

        if (!usedRegs.fRegsFront.isEmpty()) {
            asm.instruction("vpop").cond(inst)
                .group(usedRegs.fRegsFront.toArray(Operand[]::new))
                .end();
        }

        if (!usedRegs.iRegs.isEmpty()) {
            usedRegs.iRegs.replaceAll(reg -> reg.equals(IPhyReg.LR) ? IPhyReg.PC : reg);
            asm.instruction("pop").cond(inst)
                .group(usedRegs.iRegs.toArray(Operand[]::new))
                .end();
        }
    }

    record UsedPhyRegs(List<IPhyReg> iRegs, List<FPhyReg> fRegsFront, List<FPhyReg> fRegBack) {}
    private UsedPhyRegs spillUsedPhyRegsForPushOrPop(ArmFunction func) {
        final var usedIPhyReg = func.getiUsedRegs();
        final var usedFPhyReg = func.getfUsedRegs();

        final var maxRegInAPop = 16;
        final List<FPhyReg> usedFPhyRegFront;
        final List<FPhyReg> usedFPhyRegBack;
        if (usedFPhyReg.size() <= maxRegInAPop) {
            usedFPhyRegFront = usedFPhyReg;
            usedFPhyRegBack = List.of();
        } else {
            usedFPhyRegFront = usedFPhyReg.subList(0, maxRegInAPop + 1);
            usedFPhyRegBack = usedFPhyReg.subList(maxRegInAPop + 1, usedFPhyReg.size());
        }

        return new UsedPhyRegs(usedIPhyReg, usedFPhyRegFront, usedFPhyRegBack);
    }

    private void codeGenInit(String name, Constant init) {
        if (init instanceof ZeroArrayConst) {
            asm.directive("bss");
            asm.directive("align", "4");
        } else {
            asm.directive("data");
            asm.directive("align", "4");
        }

        asm.block(name);
        initConstToAsmVisitor.visit(init);
        asm.newline();
    }

    private void placeLiteralPool(ArmFunction func) {
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
                    offset += inst.getPrintCnt();
                }
                if (offset > 250) {
                    final var pool = new ArmInstLiteralPoolPlacement(func.getName() + "_ltorg_" + cnt++);
                    inst.insertAfterCO(pool);
                    haveLoadFImm = false;
                    offset = 0;
                }
            }
        }
    }

    private class InitConstToAsm implements ConstantVisitor<Void> {
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
                final var text = String.valueOf(constant.getType().getSize());
                asm.indent().directive("zero", text);
            } else {
                final var elmType = constant.getType().getElementType();
                if (elmType.isArray()) {
                    constant.getRawElements().forEach(this::visit);
                } else {
                    genScalarElementsCompressed(constant);
                }
            }
            return null;
        }

        private void genScalarElementsCompressed(ArrayConst arr) {
            int sameElmCnt = 0;
            Constant lastElm = null;

            for (final var elm : arr.getRawElements()) {
                if (lastElm != null && lastElm.equals(elm)) {
                    sameElmCnt++;
                } else {
                    genContinuousElements(sameElmCnt, lastElm);
                    sameElmCnt = 1;
                    lastElm = elm;
                }
            }

            genContinuousElements(sameElmCnt, lastElm);
        }

        private void genContinuousElements(int sameElmCnt, Constant elm) {
            if (sameElmCnt == 1) {
                visit(elm);
            } else if (sameElmCnt > 1) {
                if (elm.isZero()) {
                    final var text = String.valueOf(4 * sameElmCnt);
                    asm.directive("zero", text);
                } else {
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

            if (inst.getCond().equals(ArmCondType.Any)) {
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
        public Void visitArmInstLtorg(ArmInstLiteralPoolPlacement inst) {
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

                    asm.instruction(isVector + "movw").cond(inst)
                        .operand(dst)
                        .literal(low)
                        .end();

                    if (high != 0) {
                        asm.instruction(isVector + "movt").cond(inst)
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
            final var block = inst.getParent();
            final var func = block.getParent();

            codeGenStackFreeAtFuncEnd(inst, func);
            codeGenRegPopAtFuncEnd(inst, func);

            final var useLR = func.getiUsedRegs().contains(IPhyReg.LR);
            if (!useLR) {
                asm.instruction("bx").cond(inst)
                    .literal("lr")
                    .end();
            }

            if (inst.getCond().isAny()) {
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

class AsmBuilder {
    private enum State {
        FirstArg, RestArg, Normal
    }

    private static final String WORD_DELIMITER = "\t";
    private static final String INDENT = "\t";

    private final StringBuilder builder = new StringBuilder();
    private State state = State.Normal;
    private int blockInstCnt = 0;

    private void ensureState(State... allowStates) {
        for (final var s : allowStates) {
            if (s == state) {
                return;
            }
        }

        throw new RuntimeException("Disallowed state, except " + Set.of(allowStates) + ", in " + state);
    }

    public AsmBuilder instruction(String op) {
        ensureState(State.Normal);

        builder.append(INDENT);
        builder.append(op);

        state = State.FirstArg;
        return this;
    }

    public AsmBuilder cond(ArmInst inst) {
        return cond(inst.getCond());
    }

    public AsmBuilder cond(ArmCondType cond) {
        ensureState(State.FirstArg);
        if (cond.isAny()) {
            return this;
        } else {
            return add(cond.toString());
        }
    }

    private AsmBuilder argument(Runnable callback) {
        ensureState(State.FirstArg, State.RestArg);

        if (state != State.FirstArg) {
            builder.append(',');
        }
        builder.append(WORD_DELIMITER);
        callback.run();

        state = State.RestArg;
        return this;
    }

    public AsmBuilder operand(Operand op) {
        return operand(op, null);
    }

    public AsmBuilder operand(Operand op, ArmShift shift) {
        return argument(() -> {
            builder.append(op);
            if (shift != null) {
                builder.append(' ');
                builder.append(shift);
            }
        });
    }

    public AsmBuilder literal(String op) {
        return argument(() -> builder.append(op));
    }

    public AsmBuilder literal(int imm) {
        return argument(() -> {
            builder.append('#');
            builder.append(imm);
        });
    }

    public AsmBuilder label(ArmBlock block) {
        return argument(() -> builder.append(block.getLabel()));
    }

    public AsmBuilder indirect(Operand addr, Operand offset, ArmShift shift) {
        return argument(() -> {
            builder.append('[');
            builder.append(addr);

            if (!offset.equals(new IImm(0))) {
                builder.append(',');
                builder.append(offset);
            }

            if (shift != null) {
                builder.append(' ');
                builder.append(shift);
            }

            builder.append(']');
        });
    }

    public AsmBuilder indirect(Operand addr, Operand offset) {
        return indirect(addr, offset, null);
    }

    public AsmBuilder indirect(Operand addr) {
        return indirect(addr, new IImm(0), null);
    }

    public AsmBuilder group(Operand... operands) {
        final var texts = Arrays.stream(operands).map(Operand::toString).toArray(String[]::new);
        return argument(() -> {
            builder.append('{');
            join(", ", texts);
            builder.append('}');
        });
    }

    public void end() {
        builder.append('\n');

        state = State.Normal;
        blockInstCnt += 1;
    }

    public AsmBuilder block(ArmBlock block) {
        return block(block.getLabel());
    }

    public AsmBuilder block(String label) {
        ensureState(State.Normal);

        builder.append(label);
        builder.append(':');
        builder.append('\n');

        blockInstCnt = 0;
        return this;
    }

    public void directive(String directive, String... args) {
        ensureState(State.Normal);

        builder.append('.');
        builder.append(directive);
        builder.append(' ');
        join(", ", args);
        builder.append('\n');
    }

    public void newline() {
        builder.append('\n');
    }

    public AsmBuilder add(String text) {
        builder.append(text);
        return this;
    }

    public AsmBuilder indent() {
        builder.append(INDENT);
        return this;
    }

    private void join(String delimiter, String... items) {
        switch (items.length) {
            case 0 -> {}
            case 1 -> builder.append(items[0]);
            default -> {
                builder.append(items[0]);
                for (int i = 1; i < items.length; i++) {
                    builder.append(delimiter);
                    builder.append(items[i]);
                }
            }
        }
    }

    public int getBlockInstCnt() {
        return blockInstCnt;
    }

    public StringBuilder getBuilder() {
        return builder;
    }
}
