package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.frontend.SourceCodeSymbol;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.IRVerifyException.SelfReferenceException;
import top.origami404.ssyc.ir.User;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.FloatConst;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.IListException;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

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

    /**
     * 根据 kind 与 operands 计算一个哈希值以供参考
     * <p>类型相同, 参数同一 ==> hashCode 相等</p>
     * 哈希方法来自:  <a href="https://stackoverflow.com/a/113600">SO 回答</a>
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用 stream API 的话, 会导致 int 反复装箱开箱, 可能会导致性能问题
        int hash = getKind().ordinal();

        for (final var op : getOperands()) {
            hash *= 37;
            if (op instanceof IntConst) {
                hash += ((IntConst) op).getValue();
            } else if (op instanceof FloatConst) {
                hash += Float.floatToIntBits(((FloatConst) op).getValue());
            } else {
                hash += System.identityHashCode(op);
            }
        }

        return hash;
    }

    private final InstKind kind;
    private final INode<Instruction, BasicBlock> inode;
}
