package pass.backend;

import java.util.HashMap;
import java.util.Map;

import backend.arm.ArmInst;
import backend.arm.ArmInstBinary;
import backend.arm.ArmInstBranch;
import backend.arm.ArmInstCall;
import backend.arm.ArmInstMove;
import backend.arm.ArmInstStackLoad;
import backend.arm.ArmInstStackStore;
import backend.arm.ArmInst.ArmInstKind;
import backend.codegen.CodeGenManager;
import backend.operand.Operand;
import backend.operand.Reg;
import utils.Log;

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

                    if (inst instanceof ArmInstBranch) {
                        var branch = (ArmInstBranch) inst;
                        var nxtBlockOp = block.getINode().getNext();
                        var nxtBlock = nxtBlockOp.isPresent() ? nxtBlockOp.get().getValue() : null;
                        boolean isEqualBlock = branch.getTargetBlock().equals(nxtBlock);
                        if (branch.getCond().equals(ArmInst.ArmCondType.Any)) {
                            if (isEqualBlock) {
                                branch.freeFromIList();
                                done = false;
                            }
                        } else if (nxtInst != null && nxtInst instanceof ArmInstBranch) {
                            var nxtBranch = (ArmInstBranch) nxtInst;
                            boolean isNxtBranchAny = nxtBranch.getCond().equals(ArmInst.ArmCondType.Any);
                            if (isEqualBlock && isNxtBranchAny) {
                                var newBranch = new ArmInstBranch(nxtBranch.getTargetBlock(),
                                        branch.getCond().getOppCondType());
                                branch.insertBeforeCO(newBranch);
                                branch.freeFromIList();
                                nxtBranch.freeFromIList();
                                var trueBlock = block.getTrueSuccBlock();
                                var falseBlock = block.getFalseSuccBlock();
                                Log.ensure(trueBlock != null && falseBlock != null, "true false succ block exist null");
                                block.setFalseSuccBlock(trueBlock);
                                block.setTrueSuccBlock(falseBlock);
                            }
                        }
                    }

                    if (inst instanceof ArmInstStackLoad) {
                        var load = (ArmInstStackLoad) inst;
                        if (preInst != null && preInst instanceof ArmInstStackStore) {
                            // str a, [b, imm]
                            // ldr c, [b, imm]
                            // =>
                            // str a, [b, imm]
                            // mov c, a
                            // 这个情况理论上是用于处理寄存器分配的栈存取
                            var store = (ArmInstStackStore) preInst;
                            boolean isEqualAddr = store.getAddr().equals(load.getAddr());
                            boolean isEqualOffset = store.getOffset().equals(load.getOffset());// 和比较true offset等价
                            boolean isNoCond = store.getCond().equals(ArmInst.ArmCondType.Any);
                            if (isEqualAddr && isEqualOffset && isNoCond) {
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

                    if (inst instanceof ArmInstMove) {
                        var move = (ArmInstMove) inst;
                        if (move.getDst().equals(move.getSrc()) && move.getShift() == null) {
                            // 删除 mov a, a
                            move.freeFromIList();
                            done = false;
                        } else if (preInst != null && preInst instanceof ArmInstMove) {
                            // mov a, b
                            // mov b, a
                            // =>
                            // mov a, b
                            var preMove = (ArmInstMove) preInst;
                            boolean isEqualDst = move.getDst().equals(preMove.getSrc());
                            boolean isEqualSrc = move.getSrc().equals(preMove.getDst());
                            boolean isNoCond = preMove.getCond().equals(ArmInst.ArmCondType.Any);
                            boolean isNoShift = move.getShift() == null && preMove.getShift() == null;
                            if (isEqualDst && isEqualSrc && isNoCond && isNoShift) {
                                move.freeFromIList();
                                done = false;
                            }
                        }
                    }

                    if (inst instanceof ArmInstBinary) {
                        var binay = (ArmInstBinary) inst;
                        var isAddSub = binay.getInst().equals(ArmInstKind.IAdd)
                                || binay.getInst().equals(ArmInstKind.ISub)
                                || binay.getInst().equals(ArmInstKind.IRsb)
                                || binay.getInst().equals(ArmInstKind.IMul)
                                || binay.getInst().equals(ArmInstKind.IDiv);
                        if (preInst != null && preInst instanceof ArmInstMove && isAddSub) {
                            // mov a, b shift
                            // sub/add/rsb/mul/div d, c, a
                            // add/mul d, a, c
                            // =>
                            // mov a, b shift
                            // sub/add/rsb/mul/div d, c, b shift
                            // add/mul d, c, b shift
                            var move = (ArmInstMove) preInst;
                            boolean isReg = move.getSrc().IsReg();
                            boolean isEqualLhs = move.getDst().equals(binay.getLhs());
                            boolean isEqualRhs = move.getDst().equals(binay.getRhs());
                            boolean isNoShift = binay.getShift() == null;
                            boolean isNoCond = move.getCond().equals(ArmInst.ArmCondType.Any);
                            boolean canFix = (!move.getDst().equals(move.getSrc()))
                                    || (move.getDst().equals(move.getSrc()) && move.getDst().equals(binay.getDst())
                                            && !binay.getLhs().equals(binay.getRhs()));
                            // mov a ,a shift
                            // sub/add a, b, a
                            if (isReg && isEqualLhs && isNoShift && isNoCond && canFix
                                    && (binay.getInst().equals(ArmInstKind.IAdd)
                                            || (binay.getInst().equals(ArmInstKind.IMul)))
                                    && binay.getRhs().IsReg()) {
                                var binay2 = new ArmInstBinary(binay.getInst(), binay.getDst(), binay.getRhs(),
                                        move.getSrc());
                                binay2.setShift(move.getShift());
                                binay.insertBeforeCO(binay2);
                                binay.freeFromIList();
                                if (move.getDst().equals(move.getSrc())) {
                                    move.freeFromIList();
                                }
                                done = false;
                            } else if (isReg && isEqualRhs && isNoShift && isNoCond && canFix) {
                                var binay2 = new ArmInstBinary(binay.getInst(), binay.getDst(), binay.getLhs(),
                                        move.getSrc());
                                binay2.setShift(move.getShift());
                                binay.insertBeforeCO(binay2);
                                binay.freeFromIList();
                                if (move.getDst().equals(move.getSrc())) {
                                    move.freeFromIList();
                                }
                                done = false;
                            }
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
