package backend.regallocator;

import backend.arm.ArmFunction;
import backend.operand.Reg;

import java.util.Map;

public interface RegAllocator {
    String getName();

    Map<Reg, Reg> run(ArmFunction func);
}
