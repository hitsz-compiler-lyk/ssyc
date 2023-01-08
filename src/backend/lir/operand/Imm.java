package backend.lir.operand;

public abstract class Imm extends Operand {

    public Imm(OperandKind s) {
        super(s);
    }

    public abstract String toHexString();
}
