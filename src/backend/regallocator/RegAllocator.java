package backend.regallocator;

import backend.lir.ArmFunction;
import backend.lir.operand.Reg;

import java.util.Map;

public interface RegAllocator {
    Map<Reg, Reg> run(ArmFunction func);
}
