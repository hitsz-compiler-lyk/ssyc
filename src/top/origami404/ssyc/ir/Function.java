package top.origami404.ssyc.ir;

import java.util.List;
import java.util.Map;
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
    public Function(IRType returnType, List<Parameter> params, String name) {
        super(makeFunctionIRTypeFromParameters(returnType, params));
        this.name = name;
        this.bblocks = new IList<>(this);

        bblocks.asElementView().add(BasicBlock.createBBlockCO(this, "entry"));
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

    public String getName() {
        return name;
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

    private static FunctionIRTy makeFunctionIRTypeFromParameters(IRType returnType, List<Parameter> params) {
        final var paramTypes = params.stream().map(Parameter::getParamType).collect(Collectors.toList());
        return IRType.createFuncTy(returnType, paramTypes);
    }

    private String name;
    private List<Parameter> parameters;
    private IList<BasicBlock, Function> bblocks;
    private Map<String, AnalysisInfo> analysisInfos;
    private boolean isExternal = false;
}
