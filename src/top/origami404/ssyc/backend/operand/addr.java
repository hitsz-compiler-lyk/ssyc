package top.origami404.ssyc.backend.operand;

import top.origami404.ssyc.utils.Log;
import top.origami404.ssyc.utils.StringUtils;

public class addr extends Operand {
    private String label;
    private boolean isGlobal;
    private boolean isInt;
    private boolean isFloat;
    private int ival;
    private float fval;

    public addr(opType s) {
        super(s);
    }

    public addr(int ival) {
        super(opType.Addr);
        this.ival = ival;
        this.isGlobal = false;
        this.isInt = true;
    }

    public addr(int ival, boolean isGlobal) {
        super(opType.Addr);
        this.ival = ival;
        this.isGlobal = isGlobal;
        this.isInt = true;
    }

    public addr(int ival, String label, boolean isGlobal) {
        super(opType.Addr);
        this.ival = ival;
        this.label = label;
        this.isGlobal = isGlobal;
        this.isInt = true;
    }

    public addr(float fval) {
        super(opType.Addr);
        this.fval = fval;
        this.isGlobal = false;
        this.isFloat = true;
    }

    public addr(float fval, boolean isGlobal) {
        super(opType.Addr);
        this.fval = fval;
        this.isGlobal = isGlobal;
        this.isFloat = true;
    }

    public addr(float fval, String label, boolean isGlobal) {
        super(opType.Addr);
        this.fval = fval;
        this.label = label;
        this.isGlobal = isGlobal;
        this.isFloat = true;
    }

    public addr(String label, boolean isGlobal) {
        super(opType.Addr);
        this.label = label;
        this.isGlobal = isGlobal;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setGlobal(boolean isGlobal) {
        this.isGlobal = isGlobal;
    }

    @Override
    public String toString() {
        if (!StringUtils.isEmpty(label)) {
            return label;
        }

        if (isGlobal) {
            Log.ensure(false);
        }

        if (isInt) {
            return "#" + Integer.toString(ival);
        } else if (isFloat) {
            return "#" + Float.toString(fval);
        } else {
            Log.ensure(false);
            return "";
        }
    }

    public String toHexString() {
        if (isInt) {
            return Integer.toHexString(ival);
        } else if (isFloat) {
            return Integer.toHexString(Float.floatToIntBits(fval));
        } else {
            Log.ensure(false);
            return "";
        }
    }

}
