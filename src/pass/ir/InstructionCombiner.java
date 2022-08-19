package pass.ir;

import frontend.IRBuilder;
import ir.Function;
import ir.Module;
import ir.Value;
import ir.constant.Constant;
import ir.constant.IntConst;
import ir.inst.BinaryOpInst;
import ir.inst.InstKind;
import ir.inst.Instruction;

import java.util.*;

public class InstructionCombiner implements IRPass {
    @Override
    public void runPass(Module module) {
        new ConstructDominatorInfo().runPass(module);

        IRPass.instructionStream(module)
            .filter(this::matchMultiOp).map(BinaryOpInst.class::cast)
            .forEach(this::combine);

        IRPass.instructionStream(module).forEach(this::swapConst);
        IRPass.instructionStream(module).forEach(this::biOpWithZeroOneComb);

        module.getNonExternalFunction().forEach(this::tryFlattenNestedAddOnFunction);
    }

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

    private void tryFlattenNestedAddOnFunction(Function function) {
        final var instInReverse = function.stream().collect(ArrayList<Instruction>::new, List::addAll, List::addAll);
        Collections.reverse(instInReverse);
        instInReverse.forEach(this::tryFlattenNestedAdd);
    }

    private void tryFlattenNestedAdd(Instruction instruction) {
        // ((+ a b) (+ a b) (+ b c)) ==> (+ (+ (* a 2) (* b 2)) (* c 1))
        if (isIAdd(instruction)) {
            final var leaves = collectAddTreeLeaves((BinaryOpInst) instruction);
            final var hasLotsOfRedundant = leaves.values().stream().anyMatch(i -> i >= 5);

            if (!hasLotsOfRedundant) {
                return;
            }

            final var newLeaves = new LinkedHashSet<Value>();
            for (final var entry : leaves.entrySet()) {
                final var value = entry.getKey();
                final var count = entry.getValue();

                if (count > 1) {
                    // (* leaf count)
                    final var mul = new BinaryOpInst(InstKind.IMul, value, Constant.createIntConstant(count));
                    newLeaves.add(foldOrAddBefore(instruction, mul));
                } else {
                    newLeaves.add(value);
                }
            }

            // 依次将叶子加起来 (+ ... (+ (+ (+ 0 l1) l2) l3)...)
            Value currTopAdd = Constant.INT_0;
            for (final var newLeaf : newLeaves) {
                final var add = new BinaryOpInst(InstKind.IAdd, currTopAdd, newLeaf);
                currTopAdd = foldOrAddBefore(instruction, add);
            }

            // 最后用加起来的结果替换掉旧的 instruction
            instruction.replaceAllUseWith(currTopAdd);
            clearUselessTreeNode(currTopAdd);
        }
    }

    private Value foldOrAddBefore(Instruction originInst, Instruction newInst) {
        final var afterFolded = IRBuilder.foldExp(newInst);
        if (afterFolded instanceof Instruction) {
            originInst.insertBeforeCO((Instruction) afterFolded);
        }

        return afterFolded;
    }

    // 按树形结构先序遍历, 清扫无用节点
    private void clearUselessTreeNode(Value curr) {
        if (curr instanceof Instruction) {
            final var inst = (Instruction) curr;
            if (ClearUselessInstruction.canBeRemove(inst)) {
                final var oldOperands = IRPass.copyForChange(inst.getOperands());
                ClearUselessInstruction.deleteInstruction(inst);
                oldOperands.forEach(this::clearUselessTreeNode);
            }
        }
    }

    // 好看的递归函数版本去 VCS 里看去
    // 下面是难看的用来提升处理速度的迭代版本 (速度直接提升一倍)
    private Map<Value, Integer> collectAddTreeLeaves(BinaryOpInst root) {
        final var leaves = new HashMap<Value, Integer>();
        final var queue = new ArrayDeque<BinaryOpInst>();
        queue.addLast(root);

        while (!queue.isEmpty()) {
            final var head = queue.pollFirst();

            // 不将叶子节点放进队列又拿出来可以有效提升效率
            final var lhs = head.getLHS();
            if (isIAdd(lhs)) {
                queue.addLast((BinaryOpInst) lhs);
            } else {
                leaves.merge(lhs, 1, Integer::sum);
            }

            final var rhs = head.getRHS();
            if (isIAdd(rhs)) {
                queue.addLast((BinaryOpInst) rhs);
            } else {
                leaves.merge(rhs, 1, Integer::sum);
            }
        }

        return leaves;
    }

    private boolean isIAdd(Value value) {
        return value instanceof BinaryOpInst
            && ((BinaryOpInst) value).getKind() == InstKind.IAdd;
    }

    private boolean isIAdd(Instruction instruction) {
        return instruction.getKind() == InstKind.IAdd;
    }
}
