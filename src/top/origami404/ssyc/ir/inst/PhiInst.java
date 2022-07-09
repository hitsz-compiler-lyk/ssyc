package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.frontend.info.VersionInfo.Variable;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.IRTypeException;

import java.util.Iterator;
import java.util.List;

public class PhiInst extends Instruction {
    public PhiInst(IRType type, Variable variable) {
        super(InstKind.Phi, type);
        super.setName(variable.getIRName());

        this.variable = variable;
        this.incompleted = true;
    }

    @Override
    public void addOperandCO(Value operand) {
        throw new RuntimeException("Cannot use normal operands method for phi");
    }

    @Override
    public Value removeOperandCO(int index) {
        throw new RuntimeException("Cannot use normal operands method for phi");
    }

    public void setIncomingCO(List<Value> incomingValues) {
        if (incomingValues.size() != getIncomingBlocks().size()) {
            throw new IRTypeException(this, "Phi must have the same amount of incoming variable and blocks");
        }

        if (getIncomingSize() != 0) {
            clearIncomingCO();
        }

        super.addAllOperandsCO(incomingValues);
    }

    public void clearIncomingCO() {
        final var size = getIncomingSize();
        for (int i = 0; i < size; i++) {
            removeOperandCO(i);
        }
    }

    public boolean isIncompleted() {
        return incompleted;
    }

    public void markAsCompleted() {
        this.incompleted = false;
    }

    public Variable getVariable() {
        return variable;
    }

    public List<BasicBlock> getIncomingBlocks() {
        return getParent().orElseThrow().getPredecessors();
    }

    public List<Value> getIncomingValues() {
        return getOperands();
    }

    public static final class IncomingInfo {
        public IncomingInfo(Value value, BasicBlock block) {
            this.value = value;
            this.block = block;
        }

        public Value getValue() {
            return value;
        }

        public BasicBlock getBlock() {
            return block;
        }

        public final Value value;
        public final BasicBlock block;
    }

    public Iterable<IncomingInfo> getIncomingInfos() {
        return () -> new Iterator<>() {
            int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < getIncomingSize();
            }

            @Override
            public IncomingInfo next() {
                final var info = new IncomingInfo(getIncomingValue(idx), getIncomingBlock(idx));
                idx += 1;
                return info;
            }
        };
    }

    public int getIncomingSize() {
        if (getOperandSize() != getIncomingBlocks().size()) {
            throw new IRTypeException(this, "Phi must have the same amount of incoming variable and blocks");
        }

        return getOperandSize();
    }

    public BasicBlock getIncomingBlock(int index) {
        return getIncomingBlocks().get(index);
    }

    public Value getIncomingValue(int index) {
        return getIncomingValues().get(index);
    }

    private final Variable variable;
    private boolean incompleted;
}
