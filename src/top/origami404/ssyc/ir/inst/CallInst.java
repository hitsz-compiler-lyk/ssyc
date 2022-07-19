package top.origami404.ssyc.ir.inst;

import java.util.List;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.FunctionIRTy;

public class CallInst extends Instruction {
    public CallInst(BasicBlock block, Function callee, List<Value> args) {
        super(block, InstKind.Call, callee.getType().getReturnType());

        super.addOperandCO(callee);
        super.addAllOperandsCO(args);
    }

    public Function getCallee() {
        return getOperand(0).as(Function.class);
    }
    public List<Value> getArgList() {
        return getOperands().subList(1, getOperandSize());
    }
    public Value getArg(int i) {
        return getArgList().get(i);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var funcType = (FunctionIRTy) getCallee().getType();
        ensure(funcType.getReturnType().equals(getType()), "Type of Call must be same as the return type of callee");

        final var argCnt = getArgList().size();
        ensure(argCnt == funcType.getParamTypes().size(),
                "Amount of argument must match the amount of function parameter");
        for (var i = 0; i < argCnt; i++) {
            ensure(getArg(i).getType().equals(funcType.getParamType(i)),
                    "Type of argument must match type of parameter (Dismatch %s at %d)"
                            .formatted(getCallee().getFunctionSourceName(), i));
        }
    }
}
