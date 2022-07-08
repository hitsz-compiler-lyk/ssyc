package top.origami404.ssyc.backend.operand;

import top.origami404.ssyc.utils.Log;

public class Imm extends Operand {

    public Imm(opType s) {
        super(s);
    }

    @Override
    public String toString() {
        Log.ensure(false);
        return "";
    }

    public void setLabel(String label) {
        super.setLabel(label);
    }

    public String getLabel() {
        Log.ensure(false);
        return "";
    }

    public String toHexString() {
        Log.ensure(false);
        return "";
    }
}
