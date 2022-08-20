package pass.ir;

import frontend.IRBuilder;
import frontend.SourceCodeSymbol;
import ir.Module;
import ir.*;
import ir.constant.Constant;
import ir.constant.IntConst;
import ir.inst.*;
import ir.type.IRType;

import java.util.*;
import java.util.function.Consumer;

public class InstructionCombiner implements IRPass {
    @Override
    public void runPass(Module module) {
        new ConstructDominatorInfo().runPass(module);

        IRPass.instructionStream(module)
            .filter(this::matchMultiOp).map(BinaryOpInst.class::cast)
            .forEach(this::combine);

        IRPass.instructionStream(module).forEach(this::swapConst);
        // 以下的 int bop 常数将在右手
        IRPass.instructionStream(module).forEach(this::biOpWithZeroOneComb);
        IRPass.instructionStream(module).forEach(this::distributeIMulComb);
        IRPass.instructionStream(module).forEach(this::addMulConstComb);

        runWithInstructionInReverseOrder(module, this::tryFlattenNestedAdd);
        // runWithInstructionInReverseOrder(module, this::tryFlattenNestedMul);
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

    private void tryFlattenNestedAdd(Instruction instruction) {
        // ((+ a b) (+ a b) (+ b c)) ==> (+ (+ (* a 2) (* b 2)) (* c 1))
        if (isIAdd(instruction)) {
            final var leaves = collectAddTreeLeaves((BinaryOpInst) instruction);
            final var hasLotsOfRedundant = leaves.values().stream().anyMatch(i -> i >= 5);

            if (!hasLotsOfRedundant) {
                return;
            }

            final var newLeaves = new ArrayList<Value>();
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
            clearUselessTreeNode(instruction);
        }
    }

    private Value foldOrAddBefore(Instruction originInst, Value newValue) {
        if (newValue instanceof Instruction) {
            final var afterFolded = IRBuilder.foldExp((Instruction) newValue);
            if (afterFolded instanceof Instruction) {
                originInst.insertBeforeCO((Instruction) afterFolded);
            }

            return afterFolded;

        } else {
            return newValue;
        }
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

    /**
     * (+ (* a b) (* a d)) ==> (* a (+ b d))
     * @param inst 待化简的 instruction
     */
    private void distributeIMulComb(Instruction inst) {
        if (isIAdd(inst)) {
            final var binst = (BinaryOpInst) inst;
            final var lhs = binst.getLHS();
            final var rhs = binst.getRHS();
            if (isKind(lhs, InstKind.IMul) && isKind(rhs, InstKind.IMul) && lhs.getUserList().size() == 1 && rhs.getUserList().size() == 1) {
                final var linst = (BinaryOpInst) lhs;
                final var rinst = (BinaryOpInst) rhs;
                final var addMulOperands = getAddMulOperands(
                        linst.getLHS(), linst.getRHS(), rinst.getLHS(), rinst.getRHS()
                );
                if (addMulOperands.isPresent()) {
                    final var addInst = new BinaryOpInst(InstKind.IAdd, addMulOperands.get().get(1), addMulOperands.get().get(2));
                    final var mulInst = new BinaryOpInst(InstKind.IMul, addMulOperands.get().get(0), addInst);
                    inst.insertBeforeCO(addInst);
                    inst.replaceInIList(mulInst);
                    inst.replaceAllUseWith(mulInst);
                    inst.freeAll();
                }
            }
        }
    }

    private Optional<List<Value>> getAddMulOperands(Value a, Value b, Value c, Value d) {
        if (a.equals(c)) {
            return Optional.of(List.of(a, b, d));
        }
        if (a.equals(d)) {
            return Optional.of(List.of(a, b, c));
        }
        if (b.equals(c)) {
            return Optional.of(List.of(b, a, d));
        }
        if (b.equals(d)) {
            return Optional.of(List.of(b, a, c));
        }
        return Optional.empty();
    }

    /**
     * (* (+ a constB) constC) ==> (+ (* a constC) constB*C)
     * @param inst 待化简的 instruction
     */
    private void addMulConstComb(Instruction inst) {
        if (isKind(inst, InstKind.IMul)) {
            final var binst = (BinaryOpInst) inst;
            final var lhs = binst.getLHS();
            final var rhs = binst.getRHS();
            if (isConst(rhs) && isKind(lhs, InstKind.IAdd)) {
                final var addInst = (BinaryOpInst) lhs;
                if (isConst(addInst.getRHS())) {
                    final var lMulInst = new BinaryOpInst(InstKind.IMul, addInst.getLHS(), rhs);
                    final var rMul = Constant.createIntConstant(
                            ((IntConst) addInst.getRHS()).getValue()* ((IntConst) rhs).getValue());
                    final var reInst = new BinaryOpInst(InstKind.IAdd, lMulInst, rMul);
                    inst.insertBeforeCO(lMulInst);
                    inst.insertBeforeCO(reInst);
                    inst.replaceAllUseWith(reInst);
                    inst.freeAll();
                }
            }
        }
    }

    private static Function fpowFunction = constructFastPowFunction();

    // 连续乘法转快速幂
    static Function constructFastPowFunction() {
        /* // 返回 a^i
         * int fpow(int a, int i) {
         *      int p = 1;
         *      while (i != 0) {
         *          if (i % 2 == 1) {
         *              p = p * a;
         *          }
         *          a = a * a;
         *          i = i / 2;
         *      }
         *      return p;
         * }
         */

        // define i32 @fpow(i32 %a0, i32 %i0) {
        final var a_0 = new Parameter(newSym("a"), IRType.IntTy);
        final var i_0 = new Parameter(newSym("i"), IRType.IntTy);
        final var function = new Function(IRType.IntTy, List.of(a_0, i_0), newSym("__origami404_self_fpow__"));

        final var entry = BasicBlock.createFreeBBlock(newSym("entry"));
        final var cond = BasicBlock.createFreeBBlock(newSym("while_cond"));
        final var body = BasicBlock.createFreeBBlock(newSym("while_body"));
        final var if_then = BasicBlock.createFreeBBlock(newSym("if_then"));
        final var if_exit = BasicBlock.createFreeBBlock(newSym("if_exit"));
        final var exit = BasicBlock.createFreeBBlock(newSym("while_exit"));

        // entry:
        //      br cond
        entry.setBr(cond);

        // cond:
        //      %p = phi [1, entry], [%p2, if_exit]
        //      %a = phi [%a0, entry], [%a1, if_exit]
        //      %i = phi [%i0, entry], [%i1, if_exit]
        //      %1 = icmp ne %i, 0
        //      br %1, body, exit
        final var phi_p = new PhiInst(IRType.IntTy, newSym("p"));
        final var phi_a = new PhiInst(IRType.IntTy, newSym("a"));
        final var phi_i = new PhiInst(IRType.IntTy, newSym("i"));
        final var tmp_1 = new CmpInst(InstKind.ICmpNe, phi_i, Constant.INT_0);

        cond.addAll(List.of(phi_p, phi_a, phi_i, tmp_1));
        cond.setBrCond(tmp_1, body, exit);

        // body:
        //      %2 = imod %i, 2
        //      %3 = icmp eq %2, 1
        //      br %3, if_then, if_exit
        final var tmp_2 = new BinaryOpInst(InstKind.IMod, phi_i, Constant.createIntConstant(2));
        final var tmp_3 = new CmpInst(InstKind.ICmpEq, tmp_2, Constant.createIntConstant(1));
        body.addAll(List.of(tmp_2, tmp_3));
        body.setBrCond(tmp_3, if_then, if_exit);

        // if_then:
        //      %p1 = imul %p, %a
        //      br if_exit
        final var p_1 = new BinaryOpInst(InstKind.IMul, phi_p, phi_a);
        if_then.add(p_1);
        if_then.setBr(if_exit);

        // if_exit:
        //      %p2 = phi [%p, body], [%p1, if_then]
        //      %a1 = imul %a, %a
        //      %i1 = idiv %i, 2
        //      br cond
        final var p_2 = new PhiInst(IRType.IntTy, newSym("p"));
        p_2.setIncomingValueWithoutCheckingPredecessorsCO(List.of(phi_p, p_1));
        final var a_1 = new BinaryOpInst(InstKind.IMul, phi_a, phi_a);
        final var i_1 = new BinaryOpInst(InstKind.IDiv, phi_i, Constant.createIntConstant(2));

        if_exit.addAll(List.of(p_2, a_1, i_1));
        if_exit.setBr(cond);

        // 回填 cond 中的 phi
        phi_p.setIncomingValueWithoutCheckingPredecessorsCO(List.of(Constant.createIntConstant(1), p_2));
        phi_a.setIncomingValueWithoutCheckingPredecessorsCO(List.of(a_0, a_1));
        phi_i.setIncomingValueWithoutCheckingPredecessorsCO(List.of(i_0, i_1));

        // exit:
        //      ret %p
        exit.add(new ReturnInst(phi_p));

        function.addAll(List.of(entry, cond, body, if_then, if_exit, exit));
        function.forEach(BasicBlock::adjustPhiEnd);
        //}

        function.verifyAll();
        return function;
    }

    private static SourceCodeSymbol newSym(String name) {
        return new SourceCodeSymbol(name, 0, 0);
    }

    private boolean isIMul(Value value) {
        return value instanceof BinaryOpInst
               && ((BinaryOpInst) value).getKind() == InstKind.IMul;
    }

    private boolean isIMul(Instruction instruction) {
        return instruction.getKind() == InstKind.IMul;
    }

    // 直接复制一份提升性能
    private Map<Value, Integer> collectMulTreeLeaves(BinaryOpInst root) {
        final var leaves = new HashMap<Value, Integer>();
        final var queue = new ArrayDeque<BinaryOpInst>();
        queue.addLast(root);

        while (!queue.isEmpty()) {
            final var head = queue.pollFirst();

            // 不将叶子节点放进队列又拿出来可以有效提升效率
            final var lhs = head.getLHS();
            if (isIMul(lhs)) {
                queue.addLast((BinaryOpInst) lhs);
            } else {
                leaves.merge(lhs, 1, Integer::sum);
            }

            final var rhs = head.getRHS();
            if (isIMul(rhs)) {
                queue.addLast((BinaryOpInst) rhs);
            } else {
                leaves.merge(rhs, 1, Integer::sum);
            }
        }

        return leaves;
    }

    private void tryFlattenNestedMul(Instruction instruction) {
        // ((* a b) (* a b) (* b c) ... (* a b)) ==> (* (* (call fpow a n) (call fpow b n)) c)
        if (isIMul(instruction)) {
            final var leaves = collectMulTreeLeaves((BinaryOpInst) instruction);
            final var hasLotsOfRedundant = leaves.values().stream().anyMatch(i -> i >= 8);

            if (!hasLotsOfRedundant) {
                return;
            }

            final var newLeaves = new ArrayList<Value>();
            for (final var entry : leaves.entrySet()) {
                final var value = entry.getKey();
                final var count = entry.getValue();

                if (count == 1) {
                    newLeaves.add(value);
                    continue;
                }

                if (value instanceof IntConst) {
                    final var result = fpow(((IntConst) value).getValue(), count);
                    newLeaves.add(Constant.createIntConstant(result));
                    continue;
                }

                if (count <= 8) {
                    Value currValue = foldOrAddBefore(instruction, value);
                    for (int i = 0; i < count; i++) {
                        final var mul = new BinaryOpInst(InstKind.IMul, currValue, value);
                        currValue = foldOrAddBefore(instruction, mul);
                    }
                    newLeaves.add(currValue);

                } else {
                    final var exponent = Constant.createIntConstant(count);
                    final var callToFpow = new CallInst(fpowFunction, List.of(value, exponent));
                    instruction.insertBeforeCO(callToFpow);
                    newLeaves.add(callToFpow);
                }
            }

            // 依次将叶子乘起来 (* ... (* (* (* 1 l1) l2) l3)...)
            Value currTopAdd = Constant.createIntConstant(1);
            for (final var newLeaf : newLeaves) {
                final var add = new BinaryOpInst(InstKind.IMul, currTopAdd, newLeaf);
                currTopAdd = foldOrAddBefore(instruction, add);
            }

            // 最后用加起来的结果替换掉旧的 instruction
            instruction.replaceAllUseWith(currTopAdd);
            clearUselessTreeNode(instruction);
        }
    }

    private void runWithInstructionInReverseOrder(Module module, Consumer<Instruction> consumer) {
        for (final var function : module.getNonExternalFunction()) {
            final var instInReverse = function.stream().collect(ArrayList<Instruction>::new, List::addAll, List::addAll);
            Collections.reverse(instInReverse);

            for (final var inst : instInReverse) {
                if (inst.getParentOpt().isPresent()) {
                    consumer.accept(inst);
                }
            }
        }
    }

    /**
     * @return base^exp
     */
    static int fpow(int base, int exp) {
        int result = 1;

        while (exp != 0) {
            if ((exp & 1) == 1) {
                result *= base;
            }
            base *= base;
            exp >>= 1;
        }

        return result;
    }
}
