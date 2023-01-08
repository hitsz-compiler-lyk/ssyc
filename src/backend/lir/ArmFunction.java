package backend.lir;

import backend.lir.inst.*;
import backend.lir.operand.FPhyReg;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;
import ir.Parameter;
import utils.IList;
import utils.IListOwner;

import java.util.*;

public class ArmFunction implements IListOwner<ArmBlock, ArmFunction> {
    private int stackSize;
    private int finalStackSize;
    private int fparamsCnt; // 最大值16
    private int iparamsCnt; // 最大值4
    private final ArmBlock prologue;
    private List<Parameter> parameter;
    private final List<IPhyReg> iUsedRegs;
    private final List<FPhyReg> fUsedRegs;
    private final List<Integer> stackObject;
    private final List<Integer> stackObjectOffset;
    private final Map<Operand, ArmInstParamLoad> paramLoadMap;
    private final Map<Operand, ArmInstLoad> addrLoadMap;
    private final Map<Operand, ArmInstStackAddr> stackAddrMap;
    private final Map<Operand, ArmInstStackLoad> stackLoadMap;
    private final Map<Operand, ArmInstMove> immMap;
    private final Set<Operand> spillNodes;
    private final Set<Operand> stackStoreSet;

    public int getStackSize() {
        return stackSize;
    }

    public ArmBlock getPrologue() {
        return prologue;
    }

    public List<Parameter> getParameter() {
        return parameter;
    }

    public List<IPhyReg> getIUsedRegs() {
        return iUsedRegs;
    }

    public List<FPhyReg> getFUsedRegs() {
        return fUsedRegs;
    }

    public int getFinalStackSize() {
        return finalStackSize;
    }

    public List<Integer> getStackObject() {
        return stackObject;
    }

    public List<Integer> getStackObjectOffset() {
        return stackObjectOffset;
    }

    public void setParameter(List<Parameter> parameter) {
        this.parameter = parameter;
    }

    public void setFinalStackSize(int finalStackSize) {
        this.finalStackSize = finalStackSize;
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

    private final String name;
    private final IList<ArmBlock, ArmFunction> blocks;
    private final int paramsCnt;
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

    public void setExternal(boolean isExternal) {
        this.isExternal = isExternal;
    }

    public Map<Operand, ArmInstParamLoad> getParamLoadMap() {
        return paramLoadMap;
    }

    public Map<Operand, ArmInstLoad> getAddrLoadMap() {
        return addrLoadMap;
    }

    public Map<Operand, ArmInstStackAddr> getStackAddrMap() {
        return stackAddrMap;
    }

    public Map<Operand, ArmInstStackLoad> getStackLoadMap() {
        return stackLoadMap;
    }

    public Map<Operand, ArmInstMove> getImmMap() {
        return immMap;
    }

    public Set<Operand> getSpillNodes() {
        return spillNodes;
    }

    public Set<Operand> getStackStoreSet() {
        return stackStoreSet;
    }

    public boolean isExternal() {
        return isExternal;
    }

    public boolean isReturnFloat() {
        return isReturnFloat;
    }

    public ArmFunction(String name) {
        this(name, 0);
    }

    public ArmFunction(String name, int paramsCnt) {
        this.name = name;
        this.blocks = new IList<>(this);
        this.paramsCnt = paramsCnt;
        this.isReturnFloat = false;
        this.isExternal = false;
        this.stackSize = 0;
        this.finalStackSize = 0;
        this.iUsedRegs = new ArrayList<>();
        this.fUsedRegs = new ArrayList<>();
        this.stackObject = new ArrayList<>();
        this.stackObjectOffset = new ArrayList<>();
        this.addrLoadMap = new HashMap<>();
        this.paramLoadMap = new HashMap<>();
        this.stackAddrMap = new HashMap<>();
        this.stackLoadMap = new HashMap<>();
        this.immMap = new HashMap<>();
        this.spillNodes = new HashSet<>();
        this.stackStoreSet = new HashSet<>();
        this.prologue = new ArmBlock(this, this.name + "_prologue");
    }
}
