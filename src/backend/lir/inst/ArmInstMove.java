package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.codegen.CodeGenManager;
import backend.lir.operand.Addr;
import backend.lir.operand.FImm;
import backend.lir.operand.IImm;
import backend.lir.operand.Operand;
import utils.Log;

// 0: dst RegUse
// 1: drc RegUse
public class ArmInstMove extends ArmInst {
    ArmShift shift;

    public ArmInstMove(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        block.asElementView().add(this);
        this.initOperands(dst, src);
        if (src instanceof IImm srcIImm) {
            int imm = srcIImm.getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src instanceof Addr) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public ArmInstMove(Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        this.initOperands(dst, src);
        if (src instanceof IImm) {
            int imm = ((IImm) src).getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src instanceof Addr) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, src);
        if (src instanceof IImm) {
            int imm = ((IImm) src).getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src instanceof Addr) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public ArmInstMove(Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.setCond(cond);
        this.initOperands(dst, src);
        if (src instanceof IImm) {
            int imm = ((IImm) src).getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src instanceof Addr) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getSrc() {
        return this.getOperand(1);
    }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }

    public ArmShift getShift() {
        return shift;
    }
}
