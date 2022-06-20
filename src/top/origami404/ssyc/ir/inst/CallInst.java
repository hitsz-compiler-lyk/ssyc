package top.origami404.ssyc.ir.inst;

import java.util.List;

import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Value;

public class CallInst extends Instruction {
    CallInst(Function callee, List<Value> args) {
        super(InstKind.Call, callee.getType().getReturnType());

        this.callee = callee;
        this.argList = args;

        super.addOperandCO(callee);
        super.addAllOperandsCO(args);
    }

    public Function getCallee() {
        return callee;
    }

    public List<Value> getArgList() {
        return argList;
    }

    public Value getArg(int i) {
        return getArgList().get(i);
    }

    private Function callee;
    private List<Value> argList;
}
