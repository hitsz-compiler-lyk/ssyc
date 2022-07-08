package top.origami404.ssyc.backend.operand;

import java.util.HashSet;

import top.origami404.ssyc.backend.arm.ArmInst;
import top.origami404.ssyc.utils.Log;

public class Reg extends Operand {
    private HashSet<ArmInst> instSet = new HashSet<>();

    public Reg(opType s) {
        super(s);
        instSet = new HashSet<>();
    }

    @Override
    public String toString() {
        Log.ensure(false);
        return  "";
    }

    public void addInst(ArmInst inst) {
        instSet.add(inst);
    }

    public int getInstNum() {
        return instSet.size();
    }

}
