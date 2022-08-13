package pass.ir.loop;

import ir.analysis.AnalysisInfo;

import java.util.*;
import java.util.stream.Collectors;

public class LoopFunctionInfo implements AnalysisInfo {
    public void addLoop(NaturalLoop loop) {
        loops.add(loop);
    }

    public List<NaturalLoop> getLoops() {
        return Collections.unmodifiableList(loops);
    }

    public List<NaturalLoop> getAllLoopsInPostOrder() {
        return loops.stream()
            .map(NaturalLoop::allLoopInPostOrder)
            .flatMap(List::stream).collect(Collectors.toList());
    }

    List<NaturalLoop> loops = new ArrayList<>();
}
