package pass.ir;

import ir.BasicBlock;
import ir.Module;
import ir.inst.CallInst;
import ir.inst.Instruction;
import ir.inst.LoadInst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class LCM implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().stream()
            .flatMap(Collection::stream)
            .forEach(this::reorderBasicBlock);
    }

    public void reorderBasicBlock(BasicBlock block) {
        final var oldInsts = IRPass.copyForChange(block.nonPhiAndTerminator());
        for (final var inst : oldInsts) {
            if (inst instanceof LoadInst || inst instanceof CallInst) {
                continue;
            }

            final var instUsers = inst.getUserList().stream()
                .filter(Instruction.class::isInstance).map(Instruction.class::cast).toList();

            // only one user
            if (instUsers.size() == 1 && instUsers.get(0).getParent() == block) {
                final var user = instUsers.get(0);
                // insertBefore 应该能正确对 "新 before 就是旧 before 这种情况作出应对"
                user.insertBeforeCO(inst);
            }
        }
    }

    List<Chunk> splitBlockToChunks(BasicBlock block) {
        List<Chunk> chunks = new ArrayList<>();
        List<Instruction> currFreeInstructions = new ArrayList<>();

        for (final var inst : block.nonPhis()) {
            if (isFixedInstruction(inst)) {
                chunks.add(new Chunk(currFreeInstructions, inst));
                currFreeInstructions = new ArrayList<>();
            } else {
                currFreeInstructions.add(inst);
            }
        }

        return chunks;
    }

    boolean isFixedInstruction(Instruction instruction) {
        return instruction.getType().isVoid()
               || instruction instanceof CallInst;
    }

    long countUserInBlock(Instruction instruction) {
        final var block = instruction.getParent();

        return instruction.getUserList().stream()
            .filter(Instruction.class::isInstance).map(Instruction.class::cast)
            .filter(user -> user.getParent() == block)
            .count();
    }

    record Chunk(List<Instruction> freeInstructions, Instruction fixedInstruction) {}
}