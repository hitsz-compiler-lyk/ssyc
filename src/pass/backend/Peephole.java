package pass.backend;

import utils.INode;
import utils.ImmUtils;
import backend.lir.*;
import backend.lir.inst.ArmInst.ArmInstKind;
import backend.lir.inst.*;
import backend.lir.operand.IImm;
import backend.lir.operand.Operand;
import backend.lir.operand.Reg;
import utils.Log;
import utils.Pair;

import java.util.HashMap;
import java.util.Map;

public class Peephole implements BackendPass {

    private boolean peepholePass(ArmModule module) {
        boolean done = true;
        for (var func : module.getFunctions()) {
            for (var block : func) {
                var live = calcBlockLiveRange(block);
                for (var inst : block) {
                    var preInstOp = inst.getINode().getPrev();
                    var preInst = preInstOp.map(INode::getValue).orElse(null);
                    var nxtInstOp = inst.getINode().getNext();
                    var nxtInst = nxtInstOp.map(INode::getValue).orElse(null);

                    if (inst instanceof ArmInstBranch branch) {
                        var nxtBlockOp = block.getINode().getNext();
                        var nxtBlock = nxtBlockOp.map(INode::getValue).orElse(null);
                        boolean isEqualBlock = branch.getTargetBlock().equals(nxtBlock);
                        if (branch.getCond().equals(ArmInst.ArmCondType.Any)) {
                            if (isEqualBlock) {
                                branch.freeFromIList();
                                done = false;
                            }
                        } else if (nxtInst != null && nxtInst instanceof ArmInstBranch nxtBranch) {
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

                    if (inst instanceof ArmInstLoad) {
                        var load = (ArmInstLoad) inst;
                        var isOffsetZero = load.getOffset().equals(new IImm(0));
                        if (isOffsetZero && preInst != null && preInst instanceof ArmInstBinary binary) {
                            if (binary.getKind().equals(ArmInstKind.IAdd)) {
                                boolean canPre = (binary.getRhs().isReg() || (binary.getRhs() instanceof IImm &&
                                        ImmUtils.checkOffsetRange(((IImm) binary.getRhs()).getImm(),
                                                load.getDst())))
                                        && load.equals(live.getOrDefault(new Pair<>(binary.getDst(), binary), null));
                                boolean isEqualAddr = binary.getDst().equals(load.getAddr());
                                boolean isFloat = load.getDst().isFloat();
                                boolean isNoCond = binary.getCond().equals(ArmInst.ArmCondType.Any);
//                                boolean isNoShift = (binary.getShift() == null || binary.getShift().isNoPrint());
                                if (canPre && isEqualAddr && isNoCond && !isFloat) {
                                    load.replaceAddr(binary.getLhs());
                                    load.replaceOffset(binary.getRhs());
                                    if(binary.getShift() != null && !binary.getShift().isNoPrint()) load.replaceShift(binary.getShift());
                                    binary.freeFromIList();
                                    done = false;
                                }
                            }
                        }
                    }

                    if (inst instanceof ArmInstStore store) {
                        var isOffsetZero = store.getOffset().equals(new IImm(0));
                        if (isOffsetZero && preInst != null && preInst instanceof ArmInstBinary binary) {
                            if (binary.getKind().equals(ArmInstKind.IAdd)) {
                                boolean canPre = (binary.getRhs().isReg() || (binary.getRhs() instanceof IImm &&
                                        ImmUtils.checkOffsetRange(((IImm) binary.getRhs()).getImm(),
                                                store.getSrc())))
                                        && store.equals(live.getOrDefault(new Pair<>(binary.getDst(), binary), null));
                                boolean isEqualAddr = binary.getDst().equals(store.getAddr());
                                boolean isFloat = store.getSrc().isFloat();
                                boolean isNoCond = binary.getCond().equals(ArmInst.ArmCondType.Any);
//                                boolean isNoShift = (binary.getShift() == null || binary.getShift().isNoPrint());
                                if (canPre && isEqualAddr && isNoCond  && !isFloat) {
                                    store.replaceAddr(binary.getLhs());
                                    store.replaceOffset(binary.getRhs());
                                    if(binary.getShift() != null && !binary.getShift().isNoPrint()) store.replaceShift(binary.getShift());
                                    binary.freeFromIList();
                                    done = false;
                                }
                            }
                        }
                    }

                    if (inst instanceof ArmInstStackLoad load) {
                        if (preInst != null && preInst instanceof ArmInstStackStore store) {
                            // str a, [b, imm]
                            // ldr c, [b, imm]
                            // =>
                            // str a, [b, imm]
                            // mov c, a
                            // 这个情况理论上是用于处理寄存器分配的栈存取
                            boolean isEqualAddr = store.getAddr().equals(load.getAddr());
                            boolean isEqualOffset = store.getOffset().equals(load.getOffset());// 和比较true offset等价
                            boolean isNoCond = store.getCond().equals(ArmInst.ArmCondType.Any);
                            if (isEqualAddr && isEqualOffset && isNoCond) {
                                if (!load.getDst().equals(store.getDst())) {
                                    var move = new ArmInstMove(load.getDst(), store.getDst());
                                    move.setCond(load.getCond());
                                    load.insertBeforeCO(move);
                                }
                                load.freeFromIList();
                                done = false;
                            }
                        }
                    }

                    if (inst instanceof ArmInstMove move) {
                        if (move.getDst().equals(move.getSrc())
                                && (move.getShift() == null || move.getShift().isNoPrint())) {
                            // 删除 mov a, a
                            move.freeFromIList();
                            done = false;
                        } else if (preInst != null && preInst instanceof ArmInstMove preMove) {
                            // mov a, b
                            // mov b, a
                            // =>
                            // mov a, b
                            boolean isEqualDst = move.getDst().equals(preMove.getSrc());
                            boolean isEqualSrc = move.getSrc().equals(preMove.getDst());
                            boolean isNoCond = preMove.getCond().equals(ArmInst.ArmCondType.Any);
                            boolean isNoShift = (move.getShift() == null || move.getShift().isNoPrint())
                                    && (preMove.getShift() == null || preMove.getShift().isNoPrint());
                            if (isEqualDst && isEqualSrc && isNoCond && isNoShift) {
                                move.freeFromIList();
                                done = false;
                            }
                        }
                    }

                    if (inst instanceof ArmInstBinary binary) {
                        var isAddSub = binary.getKind().equals(ArmInstKind.IAdd)
                                || binary.getKind().equals(ArmInstKind.ISub)
                                || binary.getKind().equals(ArmInstKind.IRsb)
                                || binary.getKind().equals(ArmInstKind.IMul)
                                || binary.getKind().equals(ArmInstKind.IDiv);
                        if (preInst != null && preInst instanceof ArmInstMove move && isAddSub) {
                            // mov a, b shift
                            // sub/add/rsb/mul/div d, c, a
                            // add/mul d, a, c
                            // =>
                            // mov a, b shift
                            // sub/add/rsb/mul/div d, c, b shift
                            // add/mul d, c, b shift
                            boolean isReg = move.getSrc().isReg();
                            boolean isEqualLhs = move.getDst().equals(binary.getLhs());
                            boolean isEqualRhs = move.getDst().equals(binary.getRhs());
                            boolean isNoShift = (binary.getShift() == null || binary.getShift().isNoPrint());
                            boolean isNoCond = move.getCond().equals(ArmInst.ArmCondType.Any);
                            boolean canFix = (!move.getDst().equals(move.getSrc())
                                    && !binary.getLhs().equals(binary.getRhs()))
                                    || (move.getDst().equals(move.getSrc())
                                    && binary.equals(live.getOrDefault(new Pair<>(move.getDst(), move), null))
                                    && !binary.getLhs().equals(binary.getRhs()));
                            // mov a ,a shift
                            // sub/add a, b, a
                            if (isReg && isEqualLhs && isNoShift && isNoCond && canFix
                                    && (binary.getKind().equals(ArmInstKind.IAdd)
                                    || (binary.getKind().equals(ArmInstKind.IMul)))
                                    && binary.getRhs().isReg()) {
                                var binary2 = new ArmInstBinary(binary.getKind(), binary.getDst(),
                                        binary.getRhs(), move.getSrc());
                                binary2.setShift(move.getShift());
                                binary.insertBeforeCO(binary2);
                                binary.freeFromIList();
                                if (binary.equals(live.getOrDefault(new Pair<>(move.getDst(), move), null))) {
                                    move.freeFromIList();
                                }
                                done = false;
                            } else if (isReg && isEqualRhs && isNoShift && isNoCond && canFix) {
                                var binary2 = new ArmInstBinary(binary.getKind(), binary.getDst(),
                                        binary.getLhs(), move.getSrc());
                                binary2.setShift(move.getShift());
                                binary.insertBeforeCO(binary2);
                                binary.freeFromIList();
                                if (binary.equals(live.getOrDefault(new Pair<>(move.getDst(), move), null))) {
                                    move.freeFromIList();
                                }
                                done = false;
                            }
                        } else if (binary.getKind().equals(ArmInstKind.IAdd) && preInst != null
                                && preInst instanceof ArmInstBinary binary2) {
                            boolean isEqualLhsRhs = binary2.getDst().equals(binary.getLhs())
                                    ^ binary2.getDst().equals(binary.getRhs()); // 这里就做了 lhs != rhs
                            boolean isNoShift = (binary.getShift() == null || binary.getShift().isNoPrint())
                                    && (binary2.getShift() == null || binary2.getShift().isNoPrint());
                            boolean isMul = binary2.getKind().equals(ArmInstKind.IMul);
                            boolean isNoCond = binary2.getCond().equals(ArmInst.ArmCondType.Any);
                            boolean canFix = binary
                                    .equals(live.getOrDefault(new Pair<>(binary2.getDst(), binary2), null));
                            boolean noImm = binary2.getLhs().isReg() && binary2.getRhs().isReg()
                                    && binary.getLhs().isReg() && binary.getRhs().isReg();
                            if (isEqualLhsRhs && isNoShift && isMul && isNoCond && canFix && noImm) {
                                Operand add = null;
                                if (binary2.getDst().equals(binary.getLhs())) {
                                    add = binary.getRhs();
                                } else {
                                    add = binary.getLhs();
                                }
                                var ternay = new ArmInstTernary(ArmInstKind.IMulAdd, binary.getDst(), binary2.getLhs(),
                                        binary2.getRhs(), add, binary.getCond());
                                binary2.insertBeforeCO(ternay);
                                binary2.freeFromIList();
                                binary.freeFromIList();
                                done = false;
                            }
                        } else if (binary.getKind().equals(ArmInstKind.ISub) && preInst != null
                                && preInst instanceof ArmInstBinary binary2) {
                            boolean isEqualRhs = binary2.getDst().equals(binary.getRhs())
                                    && !binary.getRhs().equals(binary.getLhs());
                            boolean isNoShift = (binary.getShift() == null || binary.getShift().isNoPrint())
                                    && (binary2.getShift() == null || binary2.getShift().isNoPrint());
                            boolean isMul = binary2.getKind().equals(ArmInstKind.IMul);
                            boolean isNoCond = binary2.getCond().equals(ArmInst.ArmCondType.Any);
                            boolean canFix = binary
                                    .equals(live.getOrDefault(new Pair<>(binary2.getDst(), binary2), null));
                            boolean noImm = binary2.getLhs().isReg() && binary2.getRhs().isReg()
                                    && binary.getLhs().isReg() && binary.getRhs().isReg();
                            if (isEqualRhs && isNoShift && isMul && isNoCond && canFix && noImm) {
                                var ternay = new ArmInstTernary(ArmInstKind.IMulSub, binary.getDst(), binary2.getLhs(),
                                        binary2.getRhs(), binary.getLhs(), binary.getCond());
                                binary2.insertBeforeCO(ternay);
                                binary2.freeFromIList();
                                binary.freeFromIList();
                                done = false;
                            }
                        }
                    }
                }
            }
        }
        return done;
    }

    private Map<Pair<Operand, ArmInst>, ArmInst> calcBlockLiveRange(ArmBlock block) {
        Map<Pair<Operand, ArmInst>, ArmInst> ret = new HashMap<>();
        Map<Pair<Operand, ArmInst>, ArmInst> temp = new HashMap<>();
        Map<Operand, ArmInst> regMap = new HashMap<>();
        for (var inst : block) {
            for (var use : inst.getRegUse()) {
                if (regMap.containsKey(use)) {
                    temp.put(new Pair<>(use, regMap.get(use)), inst);
                }
            }
            for (var def : inst.getRegDef()) {
                ret.put(new Pair<>(def, regMap.get(def)), temp.getOrDefault(new Pair<>(def, regMap.get(def)), regMap.get(def)));
                regMap.put(def, inst);
            }
            // for (var def : inst.getRegDef()) {
            // temp.put(new Pair<>(def, inst), null);
            // }
        }
        return ret;
    }

    private boolean clearNotUseInst(ArmModule module) {
        boolean done = true;
        for (var func : module.getFunctions()) {
            for (var block : func) {
                Map<Operand, ArmInst> instMap = new HashMap<>();
                for (var inst : block) {
                    for (var use : inst.getRegUse()) {
                        instMap.remove(use);
                    }
                    for (var def : inst.getRegDef()) {
                        if (instMap.containsKey(def)) {
                            var pre = instMap.get(def);
                            instMap.remove(def);
                            if (!(pre instanceof ArmInstCall)) {
                                pre.freeFromIList();
                                done = false;
                            }
                        }
                    }
                    for (var def : inst.getRegDef()) {
                        instMap.put(def, inst);
                    }
                }
            }
        }
        return done;
    }

    @Override
    public void runPass(ArmModule module) {
        clearNotUseInst(module);
        boolean done = false;
        while (!done) {
            done = peepholePass(module);
            done &= clearNotUseInst(module);
        }
    }

}
