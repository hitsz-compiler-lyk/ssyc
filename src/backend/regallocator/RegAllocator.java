package backend.regallocator;

import java.util.Map;

import backend.arm.ArmFunction;
import backend.operand.Reg;

public interface RegAllocator {
    String getName();

    Map<Reg, Reg> run(ArmFunction func);
}
