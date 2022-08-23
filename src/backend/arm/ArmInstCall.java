package backend.arm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import backend.operand.FPhyReg;
import backend.operand.IPhyReg;
import backend.operand.Operand;
import backend.operand.Reg;
import utils.StringUtils;

public class ArmInstCall extends ArmInst {
    private ArmFunction func;
    private String funcName;
    private int paramsCnt;
    private int fparamsCnt;
    private boolean returnFloat;

    public ArmInstCall(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstCall(ArmBlock block, ArmFunction func) {
        super(ArmInstKind.Call);
        this.func = func;
        block.asElementView().add(this);
        this.paramsCnt = func.getParamsCnt();
        this.fparamsCnt = func.getFparamsCnt();
        this.returnFloat = func.isReturnFloat();
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        ops.add(new IPhyReg("r12")); // 如果是外部函数 则会因为链接器从而把r12定值
        int fcnt = 0;
        if (this.returnFloat) {
            fcnt = 1;
        }
        fcnt = Integer.max(fcnt, this.fparamsCnt);
        for (int i = 0; i < 16; i++) {
            ops.add(new FPhyReg(i));
        }
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
    }

    public ArmInstCall(ArmBlock block, String funcName, int paramsCnt, int fparamsCnt, boolean returnFloat) {
        super(ArmInstKind.Call);
        this.funcName = funcName;
        block.asElementView().add(this);
        this.paramsCnt = Integer.min(paramsCnt, 4);
        this.fparamsCnt = Integer.min(fparamsCnt, 16);
        this.returnFloat = returnFloat;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        ops.add(new IPhyReg("r12")); // 如果是外部函数 则会因为链接器从而把r12定值 memset 和 memcpy不会?
        for (int i = 0; i < 16; i++) {
            ops.add(new FPhyReg(i));
        }
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
    }

    public void setFunc(ArmFunction func) {
        this.func = func;
    }

    public void setFuncName(String funcName) {
        this.funcName = funcName;
    }

    public String getFuncName() {
        return funcName;
    }

    @Override
    public Set<Reg> getRegUse() {
        var ret = new HashSet<Reg>();
        for (int i = 0; i < Integer.min(this.paramsCnt, 4); i++) {
            ret.add(new IPhyReg(i));
        }
        for (int i = 0; i < Integer.min(this.fparamsCnt, 16); i++) {
            ret.add(new FPhyReg(i));
        }
        return ret;
    }

    @Override
    public String print() {
        // String ret = "\tmov\tlr,\tpc\n";
        // if (!StringUtils.isEmpty(funcName)) {
        // return ret + "\t" + "ldr" + "\tpc,\t=" + funcName + "\n";
        // }
        // return ret + "\t" + "ldr" + "\tpc,\t=" + func.getName() + "\n";
        if (!StringUtils.isEmpty(funcName)) {
            return "\t" + "bl" + getCond().toString() + "\t" + funcName + "\n";
        }
        return "\t" + "bl" + getCond().toString() + "\t" + func.getName() + "\n";
    }
}
