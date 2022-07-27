package top.origami404.ssyc.backend.operand;

import java.util.ArrayList;
import java.util.List;

import top.origami404.ssyc.backend.arm.ArmInst;

public abstract class Reg extends Operand {
    private List<ArmInst> instSet;
    private int id;

    public Reg(opType s) {
        super(s);
        this.instSet = new ArrayList<>();
    }

    public Reg(opType s, int id) {
        super(s);
        this.id = id;
        this.instSet = new ArrayList<>();
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void addInst(ArmInst inst) {
        instSet.add(inst);
    }

    public void removeInst(ArmInst inst) {
        instSet.remove(inst);
    }

    public void replaceRegAfter(ArmInst inst, Reg reg) {
        boolean isFound = false;
        var replaceInsts = new ArrayList<ArmInst>();
        for (var u : instSet) {
            if (isFound) {
                replaceInsts.add(u);
            }
            if (u.equals(inst)) {
                isFound = true;
            }
        }

        for (var u : replaceInsts) {
            u.replaceOperand(this, reg);
        }
    }

    public List<ArmInst> getInstSet() {
        return instSet;
    }

    public int getInstNum() {
        return instSet.size();
    }

}
