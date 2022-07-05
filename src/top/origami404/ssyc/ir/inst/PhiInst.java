package top.origami404.ssyc.ir.inst;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class PhiInst extends Instruction {
    public PhiInst(IRType type) {
        super(InstKind.Phi, type);
    }

    public PhiInst(IRType type, String name) {
        this(type);
        super.setName(name);
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
}
