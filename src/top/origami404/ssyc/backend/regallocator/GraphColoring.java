package top.origami404.ssyc.backend.regallocator;

import top.origami404.ssyc.backend.Consts;
import top.origami404.ssyc.backend.codegen.CodeGenManager;

public class GraphColoring implements RegAllocator {

    @Override
    public String getName() {
        return Consts.GraphColoring;
    }

    @Override
    public void run(CodeGenManager manager) {
        for (var func : manager.getFunctions()) {
            var done = false;

            while (!done) {
                new LivenessAnalysis().funcLivenessAnalysis(func);

            }
        }
    }

}
