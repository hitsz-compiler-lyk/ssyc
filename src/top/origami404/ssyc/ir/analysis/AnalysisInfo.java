package top.origami404.ssyc.ir.analysis;

public interface AnalysisInfo {
    default String getInfoName() {
        return this.getClass().getSimpleName();
    }
}
