package pass.backend;

import backend.arm.ArmInst;
import backend.arm.ArmInstBranch;
import backend.arm.ArmInstCmp;
import backend.arm.ArmInstReturn;
import backend.codegen.CodeGenManager;

public class BranchToCond implements BackendPass {

    @Override
    public void runPass(CodeGenManager manager) {
        for (var func : manager.getFunctions()) {
            for (var block : func.asElementView()) {
                for (var inst : block.asElementView()) {
                    if (inst instanceof ArmInstBranch) {
                        var branch = (ArmInstBranch) inst;
                        var nxtBlockOp = block.getINode().getNext();
                        var nxtBlock = nxtBlockOp.isPresent() ? nxtBlockOp.get().getValue() : null;
                        if (nxtBlock == null) {
                            continue;
                        }
                        var nxtNxtBlockOp = nxtBlock.getINode().getNext();
                        var nxtNxtBlock = nxtNxtBlockOp.isPresent() ? nxtNxtBlockOp.get().getValue() : null;
                        if (nxtNxtBlock == null) {
                            continue;
                        }
                        if (!branch.getCond().equals(ArmInst.ArmCondType.Any)
                                && branch.getTargetBlock().equals(nxtNxtBlock)
                                && nxtBlock.getPred().size() == 1
                                && nxtBlock.getPred().get(0).equals(block)
                                && nxtBlock.asElementView().size() <= 4) {
                            boolean canFix = true;
                            var OppCond = branch.getCond().getOppCondType();
                            for (var inst2 : nxtBlock.asElementView()) {
                                if ((inst2 instanceof ArmInstReturn) && nxtBlock.asElementView().size() > 2) {
                                    canFix = false;
                                    break;
                                }
                                if ((inst2 instanceof ArmInstBranch) || (inst2 instanceof ArmInstCmp)) {
                                    canFix = false;
                                    break;
                                }
                                if (!inst2.getCond().equals(ArmInst.ArmCondType.Any)
                                        && !inst2.getCond().equals(OppCond)) {
                                    canFix = false;
                                    break;
                                }
                            }
                            if (canFix) {
                                System.out.println("AAAA");
                                for (var inst2 : nxtBlock.asElementView()) {
                                    inst2.setCond(OppCond);
                                }
                                branch.freeFromIList();
                            }
                        }
                    }
                }
            }
        }

    }

}
