package top.origami404.ssyc.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

public abstract class Value {
    public Value(IRType type) {
        this.type = type;
        this.userList = new ArrayList<>();
        this.name = null;
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
     * <p>
     * 获得一个在 LLVM IR 里可以作为其他指令的参数的名字,
     * 比如说全局数组常量会是 @ 开头的字符串,
     * 基本块, 指令, 参数的名字会是 % 开头的字符串.
     * </p>
     * <p>
     * 如果需要获得其他种类的名字, 比如基本块的用作 label 的名字,
     * 则需要强转到对应类型再调用对应的方法.
     * </p>
     *
     * @return 名字
     */
    public String getName() {
        if (name == null) {
            throw new RuntimeException("This value has no name! " + this);
        }

        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void verify() throws IRVerifyException {
        ensure(name != null, "A value must have name");
        ensure(name.length() > 1, "A value's name must longer than 1");
        checkPointerAndArrayType();

        for (final var user : userList) {
            ensure(user.getOperands().contains(this),
                    "An user must contains this in its operands list");
        }
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
    private String name;

    // 用于验证 IR 的方法
    protected void ensure(boolean cond, String message) {
        if (!cond) {
            throw new IRVerifyException(this, message);
        }
    }

    protected void ensureNot(boolean cond, String message) {
        ensure(!cond, message);
    }

    protected void verifyFail(String message) {
        ensure(false, message);
    }

    private void checkPointerAndArrayType() {
        final var type = getType();
        if (type instanceof PointerIRTy) {
            ensure(((PointerIRTy) type).getBaseType().canBeElement(),
                    "Pointer's base type must be int or float or array");
        } else if (type instanceof ArrayIRTy) {
            ensure(((ArrayIRTy) type).getElementType().canBeElement(),
                    "Array's element type must be int or float or array");
        }
    }
}
