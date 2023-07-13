package backend.regallocator;

import backend.lir.operand.FPhyReg;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Reg;
import utils.Log;

import java.util.HashSet;
import java.util.Set;

public class InterfereRegs {
    private int icnt, fcnt;
    private final Set<Reg> regs;
    private final boolean selfIsInt;

    public InterfereRegs(Reg self) {
        this.regs = new HashSet<>();
        this.selfIsInt = self.isInt();
        this.icnt = 0;
        this.fcnt = 0;
        if (this.selfIsInt) {
            this.icnt++;
        } else {
            this.fcnt++;
        }
    }

    public void add(Reg reg) {
        if (regs.contains(reg)) {
            return;
        }
        if (reg.isInt()) {
            this.icnt++;
        } else {
            this.fcnt++;
        }
        regs.add(reg);
    }

    public void remove(Reg reg) {
        Log.ensure(regs.contains(reg), "remove reg not contains in regs");
        if (reg.isInt()) {
            this.icnt--;
        } else {
            this.fcnt--;
        }
        regs.remove(reg);
    }

    public Set<Reg> getRegs() {
        return regs;
    }

    public boolean canSimplify() {
        return icnt <= IPhyReg.getIntAllocatableRegs().size()
                && fcnt <= FPhyReg.getFloatAllocatableRegs().size();
    }

    public int getICnt() {
        return icnt;
    }

    public int getFCnt() {
        return fcnt;
    }

    public int getCnt(boolean isInteger) {
        return isInteger ? icnt : fcnt;
    }

    public int getAllCnt() {
        return icnt + fcnt;
    }

    public void clear() {
        this.regs.clear();
        this.icnt = 0;
        this.fcnt = 0;
        if (this.selfIsInt) {
            this.icnt++;
        } else {
            this.fcnt++;
        }
    }

    @Override
    public String toString() {
        return regs.toString();
    }
}
