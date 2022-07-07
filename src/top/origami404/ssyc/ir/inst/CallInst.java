package top.origami404.ssyc.ir.inst;

import java.util.List;

import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Value;

public class CallInst extends Instruction {
    public CallInst(Function callee, List<Value> args) {
        super(InstKind.Call, callee.getType().getReturnType());

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
}
