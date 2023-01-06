package backend.lir.visitor;

import backend.lir.operand.*;

public interface OperandVisitor<T> {
    default T visit(Operand operand) {
        if (operand instanceof Addr) { return visitAddr((Addr) operand); }
        if (operand instanceof IImm) { return visitIImm((IImm) operand); }
        if (operand instanceof FImm) { return visitFImm((FImm) operand); }
        if (operand instanceof IVirtualReg) { return visitIVirtualReg((IVirtualReg) operand); }
        if (operand instanceof FVirtualReg) { return visitFVirtualReg((FVirtualReg) operand); }
        if (operand instanceof IPhyReg) { return visitIPhyReg((IPhyReg) operand); }
        if (operand instanceof FPhyReg) { return visitFPhyReg((FPhyReg) operand); }
        throw new RuntimeException("Unknown class: " + operand.getClass().getSimpleName());
    }

    T visitAddr(Addr operand);
    T visitIImm(IImm operand);
    T visitFImm(FImm operand);
    T visitIVirtualReg(IVirtualReg operand);
    T visitFVirtualReg(FVirtualReg operand);
    T visitIPhyReg(IPhyReg operand);
    T visitFPhyReg(FPhyReg operand);
}
