package top.origami404.ssyc.backend.operand;

import java.util.HashSet;

import top.origami404.ssyc.backend.arm.ArmInst;

public class Reg extends Operand {
    protected HashSet<ArmInst> instSet = new HashSet<>();
    protected int id;

    public Reg(opType s) {
        super(s);
    }

    @Override
    public String toString() {
        return "r" + Integer.toString(id);
    }

    public void addInst(ArmInst inst) {
        instSet.add(inst);
    }

    public int getInstNum() {
        return instSet.size();
    }

}
