package top.origami404.ssyc.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.type.IRType;

public abstract class Value {
    public Value(IRType type) {
        this.type = type;
        this.userList = new ArrayList<>();
    }

    /**
     * @return 这个 Value 的 IR Type, 即其在 IR 里的 "类型"
     */
    public IRType getType() {
        return type;
    }

    public List<User> getUserList() {
        return Collections.unmodifiableList(userList);
    }

    public<T extends Value> boolean is(Class<T> cls) {
        return cls.isInstance(this);
    }

    public<T extends Value> T as(Class<T> cls) {
        return cls.cast(this);
    }

    public void replaceAllUseWith(Value newValue) {
        userList.forEach(u -> u.replaceOperandCO(this, newValue));
        newValue.userList = userList;
        userList = new ArrayList<>();
    }

    public void replaceAllUseWithInBBlock(Value newValue, BasicBlock bblock) {
        final var usersInBlock = userList.stream()
            .filter(Instruction.class::isInstance)
            .filter(i -> i.as(Instruction.class).getParent().isPresent())
            .collect(Collectors.toUnmodifiableList());

        for (final var user : usersInBlock) {
            user.replaceOperandCO(this, newValue);
            newValue.addUser(user);
        }

        userList.removeAll(usersInBlock);
    }

    public boolean isUseless() {
        return userList.isEmpty();
    }

    /**
     * 一个增加使用者的 "被动" 方法, 它只是朴素地加入一个 User, 不会 "主动" 维护 use-def 关系
     * @param user 待加入的 User
     */
    protected void addUser(User user) {
        userList.add(user);
    }
    protected void removeUser(User user) { userList.remove(user); }

    private IRType type;
    private List<User> userList;
}
