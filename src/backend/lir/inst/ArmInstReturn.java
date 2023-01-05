package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.codegen.CodeGenManager;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;

public class ArmInstReturn extends ArmInst {

    public ArmInstReturn(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstReturn(ArmBlock block) {
        super(ArmInstKind.Return);
        block.asElementView().add(this);
        this.setPrintCnt(7);
    }

    @Override
    public String print() {
        var block = this.getParent();
        var func = block.getParent();
        var stackSize = func.getFinalstackSize();
        String ret = "";
        if (stackSize > 0) {
            if (CodeGenManager.checkEncodeImm(stackSize)) {
                ret += "\tadd" + getCond().toString() + "\tsp,\tsp,\t#" + stackSize + "\n";
            } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                ret += "\tsub" + getCond().toString() + "\tsp,\tsp,\t#" + stackSize + "\n";
            } else {
                var move = new ArmInstMove(new IPhyReg("r4"), new IImm(stackSize));
                move.setCond(getCond());
                ret += move.print();
                ret += "\tadd" + getCond().toString() + "\tsp,\tsp,\tr4\n";
            }
        }

        var iuse = new StringBuilder();
        var useLR = false;
        var first = true;
        for (var reg : func.getiUsedRegs()) {
            if (!first) {
                iuse.append(", ");
            }
            if (reg.equals(new IPhyReg("lr"))) {
                iuse.append("pc");
                useLR = true;
            } else {
                iuse.append(reg.print());
            }
            first = false;
        }

        var fuse1 = new StringBuilder();
        var fuse2 = new StringBuilder();
        var fusedList = func.getfUsedRegs();
        first = true;
        for (int i = 0; i < Integer.min(fusedList.size(), 16); i++) {
            var reg = fusedList.get(i);
            if (!first) {
                fuse1.append(", ");
            }
            fuse1.append(reg.print());
            first = false;
        }
        first = true;
        for (int i = 16; i < fusedList.size(); i++) {
            var reg = fusedList.get(i);
            if (!first) {
                fuse2.append(", ");
            }
            fuse2.append(reg.print());
            first = false;
        }

        if (fuse2.length() != 0) {
            ret += "\tvpop" + getCond().toString() + "\t{" + fuse2.toString() + "}\n";
        }

        if (fuse1.length() != 0) {
            ret += "\tvpop" + getCond().toString() + "\t{" + fuse1.toString() + "}\n";
        }

        if (!func.getiUsedRegs().isEmpty()) {
            ret += "\tpop" + getCond().toString() + "\t{" + iuse.toString() + "}\n";
        }

        if (!useLR) {
            ret += "\t" + "bx" + getCond().toString() + "\t" + "lr" + "\n";
        }
        if (getCond().equals(ArmInst.ArmCondType.Any)) {
            ret += ".ltorg\n";
        }
        return ret;
    }

}
