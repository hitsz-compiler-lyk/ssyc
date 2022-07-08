package top.origami404.ssyc.ir.constant;

import top.origami404.ssyc.ir.type.IRType;

public class BoolConst extends Constant {
    static final BoolConst trueBoolConst = new BoolConst("1");
    static final BoolConst falseBoolConst = new BoolConst("0");

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

    private BoolConst(String name) {
        super(IRType.BoolTy);
        super.setName(name);
    }
}
