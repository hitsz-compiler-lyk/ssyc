package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.frontend.info.VersionInfo.Variable;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class PhiInst extends Instruction {
    public PhiInst(IRType type, Variable variable) {
        super(InstKind.Phi, type);
        super.setName(variable.getIRName());

        this.variable = variable;
        this.incompleted = true;
    }

    @Override
    public void addOperandCO(Value operand) {
        super.addOperandCO(operand);
    }

    @Override
    public Value removeOperandCO(int index) {
        return super.removeOperandCO(index);
    }

    @Override
    public Value removeOperandCO(Value value) {
        return super.removeOperandCO(value);
    }

    public boolean isIncompleted() {
        return incompleted;
    }

    public void markAsCompleted() {
        this.incompleted = false;
    }

    public Variable getVariable() {
        return variable;
    }

    private final Variable variable;
    private boolean incompleted;
}
