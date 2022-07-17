package top.origami404.ssyc.backend.arm;

import java.util.List;

import top.origami404.ssyc.ir.Parameter;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;

public class ArmFunction implements IListOwner<ArmBlock, ArmFunction> {
    public static class FunctionInfo {
        private int stackSize;
        private ArmBlock startBlock, endBlock;
        private ArmFunction func;
        private List<Parameter> parameter;

        public FunctionInfo(ArmFunction func) {
            this.func = func;
            this.stackSize = 0;
            this.startBlock = new ArmBlock(func, "." + func.name + ".startBlock");
            this.endBlock = new ArmBlock("." + func.name + ".endBlock");
        }

        public int getStackSize() {
            return stackSize;
        }

        public ArmBlock getStartBlock() {
            return startBlock;
        }

        public ArmBlock getEndBlock() {
            return endBlock;
        }

        public ArmFunction getFunc() {
            return func;
        }

        public List<Parameter> getParameter() {
            return parameter;
        }

        public void setStartBlock(ArmBlock startBlock) {
            this.startBlock = startBlock;
        }

        public void setEndBlock(ArmBlock endBlock) {
            this.endBlock = endBlock;
        }

        public void setParameter(List<Parameter> parameter) {
            this.parameter = parameter;
        }

        public void addStackSize(int n) {
            this.stackSize += n;
        }
    }

    private String name;

    private IList<ArmBlock, ArmFunction> blocks;

    private FunctionInfo funcInfo;

    @Override
    public String toString() {
        return "";
    }

    @Override
    public IList<ArmBlock, ArmFunction> getIList() {
        return blocks;
    }

    public String getName() {
        return name;
    }

    public FunctionInfo getFuncInfo() {
        return funcInfo;
    }

    public int getStackSize() {
        return this.funcInfo.getStackSize();
    }

    public void addStackSize(int n) {
        this.funcInfo.addStackSize(n);
    }

    public ArmFunction(String name) {
        this.name = name;
        this.blocks = new IList<ArmBlock, ArmFunction>(this);
        this.funcInfo = new FunctionInfo(this);
    }
}
