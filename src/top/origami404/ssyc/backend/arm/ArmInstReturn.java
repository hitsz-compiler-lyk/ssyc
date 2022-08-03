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

        var fuse = new StringBuilder();
        first = true;
        for (var reg : funcInfo.getfUsedRegs()) {
            if (!first) {
                fuse.append(", ");
            }
            fuse.append(reg.print());
            first = false;
        }

        if (!funcInfo.getfUsedRegs().isEmpty()) {
            ret += "\tvpop\t{" + fuse.toString() + "}\n";
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
