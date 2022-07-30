package top.origami404.ssyc.pass.ir.dataflow;

import top.origami404.ssyc.ir.analysis.AnalysisInfo;

public class DataFlowInfo<T> implements AnalysisInfo {
    public T in() { return in; }
    public T out() { return out; }
    boolean needUpdate() { return needUpdate; }

    T in;
    T out;
    boolean needUpdate;
}
