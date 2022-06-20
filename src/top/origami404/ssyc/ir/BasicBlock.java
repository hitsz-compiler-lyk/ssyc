package top.origami404.ssyc.ir;

import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public class BasicBlock extends Value 
    implements IListOwner<Instruction, BasicBlock>, INodeOwner<BasicBlock, Function> 
{
    public BasicBlock() {
        super(IRType.BBlockTy);

        this.instructions = new IList<>(this);
    }

    public IList<Instruction, BasicBlock> getIList() {
        return instructions;
    }

    public INode<BasicBlock, Function> getINode() {
        return inode;
    }

    private IList<Instruction, BasicBlock> instructions;
    private INode<BasicBlock, Function> inode;
}
