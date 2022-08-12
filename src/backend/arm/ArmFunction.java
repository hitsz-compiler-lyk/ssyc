package backend.arm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import backend.operand.FPhyReg;
import backend.operand.IPhyReg;
import backend.operand.Operand;
import ir.Parameter;
import utils.IList;
import utils.IListOwner;

public class ArmFunction implements IListOwner<ArmBlock, ArmFunction> {
    private int stackSize;
    private int finalstackSize;
    private int fparamsCnt; // 最大值16
    private int iparamsCnt; // 最大值4
    private ArmBlock prologue;
    private List<Parameter> parameter;
    private List<IPhyReg> iUsedRegs;
    private List<FPhyReg> fUsedRegs;
    private List<Integer> stackObject;
    private List<Integer> stackObjectOffset;
    private Map<Operand, ArmInstParamLoad> paramLoadMap;
    private Map<Operand, ArmInstLoad> addrLoadMap;
    private Map<Operand, ArmInstStackAddr> stackAddrtMap;
    private Map<Operand, ArmInstMove> immMap;

    public int getStackSize() {
        return stackSize;
    }

    public ArmBlock getPrologue() {
        return prologue;
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

    public int getFparamsCnt() {
        return fparamsCnt;
    }

    public void setFparamsCnt(int fparamsCnt) {
        this.fparamsCnt = fparamsCnt;
    }

    public int getIparamsCnt() {
        return iparamsCnt;
    }

    public void setIparamsCnt(int iparamsCnt) {
        this.iparamsCnt = iparamsCnt;
    }

    private String name;
    private IList<ArmBlock, ArmFunction> blocks;
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

    public int getParamsCnt() {
        return paramsCnt;
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

    public Map<Operand, ArmInstParamLoad> getParamLoadMap() {
        return paramLoadMap;
    }

    public Map<Operand, ArmInstLoad> getAddrLoadMap() {
        return addrLoadMap;
    }

    public Map<Operand, ArmInstStackAddr> getStackAddrtMap() {
        return stackAddrtMap;
    }

    public Map<Operand, ArmInstMove> getImmMap() {
        return immMap;
    }

    public ArmFunction(String name) {
        this.name = name;
        this.blocks = new IList<>(this);
        this.paramsCnt = 0;
        this.isReturnFloat = false;
        this.isExternal = false;
        this.stackSize = 0;
        this.finalstackSize = 0;
        this.iUsedRegs = new ArrayList<>();
        this.fUsedRegs = new ArrayList<>();
        this.stackObject = new ArrayList<>();
        this.stackObjectOffset = new ArrayList<>();
        this.addrLoadMap = new HashMap<>();
        this.paramLoadMap = new HashMap<>();
        this.stackAddrtMap = new HashMap<>();
        this.immMap = new HashMap<>();
        this.prologue = new ArmBlock(this, this.name + "_prologue");
    }

    public ArmFunction(String name, int paramsCnt) {
        this.name = name;
        this.blocks = new IList<ArmBlock, ArmFunction>(this);
        this.paramsCnt = paramsCnt;
        this.isReturnFloat = false;
        this.isExternal = false;
        this.stackSize = 0;
        this.finalstackSize = 0;
        this.iUsedRegs = new ArrayList<>();
        this.fUsedRegs = new ArrayList<>();
        this.stackObject = new ArrayList<>();
        this.stackObjectOffset = new ArrayList<>();
        this.addrLoadMap = new HashMap<>();
        this.paramLoadMap = new HashMap<>();
        this.stackAddrtMap = new HashMap<>();
        this.immMap = new HashMap<>();
        this.prologue = new ArmBlock(this, this.name + "_prologue");
    }
}
