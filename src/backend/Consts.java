package backend;

import backend.lir.operand.FPhyReg;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Reg;

import java.util.ArrayList;
import java.util.List;

public class Consts {
    public static final String SimpleGraphColoring = "SimpleGraphColoring";
    public static final int iAllocableRegCnt = 14;
    public static final int fAllocableRegCnt = 32;
    public static final List<Reg> allocableRegs = new ArrayList<>() {
        {
            for (int i = 0; i <= 12; i++) {
                add(IPhyReg.R(i));
            }
            add(IPhyReg.LR);

            for (int i = 0; i <= 31; i++) {
                add(new FPhyReg(i));
            }
        }
    };
    public static final List<IPhyReg> allocableIRegs = new ArrayList<>() {
        {
            for (int i = 0; i <= 12; i++) {
                add(IPhyReg.R(i));
            }
            add(IPhyReg.LR);
        }
    };
    public static final List<FPhyReg> allocableFRegs = new ArrayList<>() {
        {
            for (int i = 0; i <= 31; i++) {
                add(new FPhyReg(i));
            }
        }
    };
}
