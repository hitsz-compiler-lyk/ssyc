package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmFunction;
import backend.lir.operand.FPhyReg;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;
import backend.lir.operand.Reg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ArmInstCall extends ArmInst {
    private final ArmFunction func;
    private final String funcName;
    private final int intParamsCnt;
    private final int floatParamCnt;

    public ArmInstCall(ArmBlock block, ArmFunction func) {
        super(ArmInstKind.Call);
        this.func = func;
        this.funcName = func.getName();
        this.intParamsCnt = func.getParamsCnt();
        this.floatParamCnt = func.getFparamsCnt();

        block.add(this);
        addCallerSaveRegsToDef();
    }

    public ArmInstCall(ArmBlock block, String funcName, int paramsCnt, int floatParamCnt) {
        super(ArmInstKind.Call);
        this.func = null;
        this.funcName = funcName;
        this.intParamsCnt = Integer.min(paramsCnt, 4);
        this.floatParamCnt = Integer.min(floatParamCnt, 16);

        block.add(this);
        addCallerSaveRegsToDef();
    }

    private void addCallerSaveRegsToDef() {
        final var ops = new ArrayList<Operand>();

        for (int i = 0; i < 4; i++) {
            ops.add(IPhyReg.R(i));
        }

        ops.add(IPhyReg.LR);
        // 对于外部函数, 链接器有可能会使用 R12 (IP) 寄存器
        // 因此调用前后 R12 的值也不能保证, 需要加入 def 中
        ops.add(IPhyReg.R(12));

        for (int i = 0; i < 16; i++) {
            ops.add(FPhyReg.S(i));
        }

        this.initOperands(ops.toArray(Operand[]::new));
    }

    public ArmFunction getFunc() { return func; }

    public String getFuncName() {
        return funcName;
    }

    @Override
    public Set<Reg> getRegUse() {
        final var ret = new HashSet<Reg>();
        for (int i = 0; i < Integer.min(this.intParamsCnt, 4); i++) {
            ret.add(IPhyReg.R(i));
        }
        for (int i = 0; i < Integer.min(this.floatParamCnt, 16); i++) {
            ret.add(FPhyReg.S(i));
        }
        return ret;
    }
}
