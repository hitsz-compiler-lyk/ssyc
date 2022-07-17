package top.origami404.ssyc.ir;

import java.util.*;
import java.util.stream.Collectors;

import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.type.FunctionIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.*;

public class Function extends Value
    implements IListOwner<BasicBlock, Function>, AnalysisInfoOwner
{
    public Function(IRType returnType, List<Parameter> params, String funcName) {
        super(makeFunctionIRTypeFromParameters(returnType, params));
        super.setName('@' + funcName);
        this.isExternal = false;

        this.parameters = params;

        this.bblocks = new IList<>(this);
        BasicBlock.createBBlockCO(this, funcName + "_entry");

        this.analysisInfos = new HashMap<>();
    }

    public Function(IRType funcType, String funcName) {
        super(funcType);
        Log.ensure(super.getType() instanceof FunctionIRTy);

        super.setName('@' + funcName);
        this.isExternal = true;
    }

    public String getFuncName() {
        return getName().substring(1);
    }

    @Override
    public FunctionIRTy getType() {
        return (FunctionIRTy)super.getType();
    }

    @Override
    public IList<BasicBlock, Function> getIList() {
        ensureNotExternal();
        return bblocks;
    }

    @Override
    public Map<String, AnalysisInfo> getInfoMap() {
        ensureNotExternal();
        return analysisInfos;
    }

    // 外部函数将只有 name 与 type
    public boolean isExternal() {
        return isExternal;
    }

    public List<Parameter> getParameters() {
        ensureNotExternal();
        return parameters;
    }

    public BasicBlock getEntryBBlock() {
        ensureNotExternal();
        return bblocks.get(0);
    }

    public Iterable<BasicBlock> getBasicBlocks() {
        ensureNotExternal();
        return this.getIList();
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        ensure(getName().charAt(0) == '@', "Name of a function must begin with '@'");

        if (isExternal) {
            return;
        }

        final var blockList = bblocks;
        final var labels = blockList.stream()
            .map(BasicBlock::getLabelName)
            .collect(Collectors.toList());
        ensure(IteratorTools.isUnique(labels), "Labels of blocks must be unique");
        ensure(IteratorTools.isUnique(blockList), "Blocks in function must be unique");

        final var funcType = getType();
        final var paramTypes = parameters.stream().map(Parameter::getParamType).collect(Collectors.toList());
        ensure(funcType.getParamTypes().equals(paramTypes),
                "Parameters' type must match function type");

        try {
            getIList().verify();
        } catch (IListException e) {
            throw new IRVerifyException(this, "IList exception", e);
        }
    }

    @Override
    public void verifyAll() throws IRVerifyException {
        super.verifyAll();

        if (isExternal) {
            return;
        }

        for (final var param : parameters) {
            param.verifyAll();
        }

        for (final var blocks : bblocks) {
            blocks.verifyAll();
        }
    }

    private void ensureNotExternal() {
        ensureNot(isExternal, "Function should NOT be external");
    }

    private static FunctionIRTy makeFunctionIRTypeFromParameters(IRType returnType, List<Parameter> params) {
        final var paramTypes = params.stream().map(Parameter::getParamType).collect(Collectors.toList());
        return IRType.createFuncTy(returnType, paramTypes);
    }

    private List<Parameter> parameters;
    private IList<BasicBlock, Function> bblocks;
    private Map<String, AnalysisInfo> analysisInfos;
    private final boolean isExternal;
}
