package backend.regallocator;

import backend.lir.ArmFunction;
import backend.lir.operand.Reg;

import java.util.Map;

public interface RegAllocator {
    // 原本打算实现几种寄存器分配算法, 所以抽了一个接口, 但是由于时间关系, 最终只实现了一种
    String getName();

    Map<Reg, Reg> run(ArmFunction func);
}
