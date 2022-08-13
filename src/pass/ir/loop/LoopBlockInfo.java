package pass.ir.loop;

import ir.analysis.AnalysisInfo;

import java.util.Optional;

public class LoopBlockInfo implements AnalysisInfo {
    public boolean inLoop() {
        return loop.isPresent();
    }

    public NaturalLoop getLoop() {
        return loop.orElseThrow();
    }

    public Optional<NaturalLoop> getLoopOpt() {
        return loop;
    }

    void setLoop(NaturalLoop loop) {
        this.loop = Optional.of(loop);
    }

    private Optional<NaturalLoop> loop = Optional.empty();
}
