package pass.ir.loop;

import ir.analysis.AnalysisInfo;

import java.util.Optional;

public class JustLoopBlockInfo implements AnalysisInfo {
    public JustLoopBlockInfo() {
        this(null);
    }

    public JustLoopBlockInfo(JustLoop loop) {
        this.loop = Optional.ofNullable(loop);
    }

    void setLoop(JustLoop loop) {
        this.loop = Optional.ofNullable(loop);
    }

    public Optional<JustLoop> getLoop() {
        return loop;
    }

    public int getLoopDepth() {
        return loop.map(JustLoop::getLoopDepth).orElse(0);
    }

    private Optional<JustLoop> loop;
}
