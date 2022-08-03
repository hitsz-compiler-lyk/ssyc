package backend.arm;

import java.util.ArrayList;
import java.util.List;

import backend.operand.FPhyReg;
import backend.operand.IPhyReg;
import backend.operand.Operand;
import utils.StringUtils;

public class ArmInstCall extends ArmInst {
    private ArmFunction func;
    private String funcName;

    public ArmInstCall(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstCall(ArmBlock block, ArmFunction func) {
        super(ArmInstKind.Call);
        this.func = func;
        block.asElementView().add(this);
        // int defCnt = Integer.min(func.getParamsCnt(), 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        if (func.isExternal()) {
            ops.add(new IPhyReg("r12"));
        }
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
    }

    public ArmInstCall(ArmBlock block, ArmFunction func, boolean isFloat) {
        super(ArmInstKind.Call);
        this.func = func;
        block.asElementView().add(this);
        // int defCnt = Integer.min(func.getParamsCnt(), 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        if (func.isExternal()) {
            ops.add(new IPhyReg("r12"));
        }
        if (isFloat) {
            ops.add(new FPhyReg("s0"));
        }
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
    }

    public ArmInstCall(ArmBlock block, String funcName, int paramsCnt) {
        super(ArmInstKind.Call);
        this.funcName = funcName;
        block.asElementView().add(this);
        // int defCnt = Integer.min(paramsCnt, 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        ops.add(new IPhyReg("r12")); // memset 和 memcpy不会?
        this.initOperands(ops.toArray(new Operand[ops.size()]));
        this.setPrintCnt(1);
    }

    public ArmInstCall(ArmBlock block, String funcName, int paramsCnt, boolean isFloat) {
        super(ArmInstKind.Call);
        this.funcName = funcName;
        block.asElementView().add(this);
        // int defCnt = Integer.min(paramsCnt, 4);
        int defCnt = 4;
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        // 如果是外部函数 则会因为链接器从而把r12定值
        ops.add(new IPhyReg("r12")); // memset 和 memcpy不会?
        if (isFloat) {
            ops.add(new FPhyReg("s0"));
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
    public String print() {
        if (!StringUtils.isEmpty(funcName)) {
            return "\t" + "bl" + "\t" + funcName + "\n";
        }
        return "\t" + "bl" + "\t" + func.getName() + "\n";
    }
}
