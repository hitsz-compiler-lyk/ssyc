package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.User;
import top.origami404.ssyc.ir.type.IRType;

public abstract class Instruction extends User {
    Instruction(InstKind kind, IRType type) {
        super(type);
        this.kind = kind;
    }

    /**
     * @return 返回 IR 的
     */
    public InstKind getKind() {
        return kind;
    }

    private InstKind kind;
}
