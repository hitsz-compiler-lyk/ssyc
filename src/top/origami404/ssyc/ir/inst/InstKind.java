package top.origami404.ssyc.ir.inst;

import java.util.Set;

public enum InstKind {
    IAdd, ISub, IMul, IDiv, IMod,
    FAdd, FSub, FMul, FDiv,

    INeg, FNeg,

    ICmpEq, ICmpNe, ICmpGt, ICmpGe, ICmpLt, ICmpLe,
    FCmpEq, FCmpNe, FCmpGt, FCmpGe, FCmpLt, FCmpLe,

    Br, BrCond,

    Alloc, Load, Store, Phi, GEP,

    Call, Ret,

    I2F, F2I, MemInit,
    ;

    public boolean isBinary()   { return binarySet.contains(this);  }
    public boolean isUnary()    { return unarySet.contains(this);   }
    public boolean isCmp()      { return cmpSet.contains(this);     }
    public boolean isBr()       { return brSet.contains(this);      }

    public boolean isInt()      { return intSet.contains(this);     }
    public boolean isFloat()    { return floatSet.contains(this);   }
    public boolean isConvert()  { return convertSet.contains(this); }


    private static final Set<InstKind> unarySet = Set.of(INeg, FNeg);
    private static final Set<InstKind> binarySet = Set.of(
        IAdd, ISub, IMul, IDiv, IMod,
        FAdd, FSub, FMul, FDiv);
    private static final Set<InstKind> cmpSet = Set.of(
        ICmpEq, ICmpNe, ICmpGt, ICmpGe, ICmpLt, ICmpLe,
        FCmpEq, FCmpNe, FCmpGt, FCmpGe, FCmpLt, FCmpLe);
    private static final Set<InstKind> brSet = Set.of(Br, BrCond, Ret);
    private static final Set<InstKind> intSet = Set.of(
        IAdd, ISub, IMul, IDiv, IMod, INeg, F2I,
        ICmpEq, ICmpNe, ICmpGt, ICmpGe, ICmpLt, ICmpLe);
    private static final Set<InstKind> floatSet = Set.of(
        FAdd, FSub, FMul, FDiv, FNeg, I2F,
        FCmpEq, FCmpNe, FCmpGt, FCmpGe, FCmpLt, FCmpLe);
    private static final Set<InstKind> convertSet = Set.of(I2F, F2I);
}
