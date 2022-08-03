package ir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import ir.type.IRType;
import utils.Log;

public class User extends Value {
    public User(IRType type) {
        super(type);
        this.operandList = new ArrayList<>();
    }

    /**
     * @return 只读的 operand 列表
     */
    public List<Value> getOperands() {
        return Collections.unmodifiableList(operandList);
    }

    public int getOperandSize() {
        return operandList.size();
    }

    public Value getOperand(int index) {
        return operandList.get(index);
    }

    public Value replaceOperandCO(int index, Value newValue) {
        final var oldValue = operandList.get(index);
        Log.debug("Replace %s to %s".formatted(oldValue, newValue));
        oldValue.removeUser(this);

        operandList.set(index, newValue);
        newValue.addUser(this);

        return oldValue;
    }

    public Value replaceOperandCO(Value oldValue, Value newValue) {
        final var idx = operandList.indexOf(oldValue);
        return replaceOperandCO(idx, newValue);
    }

    /** 删除其所有 operand 使其再也不会使用其他 Value */
    public void freeFromUseDef() {
        ensure(getUserList().isEmpty(), "Can NOT call freeFromUseDef on value that are current being used");
        removeOperandAllCO();
        GlobalModifitationStatus.current().markAsChanged();
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();
        for (final var op : operandList) {
            ensure(op.getUserList().contains(this),
                    "An operand must contains this in its users list");
        }
    }

    // 这些直接的修改性方法只有子类有资格调用
    // 因为对普通指令而言, 一旦构造完成, 其参数数量就不应该再改变
    // 当然, MemInit, Phi 等指令除外, 不过它们必然有对应方法来调用这个方法
    // 所以这些方法是 protected 的

    protected void addOperandCO(Value operand) {
        Log.debug("Add %s to %s".formatted(operand, this));
        operandList.add(operand);
        operand.addUser(this);
    }

    protected void addAllOperandsCO(Collection<? extends Value> operands) {
        operands.forEach(this::addOperandCO);
    }

    protected Value removeOperandCO(Value value) {
        final var index = operandList.indexOf(value);
        return removeOperandCO(index);
    }

    protected Value removeOperandCO(int index) {
        final var oldValue = operandList.remove(index);
        Log.debug("Remove %s from %s".formatted(oldValue, this));
        oldValue.removeUser(this);
        return oldValue;
    }

    protected void removeOperandAllCO() {
        for (var i = operandList.size() - 1; i >= 0; i--) {
            removeOperandCO(i);
        }
    }

    protected void removeOperandIfCO(Predicate<? super Value> predicate) {
        Collections.unmodifiableList(operandList).stream().filter(predicate).forEach(this::removeOperandCO);
    }

    private final List<Value> operandList;
}
