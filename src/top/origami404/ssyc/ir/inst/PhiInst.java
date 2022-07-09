package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.frontend.info.VersionInfo.Variable;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.IRTypeException;

import java.util.List;
import java.util.stream.Collectors;

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

    public List<Instruction> getArguments() {
        try {
            return getOperands().stream().map(Instruction.class::cast).collect(Collectors.toList());
        } catch (ClassCastException e) {
            throw new IRTypeException(this, "Arguments of phi must be instructions");
        }
    }

    private final Variable variable;
    private boolean incompleted;
}
