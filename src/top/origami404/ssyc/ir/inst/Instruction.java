package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.User;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public abstract class Instruction extends User
    implements INodeOwner<Instruction, BasicBlock>
{
    Instruction(InstKind kind, IRType type) {
        super(type);
        this.kind = kind;
        this.bbNode = new INode<>(this);
        super.setName("%" + instNo++);
    }

    /**
     * @return 返回 IR 的
     */
    public InstKind getKind() {
        return kind;
    }

    @Override
    public INode<Instruction, BasicBlock> getINode() {
        return bbNode;
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        ensure(getName().charAt(0) == '%', "An instruction must have a name begins with '@'");
        ensure(getParent().isPresent(), "An instruction must have a parent");

        for (final var op : getOperands()) {
            ensure(op != this, "Cannot use itself as an operand");
        }

        for (final var user : getUserList()) {
            ensure(user != this, "Cannot use itself");
        }

        ensureNot(getType().isVoid() && getUserList().size() != 0,
                "Instructions of Void type must not have any user");
    }

    private static int instNo = 0;

    private InstKind kind;
    private INode<Instruction, BasicBlock> bbNode;
}
