package top.origami404.ssyc.ir;

import java.util.*;
import java.util.stream.Collectors;

import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.type.FunctionIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;
import top.origami404.ssyc.utils.Log;

public class Function extends Value
    implements IListOwner<BasicBlock, Function>, AnalysisInfoOwner
{
    public Function(IRType returnType, List<Parameter> params, String funcName) {
        super(makeFunctionIRTypeFromParameters(returnType, params));
        super.setName('@' + funcName);

        this.bblocks = new IList<>(this);

        bblocks.asElementView().add(BasicBlock.createBBlockCO(this, funcName + "_entry"));
    }

    public String getFuncName() {
        return getName().substring(1);
    }

    @Override
    public FunctionIRTy getType() {
        Log.ensure(super.getType() instanceof FunctionIRTy);
        return (FunctionIRTy)super.getType();
    }

    @Override
    public IList<BasicBlock, Function> getIList() {
        return bblocks;
    }

    @Override
    public Map<String, AnalysisInfo> getInfoMap() {
        return analysisInfos;
    }

    // 外部函数将只有 name 与 parameters
    public boolean isExternal() {
        return isExternal;
    }

    public void markExternal() {
        this.isExternal = true;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public BasicBlock getEntryBBlock() {
        return bblocks.asElementView().get(0);
    }

    public Iterable<BasicBlock> getBasicBlocks() {
        return this.getIList().asElementView();
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        ensure(getName().charAt(0) == '@', "Name of a function must begin with '@'");

        final var blockList = bblocks.asElementView();
        final var labels = blockList.stream()
            .map(BasicBlock::getLabelName)
            .collect(Collectors.toList());
        ensure(unique(labels).equals(labels), "Labels of blocks must be unique");
        ensure(unique(blockList).equals(blockList), "Blocks in function must be unique");

        final var funcType = getType();
        final var paramTypes = parameters.stream().map(Parameter::getParamType).collect(Collectors.toList());
        ensure(funcType.getParamTypes().equals(paramTypes),
                "Parameters' type must match function type");
    }

    @Override
    public void verifyAll() throws IRVerifyException {
        super.verifyAll();

        for (final var param : parameters) {
            param.verifyAll();
        }

        for (final var blocks : bblocks.asElementView()) {
            blocks.verifyAll();
        }
    }

    private static <E> List<E> unique(List<E> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    private static FunctionIRTy makeFunctionIRTypeFromParameters(IRType returnType, List<Parameter> params) {
        final var paramTypes = params.stream().map(Parameter::getParamType).collect(Collectors.toList());
        return IRType.createFuncTy(returnType, paramTypes);
    }

    private List<Parameter> parameters;
    private IList<BasicBlock, Function> bblocks;
    private Map<String, AnalysisInfo> analysisInfos;
    private boolean isExternal = false;
}
