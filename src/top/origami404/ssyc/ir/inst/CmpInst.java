package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class CmpInst extends Instruction {
    public CmpInst(InstKind cmpKind, Value lhs, Value rhs) {
        super(cmpKind, IRType.BoolTy);
        super.addOperandCO(lhs);
        super.addOperandCO(rhs);
    }

    public Value getLHS() { return getOperand(0); }
    public Value getRHS() { return getOperand(1); }
}
