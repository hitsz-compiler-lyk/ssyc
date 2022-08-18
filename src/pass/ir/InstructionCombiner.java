package pass.ir;

import ir.Module;
import ir.Value;
import ir.constant.Constant;
import ir.constant.IntConst;
import ir.inst.BinaryOpInst;
import ir.inst.InstKind;
import ir.inst.Instruction;
import utils.Log;
import utils.Pair;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InstructionCombiner implements IRPass {
    @Override
    public void runPass(Module module) {
        instRepeatInfoMap = new HashMap<>();
        IRPass.instructionStream(module)
                .map(inst->new Pair<>(inst,getRepeatInfo(inst)))
                .forEach(this::repeatedAddComb);
        new ConstructDominatorInfo().runPass(module);
        IRPass.instructionStream(module)
            .filter(this::matchMultiOp)
            .map(BinaryOpInst.class::cast)
            .forEach(this::combine);
        IRPass.instructionStream(module).forEach(this::swapConst);
        IRPass.instructionStream(module).forEach(this::biOpWithZeroOneComb);
    }

    private Map<Instruction,RepeatInfo> instRepeatInfoMap;

    private boolean isKind(Value value, InstKind kind) {
        if (value instanceof Instruction) {
            return isKind((Instruction) value, kind);
        } else {
            return false;
        }
    }

    private boolean isKind(Instruction inst, InstKind kind) {
        return inst.getKind().equals(kind);
    }

    private boolean matchMultiOp(Instruction inst) {
        if (inst instanceof BinaryOpInst) {
            final var binst = (BinaryOpInst) inst;
            final var kind = binst.getKind();
            if (kind.equals(InstKind.IAdd) || kind.equals(InstKind.IMul)) {
                return isKind(binst.getLHS(), kind) || isKind(binst.getRHS(), kind);
            }
        }
        return false;
    }

    private void combine(BinaryOpInst inst) {
        final var kind = inst.getKind();
        if (isKind(inst.getLHS(), kind)) {
            combineLHS(inst);
        } else if (isKind(inst.getRHS(), kind)) {
            combineRHS(inst);
        }
    }

    private int getDepth(Value value) {
        if (value instanceof Instruction) {
            return ConstructDominatorInfo.DominatorInfo
                    .domTreeDepth(((Instruction) value).getParent());
        } else {
            return -1;
        }
    }

    private void combineLHS(BinaryOpInst inst) {
        final var kind = inst.getKind();
        final var LInst = (BinaryOpInst) inst.getLHS();
        final int RDepth = getDepth(inst.getRHS());
        final int LLDepth = getDepth(LInst.getLHS());
        final int LRDepth = getDepth(LInst.getRHS());
        if (RDepth < LLDepth || RDepth < LRDepth) {
            if (LLDepth >= LRDepth) {
                final var reLInst = new BinaryOpInst(kind, inst.getRHS(), LInst.getRHS());
                inst.insertBeforeCO(reLInst);
                inst.replaceLHS(reLInst);
                inst.replaceRHS(LInst.getLHS());
            } else {
                final var reLInst = new BinaryOpInst(kind, inst.getRHS(), LInst.getLHS());
                inst.insertBeforeCO(reLInst);
                inst.replaceLHS(reLInst);
                inst.replaceRHS(LInst.getRHS());
            }
        }
    }

    private void combineRHS(BinaryOpInst inst) {
        final var kind = inst.getKind();
        final var RInst = (BinaryOpInst) inst.getRHS();
        final int LDepth = getDepth(inst.getLHS());
        final int RLDepth = getDepth(RInst.getLHS());
        final int RRDepth = getDepth(RInst.getRHS());
        if (LDepth < RLDepth || LDepth < RRDepth) {
            if (RLDepth >= RRDepth) {
                final var reRInst = new BinaryOpInst(kind, inst.getLHS(), RInst.getRHS());
                inst.insertBeforeCO(reRInst);
                inst.replaceRHS(reRInst);
                inst.replaceLHS(RInst.getLHS());
            } else {
                final var reRInst = new BinaryOpInst(kind, inst.getLHS(), RInst.getLHS());
                inst.insertBeforeCO(reRInst);
                inst.replaceRHS(reRInst);
                inst.replaceLHS(RInst.getRHS());
            }
        }
    }

    private boolean isConst(Value value) {
        return value instanceof Constant;
    }
    private void swapConst(Instruction inst) {
        if (inst instanceof BinaryOpInst && (isKind(inst, InstKind.IAdd) || isKind(inst, InstKind.IMul))) {
            final var binst = (BinaryOpInst) inst;
            final var lhs = binst.getLHS();
            final var rhs = binst.getRHS();
            if (!isConst(rhs) && isConst(lhs)) {
                binst.replaceLHS(rhs);
                binst.replaceRHS(lhs);
            }
        }
    }

    private boolean equalToInt(Value value,int intValue) {
        return (value instanceof IntConst) &&
                ((IntConst) value).getValue() == intValue;
    }

    /**
     * 在 swapConst 之后进行,
     * 确保了 Iadd 和 Imul 的 lhs 不会是 Const <br>
     * (Iadd a 0) ==> a <br>
     * (Imul a 1) ==> a <br>
     * (Imul a 0) ==> 0 <br>
     * (Isub a 0) ==> a <br>
     * (Idiv a 1) ==> a
     * @param inst 待化简的 instruction
     */
    private void biOpWithZeroOneComb(Instruction inst) {
        if (inst instanceof BinaryOpInst) {
            final var binst = (BinaryOpInst) inst;
            final var lhs = binst.getLHS();
            final var rhs = binst.getRHS();
            if (isKind(inst, InstKind.IAdd)) {
                 if (equalToInt(rhs, 0)) {
                    inst.replaceAllUseWith(lhs);
                    inst.freeAll();
                }
            } else if (isKind(inst, InstKind.IMul)) {
                if (equalToInt(rhs, 1)) {
                    inst.replaceAllUseWith(lhs);
                    inst.freeAll();
                } else if (equalToInt(rhs, 0)) {
                    inst.replaceAllUseWith(Constant.INT_0);
                    inst.freeAll();
                }
            } else if (isKind(inst, InstKind.ISub)) {
                if (equalToInt(rhs, 0)) {
                    inst.replaceAllUseWith(lhs);
                    inst.freeAll();
                }
            } else if (isKind(inst, InstKind.IDiv)) {
                if (equalToInt(rhs, 1)) {
                    inst.replaceAllUseWith(lhs);
                    inst.freeAll();
                }
            }
        }
    }
    private static class RepeatInfo {
        private boolean isRepeatedAdd;
        /**
         * 是否 Top 节点, 即是否需要展开
         */
        private boolean isTop;
        /**
         * key 需为 Instruction 或 IntConst
         */
        private Map<Value, Integer> valueFrequencyMap;
        RepeatInfo(boolean isRepeatedAdd,boolean isTop) {
            this.isRepeatedAdd = isRepeatedAdd;
            this.isTop = isTop;
            this.valueFrequencyMap = new HashMap<>();
        }
        void put(Value key,Integer value) {
            Log.ensure((key instanceof IntConst) || (key instanceof Instruction));
            valueFrequencyMap.put(key, value);
        }

        void merge(Value key,Integer value) {
            valueFrequencyMap.merge(key, value, Integer::sum);
        }

        void mergeAll(RepeatInfo info) {
            info.valueFrequencyMap.forEach(this::merge);
        }
    }

    private boolean isIntConst(Value value) {
        return value instanceof IntConst;
    }

    /**
     * 需要记录的 value 只有 inst 和 Const
     * @param inst 待记录的 instruction
     * @return info
     */
    private RepeatInfo getRepeatInfo(Instruction inst) {
        if (instRepeatInfoMap.containsKey(inst)) {
            return instRepeatInfoMap.get(inst);
        }
        if (inst instanceof BinaryOpInst) {
            final var binst = (BinaryOpInst) inst;
            final var lhs = binst.getLHS();
            final var rhs = binst.getRHS();
            if (isKind(inst, InstKind.IAdd)) {
                final var info = new RepeatInfo(true, true);
                if (lhs instanceof Instruction) {
                    info.mergeAll(getRepeatInfo((Instruction) lhs));
                } else if (lhs instanceof IntConst) {
                    info.merge(lhs, 1);
                } else {
                    Log.ensure(false,"lhs must be a Instruction or IntConst.");
                }
                if (rhs instanceof Instruction) {
                    info.mergeAll(getRepeatInfo((Instruction) rhs));
                } else if (rhs instanceof IntConst) {
                    info.merge(rhs, 1);
                } else {
                    Log.ensure(false,"rhs must be a Instruction or IntConst.");
                }
                instRepeatInfoMap.put(inst, info);
                return info;
            } else if (isKind(inst, InstKind.IMul)) {
                if (isIntConst(lhs) && !isIntConst(rhs)) {
                    final var info = new RepeatInfo(true, false);
                    final var con = (IntConst) lhs;
                    info.put(rhs,con.getValue());
                    instRepeatInfoMap.put(inst, info);
                    return info;
                }
                if (!isIntConst(lhs) && isIntConst(rhs)) {
                    final var info = new RepeatInfo(true, false);
                    final var con = (IntConst) rhs;
                    info.put(rhs, con.getValue());
                    instRepeatInfoMap.put(inst, info);
                    return info;
                }
            }
        }

        final var info = new RepeatInfo(false, false);
        info.put(inst, 1);
        instRepeatInfoMap.put(inst, info);
        return info;
    }

    private void repeatedAddComb(Pair<Instruction,RepeatInfo> pair) {
        final var inst = pair.getKey();
        final var info = pair.getValue();
        if (info.isTop && info.isRepeatedAdd) {
            final var entryList = info.valueFrequencyMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue()).collect(Collectors.toList());
            final List<Value> valueList = new LinkedList<>();
            for (var entry : entryList) {
                if (entry.getValue() > 0 && entry.getValue() < 5) {
                    for (int i = 0; i < entry.getValue(); i++) {
                        valueList.add(entry.getKey());
                    }
                } else if (entry.getValue() >= 5) {
                    final var mulInst = new BinaryOpInst(InstKind.IMul, entry.getKey(),
                            Constant.createIntConstant(entry.getValue()));
                    inst.insertBeforeCO(mulInst);
                    valueList.add(mulInst);
                }
            }
            Log.ensure(valueList.size() >= 2, "valueList must have two or more elements.");
            final var flhs = valueList.remove(0);
            final var frhs = valueList.remove(0);
            Instruction lastInst = new BinaryOpInst(InstKind.IAdd, flhs, frhs);
            for (var value : valueList) {
                inst.insertBeforeCO(lastInst);
                lastInst = new BinaryOpInst(InstKind.IAdd, value, lastInst);
            }
            inst.replaceAllUseWith(lastInst);
            inst.replaceInIList(lastInst);
            inst.freeAll();
        }
    }
}
