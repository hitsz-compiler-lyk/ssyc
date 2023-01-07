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
import ir.constant.ArrayConst;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.constant.FloatConst;
import ir.constant.IntConst;
import utils.Log;
import utils.StringUtils;

import java.util.*;

public class ToAsmManager {
    private final ArmModule module;
    private final StringBuilder asmBuilder;
    private final AsmBuilder asm;
    private final InstToAsm instVisitor = new InstToAsm();

    public ToAsmManager(ArmModule module) {
        this.module = module;
        this.asm = new AsmBuilder();
        this.asmBuilder = asm.getBuilder();
    }

    public StringBuilder codeGenArm()   {
        asmBuilder.append(".arch armv7ve\n");
        Set<ArrayConst> acSet = new HashSet<>();
        for (var entry : module.getGlobalVariables().entrySet()) {
            var key = entry.getKey();
            var val = entry.getValue().getInit();
            if (val instanceof ZeroArrayConst) {
                asmBuilder.append("\n.bss\n.align 4\n");
            } else {
                asmBuilder.append("\n.data\n.align 4\n");
            }
            asmBuilder.append(".global\t" + key + "\n" + key + ":\n");
            if (val instanceof IntConst) {
                asmBuilder.append(codeGenIntConst((IntConst) val));
            } else if (val instanceof FloatConst) {
                asmBuilder.append(codeGenFloatConst((FloatConst) val));
            } else if (val instanceof ArrayConst) {
                acSet.add((ArrayConst) val);
                asmBuilder.append(codeGenArrayConst((ArrayConst) val));
            }
            asmBuilder.append("\n");
        }
        for (var entry : module.getArrayConstants().entrySet()) {
            var key = entry.getKey();
            var val = entry.getValue();
            if (acSet.contains(val)) {
                continue;
            }
            if (val instanceof ZeroArrayConst) {
                asmBuilder.append("\n.bss\n.align 4\n");
            } else {
                asmBuilder.append("\n.data\n.align 4\n");
            }
            asmBuilder.append(key + ":\n");
            asmBuilder.append(codeGenArrayConst(val));
            asmBuilder.append("\n");
        }

        asmBuilder.append("\n.text\n");
        for (var func : module.getFunctions()) {
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
                    var move = new ArmInstMove(IPhyReg.R(4), new IImm(stackSize));
                    instVisitor.visitArmInstMove(move);
                    prologuePrint += "\tsub\tsp,\tsp,\tr4\n";
                }
            }
            asmBuilder.append("\n.global\t" + func.getName() + "\n" + func.getName() + ":\n");
            asmBuilder.append(prologuePrint);
            fixLtorg(func);
            for (var block : func.asElementView()) {
                asmBuilder.append(block.getLabel() + ":\n");
                for (var inst : block.asElementView()) {
                    instVisitor.visit(inst);
                }
            }
        }
        return asmBuilder;
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
                asm.ltorg();
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
        public Void visitArmInstLtorg(ArmInstLtorg inst) {
            asm.instruction("b").literal(inst.getLabel()).end();
            asm.ltorg();
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
            final var stackSize = func.getFinalstackSize();

            if (stackSize > 0) {
                if (CodeGenManager.checkEncodeImm(stackSize)) {
                    asm.instruction("add").cond(inst)
                        .literal("sp")
                        .literal("sp")
                        .literal(stackSize)
                        .end();
                } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                    asm.instruction("sub").cond(inst)
                        .literal("sp")
                        .literal("sp")
                        .literal(stackSize)
                        .end();
                } else {
                    final var move = new ArmInstMove(IPhyReg.R(4), new IImm(stackSize), inst.getCond());
                    visitArmInstMove(move);
                    asm.instruction("add").cond(inst)
                        .literal("sp")
                        .literal("sp")
                        .literal("r4")
                        .end();
                }
            }

            // TODO: clean up this
            var iuse = new StringBuilder();
            var useLR = false;
            var first = true;
            for (var reg : func.getiUsedRegs()) {
                if (!first) {
                    iuse.append(", ");
                }
                if (reg.equals(IPhyReg.LR)) {
                    iuse.append("pc");
                    useLR = true;
                } else {
                    iuse.append(reg.print());
                }
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

            if (fuse2.length() != 0) {
                asm.instruction("vpop").cond(inst)
                    .literal("{" + fuse2 + "}")
                    .end();
            }

            if (fuse1.length() != 0) {
                asm.instruction("vpop").cond(inst)
                    .literal("{" + fuse1 + "}")
                    .end();
            }

            if (!func.getiUsedRegs().isEmpty()) {
                asm.instruction("pop").cond(inst)
                    .literal("{" + iuse + "}")
                    .end();
            }

            if (!useLR) {
                asm.instruction("bx").cond(inst)
                    .literal("lr")
                    .end();
            }

            if (inst.getCond().isAny()) {
                asm.ltorg();
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
        ensureState(State.FirstArg);
        if (inst.getCond().isAny()) {
            return this;
        } else {
            return add(inst.getCond().toString());
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
        return argument(() -> {
            builder.append('{');
            switch (operands.length) {
                case 0 -> {}
                case 1 -> builder.append(operands[0]);
                default -> {
                    builder.append(operands[0]);
                    for (int i = 1; i < operands.length; i++) {
                        builder.append(',');
                        builder.append(operands[i]);
                    }
                }
            }
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

    public void ltorg() {
        ensureState(State.Normal);
        builder.append(".ltorg\n");
    }

    public AsmBuilder add(String text) {
        builder.append(text);
        return this;
    }

    public int getBlockInstCnt() {
        return blockInstCnt;
    }

    public StringBuilder getBuilder() {
        return builder;
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
