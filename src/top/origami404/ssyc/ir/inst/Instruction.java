package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
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
        this.name = "%" + instNo++;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private static int instNo = 0;

    private InstKind kind;
    private INode<Instruction, BasicBlock> bbNode;
    private String name;
}
