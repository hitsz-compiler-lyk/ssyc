package ir.analysis;

public interface AnalysisInfo {
    default String getInfoName() {
        return this.getClass().getSimpleName();
    }
}
