package top.origami404.ssyc.ir;

import java.util.List;

import top.origami404.ssyc.ir.type.IRType;

public abstract class Value {
    public Value(IRType type) {
        this.type = type;
    }

    /**
     * @return 这个 Value 的 IR Type, 即其在 IR 里的 "类型"
     */
    public IRType getType() {
        return type;
    }


    public List<User> getUserList() {
        return userList;
    }

    public<T extends Value> boolean is(Class<T> cls) {
        return cls.isInstance(this);
    }

    public<T extends Value> T as(Class<T> cls) {
        return cls.cast(this);
    }

    /**
     * 一个增加使用者的 "被动" 方法, 它只是朴素地加入一个 User, 不会 "主动" 维护 use-def 关系
     * @param user 待加入的 User
     */
    protected void addUser(User user) {
        userList.add(user);
    }

    private IRType type;
    private List<User> userList;
}
