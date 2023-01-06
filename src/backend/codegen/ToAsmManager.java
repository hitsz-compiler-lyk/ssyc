package backend.codegen;

import backend.lir.ArmBlock;
import backend.lir.ArmFunction;
import backend.lir.ArmModule;
import backend.lir.ArmShift;
import backend.lir.inst.*;
import backend.lir.inst.ArmInst.ArmInstKind;
import backend.lir.operand.*;
import ir.constant.ArrayConst;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.constant.FloatConst;
import ir.constant.IntConst;
import utils.Log;

import java.util.*;

public class ToAsmManager {
    private final ArmModule module;

    public ToAsmManager(ArmModule module) {
        this.module = module;
    }

    public StringBuilder codeGenArm() {
        var arm = new StringBuilder();
        arm.append(".arch armv7ve\n");
        Set<ArrayConst> acSet = new HashSet<>();
        for (var entry : module.getGlobalVariables().entrySet()) {
            var key = entry.getKey();
            var val = entry.getValue().getInit();
            if (val instanceof ZeroArrayConst) {
                arm.append("\n.bss\n.align 4\n");
            } else {
                arm.append("\n.data\n.align 4\n");
            }
            arm.append(".global\t" + key + "\n" + key + ":\n");
            if (val instanceof IntConst) {
                arm.append(codeGenIntConst((IntConst) val));
            } else if (val instanceof FloatConst) {
                arm.append(codeGenFloatConst((FloatConst) val));
            } else if (val instanceof ArrayConst) {
                acSet.add((ArrayConst) val);
                arm.append(codeGenArrayConst((ArrayConst) val));
            }
            arm.append("\n");
        }
        for (var entry : module.getArrayConstants().entrySet()) {
            var key = entry.getKey();
            var val = entry.getValue();
            if (acSet.contains(val)) {
                continue;
            }
            if (val instanceof ZeroArrayConst) {
                arm.append("\n.bss\n.align 4\n");
            } else {
                arm.append("\n.data\n.align 4\n");
            }
            arm.append(key + ":\n");
            arm.append(codeGenArrayConst(val));
            arm.append("\n");
        }

        arm.append("\n.text\n");
        for (var func : module.getFunctions()) {
            var stackSize = func.getFinalstackSize();
            String prologuePrint = "";

            var iuse = new StringBuilder();
            var first = true;
            for (var reg : func.getiUsedRegs()) {
                if (!first) {
                    iuse.append(", ");
                }
                iuse.append(reg.print());
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

            if (!func.getiUsedRegs().isEmpty()) {
                prologuePrint += "\tpush\t{" + iuse.toString() + "}\n";
            }

            if (fuse1.length() != 0) {
                prologuePrint += "\tvpush\t{" + fuse1.toString() + "}\n";
            }

            if (fuse2.length() != 0) {
                prologuePrint += "\tvpush\t{" + fuse2.toString() + "}\n";
            }

            if (stackSize > 0) {
                if (CodeGenManager.checkEncodeImm(stackSize)) {
                    prologuePrint += "\tsub\tsp,\tsp,\t#" + stackSize + "\n";
                } else if (CodeGenManager.checkEncodeImm(-stackSize)) {
                    prologuePrint += "\tadd\tsp,\tsp,\t#" + stackSize + "\n";
                } else {
                    var move = new ArmInstMove(IPhyReg.R(4), new IImm(stackSize));
                    prologuePrint += new InstToAsm().visitArmInstMove(move);
                    prologuePrint += "\tsub\tsp,\tsp,\tr4\n";
                }
            }
            arm.append("\n.global\t" + func.getName() + "\n" + func.getName() + ":\n");
            arm.append(prologuePrint);
            fixLtorg(func);
            for (var block : func.asElementView()) {
                arm.append(block.getLabel() + ":\n");
                for (var inst : block.asElementView()) {
                    arm.append(new InstToAsm().visit(inst));
                }
            }
        }
        return arm;
    }

    private String codeGenIntConst(IntConst val) {
        return "\t" + ".word" + "\t" + val.getValue() + "\n";
    }

    private String codeGenFloatConst(FloatConst val) {
        return "\t" + ".word" + "\t" + "0x" + Integer.toHexString(Float.floatToIntBits(val.getValue())) + "\n";
    }

    private String codeGenArrayConst(ArrayConst val) {
        if (val instanceof ZeroArrayConst) {
            return "\t" + ".zero" + "\t" + val.getType().getSize() + "\n";
        }
        var sb = new StringBuilder();
        for (var elem : val.getRawElements()) {
            if (elem instanceof IntConst) {
                return codeGenIntArrayConst(val);
                // sb.append(CodeGenIntConst((IntConst) elem));
            } else if (elem instanceof FloatConst) {
                return codeGenFloatArrayConst(val);
                // sb.append(CodeGenFloatConst((FloatConst) elem));
            } else if (elem instanceof ArrayConst) {
                sb.append(codeGenArrayConst((ArrayConst) elem));
            }
        }
        return sb.toString();
    }

    private String codeGenIntArrayConst(ArrayConst arr) {
        var sb = new StringBuilder();
        int cnt = 0;
        IntConst val = null;
        for (var elem : arr.getRawElements()) {
            Log.ensure(elem instanceof IntConst);
            var ic = (IntConst) elem;
            if (val != null && val.getValue() == ic.getValue()) {
                cnt++;
            } else {
                if (cnt == 1) {
                    sb.append(codeGenIntConst(val));
                } else if (cnt > 1) {
                    if (val.getValue() == 0) {
                        sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
                    } else {
                        sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
                    }
                }
                cnt = 1;
                val = ic;
            }
        }
        if (cnt == 1) {
            sb.append(codeGenIntConst(val));
        } else if (cnt > 1) {
            if (val.getValue() == 0) {
                sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
            } else {
                sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
            }
        }
        return sb.toString();
    }

    private String codeGenFloatArrayConst(ArrayConst arr) {
        var sb = new StringBuilder();
        int cnt = 0;
        FloatConst val = null;
        for (var elem : arr.getRawElements()) {
            Log.ensure(elem instanceof FloatConst);
            var fc = (FloatConst) elem;
            if (val != null && (val.getValue() == fc.getValue())) {
                cnt++;
            } else {
                if (cnt == 1) {
                    sb.append(codeGenFloatConst(val));
                } else if (cnt > 1) {
                    if (val.getValue() == 0) {
                        sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
                    } else {
                        sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
                    }
                }
                cnt = 1;
                val = fc;
            }
        }
        if (cnt == 1) {
            sb.append(codeGenFloatConst(val));
        } else if (cnt > 1) {
            if (val.getValue() == 0) {
                sb.append("\t" + ".zero" + "\t" + 4 * cnt + "\n");
            } else {
                sb.append("\t" + ".fill" + "\t" + cnt + ",\t4,\t" + val + "\n");
            }
        }
        return sb.toString();
    }


    private void fixLtorg(ArmFunction func) {
        boolean haveLoadFImm = false;
        int offset = 0;
        int cnt = 0;
        for (var block : func.asElementView()) {
            for (var inst : block.asElementView()) {
                if (inst.needLtorg()) {
                    haveLoadFImm = true;
                }
                if (inst.haveLtorg()) {
                    haveLoadFImm = false;
                    offset = 0;
                }
                if (haveLoadFImm) {
                    offset += inst.getPrintCnt();
                }
                if (offset > 250) {
                    var ltorg = new ArmInstLtorg(func.getName() + "_ltorg_" + cnt++);
                    ltorg.InitSymbol();
                    inst.insertAfterCO(ltorg);
                    haveLoadFImm = false;
                    offset = 0;
                }
            }
        }
    }


}
