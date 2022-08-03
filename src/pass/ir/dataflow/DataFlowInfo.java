package pass.ir.dataflow;

import ir.analysis.AnalysisInfo;

public class DataFlowInfo<T> implements AnalysisInfo {
    public T in() { return in; }
    public T out() { return out; }
    boolean needUpdate() { return needUpdate; }

    T in;
    T out;
    boolean needUpdate;
}
