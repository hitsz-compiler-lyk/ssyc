package ir.inst;

import frontend.SourceCodeSymbol;
import ir.BasicBlock;
import ir.IRVerifyException;
import ir.IRVerifyException.SelfReferenceException;
import ir.Value;
import ir.type.IRType;

import java.util.Iterator;
import java.util.List;

public class PhiInst extends Instruction {
    /**
     * @param symbol 最开始的 Phi 所代表的变量的 symbol, 它既是最初的 getSymbol(), 也是永远的 waitFor
     */
    public PhiInst(IRType type, SourceCodeSymbol symbol) {
        super(InstKind.Phi, type);
        super.setSymbol(symbol);

        this.waitFor = symbol;
        this.incompleted = true;
    }

    public void setIncomingCO(List<Value> incomingValues) {
        ensure(incomingValues.size() == getIncomingBlocks().size(),
            "Phi must have the same amount of incoming variable and blocks");
        ensure(getIncomingSize() == 0,
            "Phi could only set incoming once");

        setIncomingValueWithoutCheckingPredecessorsCO(incomingValues);
    }

    public void setIncomingValueWithoutCheckingPredecessorsCO(List<Value> incomingValues) {
        super.addAllOperandsCO(incomingValues);
    }

    public void clearIncomingCO() {
        removeOperandAllCO();
    }

    public boolean isIncompleted() {
        return incompleted;
    }

    public void markAsCompleted() {
        this.incompleted = false;
    }

    public List<BasicBlock> getIncomingBlocks() {
        return getParentOpt().orElseThrow().getPredecessors();
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
        return getOperandSize();
    }

    public BasicBlock getIncomingBlock(int index) {
        return getIncomingBlocks().get(index);
    }

    public Value getIncomingValue(int index) {
        return getIncomingValues().get(index);
    }

    @Override
    public void verify() throws IRVerifyException {
        try {
            super.verify();
            final var valueCnt = getIncomingValues().size();
            final var blockCnt = getIncomingBlocks().size();
            ensure(valueCnt == blockCnt,
                    "Phi must have the same amount of incoming value and blocks (%d vs %d)".formatted(valueCnt, blockCnt));
        } catch (SelfReferenceException e) {
            // Do nothing
        }
    }

    public SourceCodeSymbol getWaitFor() {
        return waitFor;
    }

    @Override
    public String toString() {
        return super.toString() + "(for " + getWaitFor() + ")";
    }

    private final SourceCodeSymbol waitFor;
    private boolean incompleted;
}
