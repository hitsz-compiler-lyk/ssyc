package backend.regallocator;

import backend.lir.ArmFunction;
import backend.lir.operand.Reg;

import java.util.HashSet;

public class LivenessAnalysis {
    public static void funcLivenessAnalysis(ArmFunction func) {
        for (var block : func.asElementView()) {
            var blockLiveInfo = block.getBlockLiveInfo();
            blockLiveInfo.clear();

            for (var inst : block.asElementView()) {
                for (var reg : inst.getRegUse()) {
                    if (!blockLiveInfo.getLiveDef().contains(reg)) {
                        blockLiveInfo.getLiveUse().add(reg);
                    }
                }

                for (var reg : inst.getRegDef()) {
                    if (!blockLiveInfo.getLiveUse().contains(reg)) {
                        blockLiveInfo.getLiveDef().add(reg);
                    }
                }
            }

            blockLiveInfo.getLiveIn().addAll(blockLiveInfo.getLiveUse());
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (var block : func.asElementView()) {
                var blockLiveInfo = block.getBlockLiveInfo();
                var liveOut = new HashSet<Reg>();

                for (var succ : block.getSucc()) {
                    liveOut.addAll(succ.getBlockLiveInfo().getLiveIn());
                }

                if (!liveOut.equals(blockLiveInfo.getLiveOut())) {
                    changed = true;
                    blockLiveInfo.setLiveOut(liveOut);
                    for (var reg : blockLiveInfo.getLiveOut()) {
                        if (!blockLiveInfo.getLiveDef().contains(reg)) {
                            blockLiveInfo.getLiveIn().add(reg);
                        }
                    }
                }
            }
        }
    }
}
