package ir.constant;

import ir.type.IRType;

public class BoolConst extends Constant {
    static final BoolConst trueBoolConst = new BoolConst();
    static final BoolConst falseBoolConst = new BoolConst();

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        if (this == trueBoolConst) {
            return 1;
        } else if (this == falseBoolConst) {
            return 0;
        } else {
            throw new RuntimeException("Another bool has apperanced");
        }
    }

    public boolean getValue() {
        return hashCode() == 1;
    }

    @Override
    public String toString() {
        return Integer.toString(hashCode());
    }

    private BoolConst() {
        super(IRType.BoolTy);
    }
}
