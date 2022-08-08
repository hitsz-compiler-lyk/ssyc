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
    private boolean isFloatParam;

    public ArmInstCall(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstCall(ArmBlock block, ArmFunction func) {
        super(ArmInstKind.Call);
        this.func = func;
        block.asElementView().add(this);
        this.paramsCnt = func.getParamsCnt();
        // int defCnt = Integer.min(func.getParamsCnt(), 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        ops.add(new IPhyReg("r12"));
        ops.add(new FPhyReg("s0"));
        // if (func.isExternal()) {
        // ops.add(new IPhyReg("r12"));
        // }
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
        this.isFloatParam = false;
    }

    public ArmInstCall(ArmBlock block, ArmFunction func, boolean isFloatParam) {
        super(ArmInstKind.Call);
        this.func = func;
        block.asElementView().add(this);
        this.paramsCnt = func.getParamsCnt();
        // int defCnt = Integer.min(func.getParamsCnt(), 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        ops.add(new IPhyReg("r12"));
        ops.add(new FPhyReg("s0"));
        // if (func.isExternal()) {
        // ops.add(new IPhyReg("r12"));
        // }
        // if (isFloatParam) {
        // ops.add(new FPhyReg("s0"));
        // }
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
        this.isFloatParam = isFloatParam;
    }

    public ArmInstCall(ArmBlock block, String funcName, int paramsCnt) {
        super(ArmInstKind.Call);
        this.funcName = funcName;
        block.asElementView().add(this);
        this.paramsCnt = paramsCnt;
        // int defCnt = Integer.min(paramsCnt, 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        ops.add(new IPhyReg("r12")); // memset 和 memcpy不会?
        ops.add(new FPhyReg("s0"));
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
        this.isFloatParam = false;
    }

    public ArmInstCall(ArmBlock block, String funcName, int paramsCnt, boolean isFloatParam) {
        super(ArmInstKind.Call);
        this.funcName = funcName;
        block.asElementView().add(this);
        this.paramsCnt = paramsCnt;
        // int defCnt = Integer.min(paramsCnt, 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        ops.add(new IPhyReg("r12")); // memset 和 memcpy不会?
        ops.add(new FPhyReg("s0"));
        // if (isFloatParam) {
        // ops.add(new FPhyReg("s0"));
        // }
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
        this.isFloatParam = isFloatParam;
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
        if (isFloatParam) {
            ret.add(new FPhyReg(0));
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
            return "\t" + "bl" + "\t" + funcName + "\n";
        }
        return "\t" + "bl" + "\t" + func.getName() + "\n";
    }
}
