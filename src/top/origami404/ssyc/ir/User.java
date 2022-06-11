package top.origami404.ssyc.ir;

import java.util.Collection;
import java.util.List;

import top.origami404.ssyc.ir.type.IRType;

public class User extends Value {
    public User(IRType type) {
        super(type);
    }

    public List<Value> getOperandList() {
        return operandList;
    }

    /**
     * 一个 "主动" 的加入参数的方法, 会维护 use-def 关系
     * @param operand
     */
    public void addOperand(Value operand) {
        operandList.add(operand);
        operand.addUser(this);
    }

    public void addAllOperands(Collection<? extends Value> operands) {
        for (final var op : operands) {
            this.addOperand(op);
        }
    }

    private List<Value> operandList;
}
