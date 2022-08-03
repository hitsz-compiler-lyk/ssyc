package backend.arm;

import java.util.ArrayList;
import java.util.List;

import backend.operand.FPhyReg;
import backend.operand.IPhyReg;
import ir.Parameter;
import utils.IList;
import utils.IListOwner;

public class ArmFunction implements IListOwner<ArmBlock, ArmFunction> {
    public static class FunctionInfo {
        private int stackSize;
        private int finalstackSize;
        private ArmBlock prologue;
        private ArmFunction func;
        private List<Parameter> parameter;
        private List<IPhyReg> iUsedRegs;
        private List<FPhyReg> fUsedRegs;
        private List<Integer> stackObject;
        private List<Integer> stackObjectOffset;

        public FunctionInfo(ArmFunction func) {
            this.func = func;
            this.stackSize = 0;
            this.finalstackSize = 0;
            this.prologue = new ArmBlock(func, func.name + "_prologue");
            this.iUsedRegs = new ArrayList<>();
            this.fUsedRegs = new ArrayList<>();
            this.stackObject = new ArrayList<>();
            this.stackObjectOffset = new ArrayList<>();
        }

        public int getStackSize() {
            return stackSize;
        }

        public ArmBlock getPrologue() {
            return prologue;
        }

        public ArmFunction getFunc() {
            return func;
        }

        public List<Parameter> getParameter() {
            return parameter;
        }

        public List<IPhyReg> getiUsedRegs() {
            return iUsedRegs;
        }

        public List<FPhyReg> getfUsedRegs() {
            return fUsedRegs;
        }

        public int getFinalstackSize() {
            return finalstackSize;
        }

        public List<Integer> getStackObject() {
            return stackObject;
        }

        public List<Integer> getStackObjectOffset() {
            return stackObjectOffset;
        }

        public void setPrologue(ArmBlock prologue) {
            this.prologue = prologue;
        }

        public void setParameter(List<Parameter> parameter) {
            this.parameter = parameter;
        }

        public void setFinalstackSize(int finalstackSize) {
            this.finalstackSize = finalstackSize;
        }

        public void addStackSize(int n) {
            this.stackObjectOffset.add(this.stackSize);
            this.stackSize += n;
            this.stackObject.add(n);
        }
    }

    private String name;
    private IList<ArmBlock, ArmFunction> blocks;
    private FunctionInfo funcInfo;
    private int paramsCnt;
    private boolean isReturnFloat;
    private boolean isExternal;

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

    public int getParamsCnt() {
        return paramsCnt;
    }

    public void addStackSize(int n) {
        this.funcInfo.addStackSize(n);
    }

    public void setReturnFloat(boolean isReturnFloat) {
        this.isReturnFloat = isReturnFloat;
    }

    public boolean isReturnFloat() {
        return isReturnFloat;
    }

    public void setExternal(boolean isExternal) {
        this.isExternal = isExternal;
    }

    public boolean isExternal() {
        return isExternal;
    }

    public ArmFunction(String name) {
        this.name = name;
        this.blocks = new IList<>(this);
        this.funcInfo = new FunctionInfo(this);
        this.paramsCnt = 0;
        this.isReturnFloat = false;
        this.isExternal = false;
    }

    public ArmFunction(String name, int paramsCnt) {
        this.name = name;
        this.blocks = new IList<ArmBlock, ArmFunction>(this);
        this.funcInfo = new FunctionInfo(this);
        this.paramsCnt = paramsCnt;
        this.isReturnFloat = false;
        this.isExternal = false;
    }
}
