package backend.codegen;

import backend.lir.inst.*;
import backend.lir.inst.ArmInst.ArmCondType;
import backend.lir.inst.ArmInst.ArmInstKind;
import backend.lir.operand.Addr;
import backend.lir.operand.FImm;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.visitor.ArmInstVisitor;
import utils.Log;
import utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class InstToAsm implements ArmInstVisitor<String> {
    @Override
    public String visitArmInstBinary(ArmInstBinary inst) {
        String op = binaryMap.get(inst.getKind());
        var dst = inst.getDst();
        var lhs = inst.getLhs();
        var rhs = inst.getRhs();
        String fprint = dst.isFloat() ? ".f32" : "";
        Log.ensure(op != null);
        if (inst.getShift() != null) {
            return "\t" + op + inst.getCond().toString() + fprint + "\t" + dst.print() + ",\t" + lhs.print() + ",\t"
                + rhs.print() + inst.getShift().toString() + "\n";
        } else {
            return "\t" + op + inst.getCond().toString() + fprint + "\t" + dst.print() + ",\t" + lhs.print() + ",\t"
                + rhs.print() + "\n";
        }
    }

    @Override
    public String visitArmInstBranch(ArmInstBranch inst) {
        String ret = "\t" + "b" + inst.getCond().toString() + "\t" + inst.getTargetBlock().getLabel() + "\n";
        if (inst.getCond().equals(ArmCondType.Any)) {
            ret += ".ltorg\n";
        }
        return ret;
    }

    @Override
    public String visitArmInstCall(ArmInstCall inst) {
        if (!StringUtils.isEmpty(inst.getFuncName())) {
            return "\t" + "bl" + inst.getCond().toString() + "\t" + inst.getFuncName() + "\n";
        }
        return "\t" + "bl" + inst.getCond().toString() + "\t" + inst.getFunc().getName() + "\n";
    }

    @Override
    public String visitArmInstCmp(ArmInstCmp inst) {
        var lhs = inst.getLhs();
        var rhs = inst.getRhs();
        var op = "cmp";
        var ret = "";
        if (lhs.isFloat() || rhs.isFloat()) {
            op = "vcmp.f32";
        } else if (inst.isCmn()) {
            op = "cmn";
        }
        ret += "\t" + op + "\t" + lhs.print() + ",\t" + rhs.print() + "\n";
        if (lhs.isFloat() || rhs.isFloat()) {
            ret += "\tvmrs\tAPSR_nzcv,\tfpscr\n";
        }
        return ret;
    }

    @Override
    public String visitArmInstFloatToInt(ArmInstFloatToInt inst) {
        return "\t" + "vcvt" + inst.getCond().toString() + ".s32.f32" + "\t" +
            inst.getDst().print() + ",\t" + inst.getSrc().print() + "\n";
    }

    @Override
    public String visitArmInstIntToFloat(ArmInstIntToFloat inst) {
        return "\t" + "vcvt" + inst.getCond().toString() + ".f32.s32" + "\t" +
            inst.getDst().print() + ",\t" + inst.getSrc().print() + "\n";
    }

    @Override
    public String visitArmInstLoad(ArmInstLoad inst) {
        var dst = inst.getDst();
        var addr = inst.getAddr();
        var offset = inst.getOffset();

        var isVector = "";
        if (dst.isFloat()) {
            isVector = "v";
        }

        if (addr instanceof Addr) {
            Log.ensure(!dst.isFloat(), "load addr into vfp");
            return "\tmovw" + inst.getCond().toString() + "\t" + dst.print() + ",\t:lower16:" + addr.print() + "\n" +
                "\tmovt" + inst.getCond().toString() + "\t" + dst.print() + ",\t:upper16:" + addr.print() + "\n";
        } else if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "ldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print() + "]\n";
        } else if (inst.getShift() != null) {
            Log.ensure(offset.isReg(), "offset must be reg");
            return "\t" + isVector + "ldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print()
                + ",\t" + offset.print() + inst.getShift().toString() + "]\n";
        } else {
            return "\t" + isVector + "ldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print()
                + ",\t" + offset.print() + "]\n";
        }
    }

    @Override
    public String visitArmInstLtorg(ArmInstLtorg inst) {
        String ret = "\tb\t" + inst.getLabel() + "\n";
        ret += ".ltorg\n";
        ret += inst.getLabel() + ":\n";
        return ret;
    }

    @Override
    public String visitArmInstMove(ArmInstMove inst) {
        var dst = inst.getDst();
        var src = inst.getSrc();

        var isVector = "";
        if (dst.isFloat() || src.isFloat()) {
            isVector = "v";
        }

        if (src instanceof IImm) {
            int imm = ((IImm) src).getImm();
            // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-immediate-values-using-mov-and-mvn?lang=en
            if (CodeGenManager.checkEncodeImm(~imm)) {
                return "\t" + isVector + "mvn" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                    + Integer.toString(~imm) + "\n";
            } else if (CodeGenManager.checkEncodeImm(imm)) {
                return "\t" + isVector + "mov" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                    + Integer.toString(imm) + "\n";
            } else {
                // MOVW 把 16 位立即数放到寄存器的底16位，高16位清0
                // MOVT 把 16 位立即数放到寄存器的高16位，低16位不影响
                var high = imm >>> 16;
                var low = (imm << 16) >>> 16;
                String ret = "";
                ret += "\t" + isVector + "movw" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                    + Integer.toString(low) + "\n";
                if (high != 0) {
                    ret += "\t" + isVector + "movt" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                        + Integer.toString(high) + "\n";
                }
                return ret;
            }
        } else if (src instanceof FImm) {
            // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-32-bit-immediate-values-to-a-register-using-ldr-rd---const?lang=en
            // VLDR Rn =Const
            return "\t" + "vldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + "="
                + ((FImm) src).toHexString() + "\n";
        } else if (src instanceof Addr) {
            Log.ensure(!dst.isFloat(), "load addr into vfp");
            // return "\tldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t=" +
            // src.print() + "\n";
            return "\tmovw" + inst.getCond().toString() + "\t" + dst.print() + ",\t:lower16:" + src.print() + "\n" +
                "\tmovt" + inst.getCond().toString() + "\t" + dst.print() + ",\t:upper16:" + src.print() + "\n";
        } else if (inst.getShift() != null) {
            return "\t" + isVector + "mov" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + src.print()
                + inst.getShift().toString() + "\n";
        } else {
            return "\t" + isVector + "mov" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + "\n";
        }
    }

    @Override
    public String visitArmInstParamLoad(ArmInstParamLoad inst) {
        var dst = inst.getDst();
        var addr = inst.getAddr();
        var offset = inst.getTrueOffset();
        Log.ensure(offset != null, "true offset must not be null");

        var isVector = "";
        if (dst.isFloat()) {
            isVector = "v";
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), true), "LoadParam offset illegal");
        } else {
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), false), "LoadParam offset illegal");
        }

        if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "ldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print() + "]\n";
        }
        return "\t" + isVector + "ldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print()
            + ",\t" + offset.print() + "]\n";
    }

    @Override
    public String visitArmInstReturn(ArmInstReturn inst) {
        var block = inst.getParent();
        var func = block.getParent();
        var stackSize = func.getFinalstackSize();
        String ret = "";
        if (stackSize > 0) {
            if (CodeGenManager.checkEncodeImm(stackSize)) {
                ret += "\tadd" + inst.getCond().toString() + "\tsp,\tsp,\t#" + stackSize + "\n";
            } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                ret += "\tsub" + inst.getCond().toString() + "\tsp,\tsp,\t#" + stackSize + "\n";
            } else {
                var move = new ArmInstMove(IPhyReg.R(4), new IImm(stackSize));
                move.setCond(inst.getCond());
                ret += visitArmInstMove(move);
                ret += "\tadd" + inst.getCond().toString() + "\tsp,\tsp,\tr4\n";
            }
        }

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
            ret += "\tvpop" + inst.getCond().toString() + "\t{" + fuse2.toString() + "}\n";
        }

        if (fuse1.length() != 0) {
            ret += "\tvpop" + inst.getCond().toString() + "\t{" + fuse1.toString() + "}\n";
        }

        if (!func.getiUsedRegs().isEmpty()) {
            ret += "\tpop" + inst.getCond().toString() + "\t{" + iuse.toString() + "}\n";
        }

        if (!useLR) {
            ret += "\t" + "bx" + inst.getCond().toString() + "\t" + "lr" + "\n";
        }
        if (inst.getCond().equals(ArmCondType.Any)) {
            ret += ".ltorg\n";
        }
        return ret;
    }

    @Override
    public String visitArmInstStackAddr(ArmInstStackAddr inst) {
        var dst = inst.getDst();
        var src = inst.getSrc();
        var offset = inst.getOffset();
        if (inst.getTrueOffset() != null) {
            offset = inst.getTrueOffset();
        }
        int imm = Math.abs(offset.getImm());
        if (!inst.isCAlloc()) {
            Log.ensure((imm % 1024) == 0, "offset must be %1024 ==0");
        }
        String op = "add";
        if (offset.getImm() < 0) {
            op = "sub";
        }
        if (CodeGenManager.checkEncodeImm(imm)) {
            return "\t" + op + inst.getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + ",\t#"
                + imm + "\n";
        } else {
            var move = new ArmInstMove(dst, new IImm(imm));
            move.setCond(inst.getCond());
            return visitArmInstMove(move) +
                "\t" + op + inst.getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + ",\t" + dst.print()
                + "\n";
        }
    }

    @Override
    public String visitArmInstStackLoad(ArmInstStackLoad inst) {
        var dst = inst.getDst();
        var addr = inst.getAddr();
        var offset = inst.getTrueOffset();
        Log.ensure(offset != null, "true offset must not be null");

        var isVector = "";
        if (dst.isFloat()) {
            isVector = "v";
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), true), "LoadParam offset illegal");
        } else {
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), false), "LoadParam offset illegal");
        }

        if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "ldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print() + "]\n";
        }
        return "\t" + isVector + "ldr" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print()
            + ",\t" + offset.print() + "]\n";
    }

    @Override
    public String visitArmInstStackStore(ArmInstStackStore inst) {
        var dst = inst.getDst();
        var addr = inst.getAddr();
        var offset = inst.getTrueOffset();
        Log.ensure(offset != null, "true offset must not be null");

        var isVector = "";
        if (dst.isFloat()) {
            isVector = "v";
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), true), "LoadParam offset illegal");
        } else {
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), false), "LoadParam offset illegal");
        }

        if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "str" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print() + "]\n";
        }
        return "\t" + isVector + "str" + inst.getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print()
            + ",\t" + offset.print() + "]\n";
    }

    @Override
    public String visitArmInstStore(ArmInstStore inst) {
        var src = inst.getSrc();
        var addr = inst.getAddr();
        var offset = inst.getOffset();
        Log.ensure(!(addr instanceof Addr), "str a actual addr");

        var isVector = "";
        if (src.isFloat()) {
            isVector = "v";
        }
        if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "str" + inst.getCond().toString() + "\t" + src.print() + ",\t[" + addr.print() + "]\n";
        } else if (inst.getShift() != null) {
            Log.ensure(offset.isReg(), "offset must be reg");
            return "\t" + isVector + "str" + inst.getCond().toString() + "\t" + src.print() + ",\t[" + addr.print()
                + ",\t" + offset.print() + inst.getShift().toString() + "]\n";
        } else {
            return "\t" + isVector + "str" + inst.getCond().toString() + "\t" + src.print() + ",\t[" + addr.print()
                + ",\t" + offset.print() + "]\n";
        }
    }

    @Override
    public String visitArmInstTernary(ArmInstTernary inst) {
        String op = ternaryMap.get(inst.getKind());
        Log.ensure(op != null);
        String ret = "\t" + op + inst.getCond().toString() + "\t" + inst.getDst().print() + ",\t"
            + inst.getOp1().print() + ",\t" + inst.getOp2().print() + ",\t" + inst.getOp3().print() + "\n";
        return ret;
    }

    @Override
    public String visitArmInstUnary(ArmInstUnary inst) {
        var dst = inst.getDst();
        var src = inst.getSrc();

        if (inst.getKind() == ArmInstKind.INeg) {
            return "\t" + "neg" + inst.getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + "\n";
        } else if (inst.getKind() == ArmInstKind.FNeg) {
            return "\t" + "vneg" + inst.getCond().toString() + ".f32" + "\t" + dst.print() + ",\t" + src.print() + "\n";
        } else {
            Log.ensure(false);
            return "";
        }
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
