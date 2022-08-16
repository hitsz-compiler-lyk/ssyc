package pass.backend;

import java.util.HashMap;
import java.util.Map;

import backend.arm.ArmInst;
import backend.arm.ArmInstCall;
import backend.arm.ArmInstMove;
import backend.arm.ArmInstStackLoad;
import backend.arm.ArmInstStackStore;
import backend.codegen.CodeGenManager;
import backend.operand.Operand;
import backend.operand.Reg;

public class Peephole implements BackendPass {

    private boolean peepholePass(CodeGenManager manager) {
        boolean done = true;
        for (var func : manager.getFunctions()) {
            for (var block : func.asElementView()) {
                for (var inst : block.asElementView()) {
                    var preInstOp = inst.getINode().getPrev();
                    var preInst = preInstOp.isPresent() ? preInstOp.get().getValue() : null;
                    var nxtInstOp = inst.getINode().getNext();
                    var nxtInst = nxtInstOp.isPresent() ? nxtInstOp.get().getValue() : null;

                    // str a, [b, x]
                    // ldr c, [b, x]
                    // replace
                    // str a, [b, x]
                    // mov c, a
                    // 这个情况理论上是用于处理寄存器分配的栈存取
                    if (inst instanceof ArmInstStackLoad) {
                        var load = (ArmInstStackLoad) inst;
                        if (preInst != null && preInst instanceof ArmInstStackStore) {
                            var store = (ArmInstStackStore) preInst;
                            boolean isSameAddr = store.getAddr().equals(load.getAddr());
                            boolean isSameOffset = store.getOffset().equals(load.getOffset());// 和比较true offset等价
                            boolean isNoCond = store.getCond().equals(ArmInst.ArmCondType.Any);
                            if (isSameAddr && isSameOffset && isNoCond) {
                                if (!load.getDst().equals(store.getDst())) {
                                    var move = new ArmInstMove(load.getDst(), store.getDst());
                                    move.setCond(move.getCond());
                                    load.insertBeforeCO(move);
                                }
                                load.freeFromIList();
                                done = false;
                            }
                        }
                    }

                    // 删除 mov a, a
                    if (inst instanceof ArmInstMove) {
                        var move = (ArmInstMove) inst;
                        if (move.getDst().equals(move.getSrc()) && move.getShift() == null) {
                            move.freeFromIList();
                            done = false;
                        }
                    }
                }
            }
        }
        return done;
    }

    private boolean clearNotUseInst(CodeGenManager manager) {
        boolean done = true;
        for (var func : manager.getFunctions()) {
            for (var block : func.asElementView()) {
                Map<Operand, ArmInst> instMap = new HashMap<>();
                for (var inst : block.asElementView()) {
                    for (var use : inst.getRegUse()) {
                        if (use instanceof Reg) {
                            instMap.remove(use);
                        }
                    }
                    for (var def : inst.getRegDef()) {
                        if (def instanceof Reg) {
                            if (instMap.containsKey(def)) {
                                var pre = instMap.get(def);
                                instMap.remove(def);
                                if (!(pre instanceof ArmInstCall)) {
                                    pre.freeFromIList();
                                    done = false;
                                }
                            }
                        }
                    }
                    for (var def : inst.getRegDef()) {
                        if (def instanceof Reg) {
                            instMap.put(def, inst);
                        }
                    }
                }
            }
        }
        return done;
    }

    @Override
    public void runPass(CodeGenManager manager) {
        clearNotUseInst(manager);
        boolean done = false;
        while (!done) {
            done = true;
            done &= peepholePass(manager);
            done &= clearNotUseInst(manager);
        }
    }

}
