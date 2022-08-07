package backend.arm;

import backend.codegen.CodeGenManager;
import backend.operand.IImm;
import backend.operand.IPhyReg;

public class ArmInstReturn extends ArmInst {

    public ArmInstReturn(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstReturn(ArmBlock block) {
        super(ArmInstKind.Return);
        block.asElementView().add(this);
        this.setPrintCnt(50); //6
    }

    @Override
    public String print() {
        var block = this.getParent();
        var func = block.getParent();
        var funcInfo = func.getFuncInfo();
        var stackSize = funcInfo.getFinalstackSize();
        String ret = "";
        if (stackSize > 0) {
            if (CodeGenManager.checkEncodeImm(stackSize)) {
                ret += "\tadd\tsp,\tsp,\t#" + stackSize + "\n";
            } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                ret += "\tsub\tsp,\tsp,\t#" + stackSize + "\n";
            } else {
                var move = new ArmInstMove(new IPhyReg("r4"), new IImm(stackSize));
                ret += move.print();
                ret += "\tadd\tsp,\tsp,\tr4\n";
            }
        }

        var iuse = new StringBuilder();
        var useLR = false;
        var first = true;
        for (var reg : funcInfo.getiUsedRegs()) {
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
        var fusedList = funcInfo.getfUsedRegs();
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
            ret += "\tvpop\t{" + fuse2.toString() + "}\n";
        }

        if (fuse1.length() != 0) {
            ret += "\tvpop\t{" + fuse1.toString() + "}\n";
        }

        if (!funcInfo.getiUsedRegs().isEmpty()) {
            ret += "\tpop\t{" + iuse.toString() + "}\n";
        }

        if (!useLR) {
            ret += "\t" + "bx" + "\t" + "lr" + "\n";
        }
        ret += ".ltorg\n";
        return ret;
    }

}
