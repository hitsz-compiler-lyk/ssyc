package ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import frontend.SourceCodeSymbol;
import ir.type.ArrayIRTy;
import ir.type.IRType;
import utils.ReflectiveTools;

public abstract class Value {
    protected Value(IRType type) {
        this.type = type;
        this.userList = new ArrayList<>();
        this.symbol = Optional.empty();
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
        final var oldUserList = new ArrayList<>(userList);
        oldUserList.forEach(u -> u.replaceOperandCO(this, newValue));
        ensure(userList.isEmpty(), "User list should be empty after RAUW");
        GlobalModifitationStatus.current().markAsChanged();
    }

    // public void replaceAllUseWithInBBlock(Value newValue, BasicBlock bblock) {
    //     final var usersInBlock = userList.stream()
    //         .filter(Instruction.class::isInstance)
    //         .filter(i -> i.as(Instruction.class).getParent().isPresent())
    //         .collect(Collectors.toUnmodifiableList());
    //
    //     for (final var user : usersInBlock) {
    //         user.replaceOperandCO(this, newValue);
    //         newValue.addUser(user);
    //     }
    //
    //     userList.removeAll(usersInBlock);
    // }

    public boolean isUseless() {
        return userList.isEmpty();
    }

    public Optional<SourceCodeSymbol> getSymbolOpt() {
        return symbol;
    }

    public SourceCodeSymbol getSymbol() {
        return getSymbolOpt().orElseThrow(() -> new RuntimeException("This value do NOT have a symbol with it"));
    }

    public void setSymbol(SourceCodeSymbol symbol) {
        this.symbol = Optional.of(symbol);
    }

    @Override
    public String toString() {
        return symbol.map(SourceCodeSymbol::toString).orElseGet(() -> this.getClass().getSimpleName());
    }

    /**
     * 验证 IR 的合法性.
     * 对于那些 "包含" 其他 Value 的 Value (如 BasicBlock/Function),
     * 这个方法不会递归调用它的其他元素
     * @throws IRVerifyException IR 不合法
     */
    public void verify() throws IRVerifyException {
        checkPointerAndArrayType();

        for (final var user : userList) {
            ensure(user.getOperands().contains(this),
                    "An user must contains this in its operands list");
        }
    }

    /**
     * 验证 IR 的合法性
     * 这个方法会递归调用它包含的其他 Value
     * @throws IRVerifyException IR 不合法
     */
    public void verifyAll() throws IRVerifyException {
        verify();
    }

    /**
     * 一个增加使用者的 "被动" 方法, 它只是朴素地加入一个 User, 不会 "主动" 维护 use-def 关系
     * @param user 待加入的 User
     */
    protected void addUser(User user) {
        userList.add(user);
    }
    protected void removeUser(User user) { userList.remove(user); }

    private final IRType type;
    private final List<User> userList;
    private Optional<SourceCodeSymbol> symbol;

    // 用于验证 IR 的方法
    protected void ensure(boolean cond, String message) {
        if (!cond) {
            final var info = ReflectiveTools.getCallerInfo();
            throw IRVerifyException.create(info.getLineNo(), this, message);
        }
    }

    protected void ensureNot(boolean cond, String message) {
        if (cond) {
            final var info = ReflectiveTools.getCallerInfo();
            throw IRVerifyException.create(info.getLineNo(), this, message);
        }
    }

    protected void verifyFail(String message) {
        final var info = ReflectiveTools.getCallerInfo();
        throw IRVerifyException.create(info.getLineNo(), this, message);
    }

    private void checkPointerAndArrayType() {
        // 对全局变量, 有可能出现指针的指针类型 (因为数组退化的缘故)
        if (type instanceof ArrayIRTy) {
            ensure(((ArrayIRTy) type).getElementType().canBeElement(),
                    "Array's element type must be int or float or array");
        }
    }
}
