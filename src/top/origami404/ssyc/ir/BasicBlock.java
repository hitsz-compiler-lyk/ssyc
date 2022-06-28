package top.origami404.ssyc.ir;

import java.util.ListIterator;
import java.util.Map;

import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.inst.PhiInst;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public class BasicBlock extends Value
    implements IListOwner<Instruction, BasicBlock>, INodeOwner<BasicBlock, Function>,
        AnalysisInfoOwner
{
    public BasicBlock(Function func) {
        this(func, "_" + bblockNo++);
    }

    public BasicBlock(Function func, String name) {
        // 在生成 while 或者是 if 的时候, bblock 经常会有自己的带独特前缀的名字
        // 比如 _cond_1, _if_23 之类的
        // 所以对 BasicBlock 保留带 name 的构造函数

        super(IRType.BBlockTy);

        this.instructions = new IList<>(this);
        func.getIList().asElementView().add(this);

        this.phiEnd = instructions.asINodeView().listIterator();
        this.name = name;
    }

    public IList<Instruction, BasicBlock> getIList() {
        return instructions;
    }

    public INode<BasicBlock, Function> getINode() {
        return inode;
    }

    public int getInstructionCount() {
        return instructions.getSize();
    }

    @Override
    public Map<String, AnalysisInfo> getInfoMap() {
        return analysisInfos;
    }

    public void addPhi(PhiInst phi) {
        phiEnd.add(phi.getINode());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private static int bblockNo = 0;

    private String name;
    private IList<Instruction, BasicBlock> instructions;
    private ListIterator<INode<Instruction, BasicBlock>> phiEnd;
    private INode<BasicBlock, Function> inode;
    private Map<String, AnalysisInfo> analysisInfos;
}
