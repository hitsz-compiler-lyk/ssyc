package backend;

import backend.operand.FPhyReg;
import backend.operand.IPhyReg;
import backend.operand.Reg;

import java.util.ArrayList;
import java.util.List;

public class Consts {
    public static final String SimpleGraphColoring = "SimpleGraphColoring";
    public static final int iAllocableRegCnt = 14;
    public static final int fAllocableRegCnt = 32;
    public static final List<Reg> allocableRegs = new ArrayList<>() {
        {
            for (int i = 0; i <= 12; i++) {
                add(new IPhyReg(i));
            }
            add(new IPhyReg(14));

            for (int i = 0; i <= 31; i++) {
                add(new FPhyReg(i));
            }
        }
    };
    public static final List<IPhyReg> allocableIRegs = new ArrayList<>() {
        {
            for (int i = 0; i <= 12; i++) {
                add(new IPhyReg(i));
            }
            add(new IPhyReg(14));
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
