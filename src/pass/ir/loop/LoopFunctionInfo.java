package pass.ir.loop;

import ir.analysis.AnalysisInfo;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class LoopFunctionInfo implements AnalysisInfo {
    public void addLoop(NaturalLoop loop) {
        loops.add(loop);
    }

    public Set<NaturalLoop> getLoops() {
        return Collections.unmodifiableSet(loops);
    }

    Set<NaturalLoop> loops = new LinkedHashSet<>();
}
