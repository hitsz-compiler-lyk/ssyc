package pass.ir;

import ir.Module;
import ir.Value;
import ir.constant.Constant;
import ir.constant.IntConst;
import ir.inst.BinaryOpInst;
import ir.inst.GEPInst;
import ir.inst.InstKind;
import ir.inst.Instruction;

public class InstructionCombiner implements IRPass {
    @Override
    public void runPass(Module module) {
        new ConstructDominatorInfo().runPass(module);
        IRPass.instructionStream(module)
                .filter(this::matchMultiOp)
                .map(self -> (BinaryOpInst) self)
                .forEach(this::combine);
        IRPass.instructionStream(module).forEach(this::swapConst);
        IRPass.instructionStream(module).forEach(this::gepZeroComb);
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

    /**
     * (gep %ptr 0) ==> %ptr
     * @param inst 待化简的 instruction
     */
    private void gepZeroComb(Instruction inst) {
        if (inst instanceof GEPInst) {
            final var gepInst = (GEPInst) inst;
            if (gepInst.getIndices().size() == 1) {
                final var off = gepInst.getIndices().get(0);
                if (off instanceof IntConst) {
                    final var con = (IntConst) off;
                    if (con.getValue() == 0) {
                        final var ptr = gepInst.getPtr();
                        inst.replaceAllUseWith(ptr);
                        inst.freeAll();
                    }
                }
            }
        }
    }

}
