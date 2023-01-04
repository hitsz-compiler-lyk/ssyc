package ir;

import frontend.SourceCodeSymbol;
import ir.analysis.AnalysisInfo;
import ir.analysis.AnalysisInfoOwner;
import ir.type.FunctionIRTy;
import ir.type.IRType;
import utils.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Function extends Value
    implements IListOwner<BasicBlock, Function>, AnalysisInfoOwner
{
    public Function(IRType returnType, List<Parameter> params, SourceCodeSymbol symbol) {
        super(makeFunctionIRTypeFromParameters(returnType, params));
        super.setSymbol(symbol);
        this.isExternal = false;

        this.parameters = params;
        this.bblocks = new IList<>(this);
        this.analysisInfos = new HashMap<>();
    }

    public Function(IRType funcType, SourceCodeSymbol symbol) {
        super(funcType);
        super.setSymbol(symbol);
        Log.ensure(super.getType() instanceof FunctionIRTy);

        this.isExternal = true;
    }

    public String getFunctionSourceName() {
        return getSymbol().getName();
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

    /** @deprecated 现在函数本身就是一个 {@code List<BasicBlock>} 了, 可以直接使用 {@code for (final var block : func)} */
    @Deprecated
    public List<BasicBlock> getBasicBlocks() {
        ensureNotExternal();
        return this.getIList();
    }

    @Override
    public IList<BasicBlock, Function>.IListElementIterator listIterator(final int index) {
        ensureNotExternal();
        return IListOwner.super.listIterator(index);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        ensure(getSymbolOpt().isPresent(), "A function must own a symbol");

        if (isExternal) {
            return;
        }

        final var blockList = bblocks;
        final var labels = blockList.stream()
            .map(BasicBlock::getSymbol)
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
            throw IRVerifyException.create(this, "IList exception", e);
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
