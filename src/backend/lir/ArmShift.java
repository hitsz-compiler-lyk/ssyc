package backend.lir;

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

    private final int imm;
    private final ShiftType type;

    public ArmShift(ShiftType type, int imm) {
        this.type = type;
        this.imm = imm;
    }

    public boolean isNoPrint() {
        return type == ShiftType.None || imm == 0 && type != ShiftType.Rrx;
    }

    @Override
    public String toString() {
        if (isNoPrint()) {
            return "";
        }
        if (type == ShiftType.Rrx) {
            return type.toString();
        }
        String ret = ",\t" + type.toString();
        ret += "\t#" + imm;
        return ret;
    }
}