package backend.arm;

import backend.operand.Operand;

public class ArmShift {
    public enum ShiftType {
        None,
        Asr, // arithmetic right
        Lsl, // logic left
        Lsr, // logic right
        Ror, // rotate right
        Rrx; // rotate right one bit with extend

        @Override
        public String toString() {
            return this == ShiftType.None ? "" : super.toString().toLowerCase();
        }
    }

    private int imm = 0;
    private Operand op = null;
    private ShiftType type = ShiftType.None;

    public ArmShift(ShiftType type, int imm) {
        this.type = type;
        this.imm = imm;
    }

    public ArmShift(ShiftType type, Operand op) {
        this.type = type;
        this.op = op;
    }

    public boolean isNoPrint() {
        return type == ShiftType.None || (imm == 0 && op == null && type != ShiftType.Rrx);
    }

    @Override
    public String toString() {
        if (type == ShiftType.None || (imm == 0 && op == null && type != ShiftType.Rrx)) {
            return "";
        }
        if (type == ShiftType.Rrx) {
            return type.toString();
        }
        String ret = ",\t" + type.toString();
        if (op == null) {
            ret += "\t#" + Integer.toString(imm);
        } else {
            ret += "\t" + op.print();
        }
        return ret;
    }
}