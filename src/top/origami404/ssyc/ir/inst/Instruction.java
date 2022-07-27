package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.frontend.SourceCodeSymbol;
import top.origami404.ssyc.ir.*;
import top.origami404.ssyc.ir.IRVerifyException.SelfReferenceException;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.IListException;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;
import top.origami404.ssyc.utils.Log;

public abstract class Instruction extends User
    implements INodeOwner<Instruction, BasicBlock>
{
    Instruction(InstKind kind, IRType type) {
        super(type);
        this.kind = kind;
        this.inode = new INode<>(this);
    }

    /**
     * @return 返回 IR 的
     */
    public InstKind getKind() {
        return kind;
    }

    @Override
    public INode<Instruction, BasicBlock> getINode() {
        return inode;
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        ensure(getParentOpt().isPresent(), "An instruction must have a parent");

        for (final var op : getOperands()) {
            // phi 有可能引用自己, 所以这个情况要丢一个特殊的异常
            if (op == this) {
                throw new SelfReferenceException(this);
            }
        }

        for (final var user : getUserList()) {
            ensure(user != this, "Cannot use itself");
        }

        ensureNot(getType().isVoid() && getUserList().size() != 0,
                "Instructions of Void type must not have any user");

        try {
            getINode().verify();
        } catch (IListException e) {
            throw new IRVerifyException(this, "INode exception", e);
        }
    }

    public void freeAll() {
        freeFromUseDef();
        freeFromIList();
    }

    @Override
    public String toString() {
        return getKind() + ":" + getSymbolOpt().map(SourceCodeSymbol::toString).orElse("?") + "|" + getParentOpt().map(Value::toString).orElse("?");
    }

    private final InstKind kind;
    private final INode<Instruction, BasicBlock> inode;
}
