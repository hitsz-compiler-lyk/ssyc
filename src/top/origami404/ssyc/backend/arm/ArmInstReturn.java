package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.codegen.CodeGenManager;
import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.IPhyReg;

public class ArmInstReturn extends ArmInst {

    public ArmInstReturn(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstReturn(ArmBlock block) {
        super(ArmInstKind.Return);
        block.asElementView().add(this);
    }

    @Override
    public String toString() {
        String ret = "";
        var block = getParent().get();
        var func = block.getParent().get();
        var stackSize = func.getFuncInfo().getStackSize();
        if (stackSize > 0) {
            if (CodeGenManager.checkEncodeIImm(stackSize)) {
                var add = new ArmInstBinary(ArmInstKind.IAdd, new IPhyReg("sp"), new IPhyReg("sp"),
                        new IImm(stackSize));
                ret += add.toString();
            } else if (CodeGenManager.checkEncodeIImm(-stackSize)) {
                var sub = new ArmInstBinary(ArmInstKind.ISub, new IPhyReg("sp"), new IPhyReg("sp"),
                        new IImm(-stackSize));
                ret += sub.toString();
            } else {
                var move = new ArmInstMove(new IPhyReg("r1"), new IImm(stackSize));
                ret += move.toString();
                var sub = new ArmInstBinary(ArmInstKind.ISub, new IPhyReg("sp"), new IPhyReg("sp"), new IPhyReg("r1"));
                ret += sub.toString();
            }
        }

        ret += "\t" + "bx" + "\t" + "lr" + "\n";
        return ret;
    }

}
