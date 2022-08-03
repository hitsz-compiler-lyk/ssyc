package top.origami404.ssyc.backend;

import java.util.ArrayList;
import java.util.List;

import top.origami404.ssyc.backend.operand.FPhyReg;
import top.origami404.ssyc.backend.operand.IPhyReg;
import top.origami404.ssyc.backend.operand.Reg;

public class Consts {
    public static final String SimpleGraphColoring = "SimpleGraphColoring";
    public static final String SimpleRegisterAllocator = "SimpleRegisterAllocator";
    public static final int iAllocableRegCnt = 14;
    public static final int fAllocableRegCnt = 8;
    public static final List<Reg> allocableRegs = new ArrayList<>() {
        {
            for (int i = 0; i <= 12; i++) {
                add(new IPhyReg(i));
            }
            add(new IPhyReg(14));

            for (int i = 0; i <= 7; i++) {
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
            for (int i = 0; i <= 7; i++) {
                add(new FPhyReg(i));
            }
        }
    };
}
