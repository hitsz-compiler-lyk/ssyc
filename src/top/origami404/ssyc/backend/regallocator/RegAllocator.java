package top.origami404.ssyc.backend.regallocator;

import java.util.Map;

import top.origami404.ssyc.backend.arm.ArmFunction;
import top.origami404.ssyc.backend.operand.Reg;

public interface RegAllocator {
    String getName();

    Map<Reg, Reg> run(ArmFunction func);
}
