package top.origami404.ssyc.ir.inst;

import java.util.LinkedList;
import java.util.List;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class PhiInst extends Instruction {
    public PhiInst(IRType type) {
        super(InstKind.Phi, type);
        this.arguments = new LinkedList<>();
    }

    public void addArgumentCO(Value value) {
        arguments.add(value);
        addOperandCO(value);
    }

    public List<Value> getArguments() {
        return arguments;
    }

    public Value getArgument(int index) {
        return arguments.get(index);
    }

    private List<Value> arguments;
}
