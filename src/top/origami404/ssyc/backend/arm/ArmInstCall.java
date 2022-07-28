package top.origami404.ssyc.backend.arm;

import java.util.ArrayList;
import java.util.List;

import top.origami404.ssyc.backend.operand.IPhyReg;
import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.StringUtils;

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
        int defCnt = Integer.min(func.getParamsCnt(), 4);
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        this.initOperands(ops.toArray(new Operand[ops.size()]));
    }

    public ArmInstCall(ArmBlock block, String funcName, int paramsCnt) {
        super(ArmInstKind.Call);
        this.funcName = funcName;
        block.asElementView().add(this);
        int defCnt = Integer.min(paramsCnt, 4);
        List<Operand> ops = new ArrayList<>();
        for (int i = 0; i < defCnt; i++) {
            ops.add(new IPhyReg(i));
        }
        ops.add(new IPhyReg("lr"));
        this.initOperands(ops.toArray(new Operand[ops.size()]));
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
