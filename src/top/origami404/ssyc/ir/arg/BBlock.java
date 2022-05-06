package top.origami404.ssyc.ir.arg;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import top.origami404.ssyc.ir.inst.BranchInst;
import top.origami404.ssyc.ir.inst.Inst;
import top.origami404.ssyc.ir.inst.PhiInst;

public class BBlock extends Argument {

    public BBlock(String label) {
        super(Kind.BBlock);
        this.label = label;
        this.phis = new ArrayList<>();
        this.insts = new ArrayList<>();
        this.terminator = Optional.empty();
    }

    public String getLabel() {
        return label;
    }

    public BranchInst getTerminator() {
        return terminator.orElseThrow();
    }
    
    public void setTerminator(BranchInst terminator) {
        this.terminator = Optional.of(terminator);
    }

    public void insertInst(Inst inst) {
        insts.add(inst);
    }

    public void insertPhi(PhiInst phi) {
        phis.add(phi);
    }

    private List<PhiInst> phis;
    private List<Inst> insts;
    private Optional<BranchInst> terminator;
    private String label;
}
