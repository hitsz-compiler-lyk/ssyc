package top.origami404.ssyc.ir;

import java.util.LinkedList;
import java.util.List;

import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.type.IRType;

public class BasicBlock extends Value {
    public BasicBlock() {
        super(IRType.BBlockTy);

        this.instList = new LinkedList<>();
    }

    private List<Instruction> instList;
}
