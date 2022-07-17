package top.origami404.ssyc.backend.operand;

import java.util.HashSet;
import java.util.Set;

import top.origami404.ssyc.backend.arm.ArmInst;

public abstract class Reg extends Operand {
    private Set<ArmInst> instSet;
    private int id;

    public Reg(opType s) {
        super(s);
        this.instSet = new HashSet<>();
    }

    public Reg(opType s, int id) {
        super(s);
        this.id = id;
        this.instSet = new HashSet<>();
    }

    public abstract String toString();

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

    public Set<ArmInst> getInstSet() {
        return instSet;
    }

    public int getInstNum() {
        return instSet.size();
    }

}
