package pass.ir.util;

import ir.Value;
import ir.inst.*;
import ir.visitor.InstructionVisitor;

import java.util.stream.Collectors;

public abstract class SimpleInstructionCloner implements InstructionVisitor<Instruction> {
    protected abstract <T extends Value> T getNewOperand(T value);

    @Override
    public Instruction visit(final Instruction inst) {
        final var newInst = InstructionVisitor.super.visit(inst);
        inst.getSymbolOpt().ifPresent(newInst::setSymbol);
        return newInst;
    }

    @Override
    public Instruction visitBinaryOpInst(final BinaryOpInst inst) {
        final var lhs = getNewOperand(inst.getLHS());
        final var rhs = getNewOperand(inst.getRHS());
        return new BinaryOpInst(inst.getKind(), lhs, rhs);
    }

    @Override
    public Instruction visitBoolToIntInst(final BoolToIntInst inst) {
        final var from = getNewOperand(inst.getFrom());
        return new BoolToIntInst(from);
    }

    @Override
    public Instruction visitBrCondInst(final BrCondInst inst) {
        final var currBB = getNewOperand(inst.getParent());
        final var cond = getNewOperand(inst.getCond());
        final var trueBB = getNewOperand(inst.getTrueBB());
        final var falseBB = getNewOperand(inst.getFalseBB());
        return new BrCondInst(currBB, cond, trueBB, falseBB);
    }

    @Override
    public Instruction visitBrInst(final BrInst inst) {
        final var currBB = getNewOperand(inst.getParent());
        final var nextBB = getNewOperand(inst.getNextBB());
        return new BrInst(currBB, nextBB);
    }

    @Override
    public Instruction visitCallInst(final CallInst inst) {
        final var callee = getNewOperand(inst.getCallee());
        final var args = inst.getArgList().stream()
            .map(this::getNewOperand).collect(Collectors.toList());
        return new CallInst(callee, args);
    }

    @Override
    public Instruction visitCAllocInst(final CAllocInst inst) {
        return new CAllocInst(inst.getAllocType());
    }

    @Override
    public Instruction visitCmpInst(final CmpInst inst) {
        final var lhs = getNewOperand(inst.getLHS());
        final var rhs = getNewOperand(inst.getRHS());
        return new CmpInst(inst.getKind(), lhs, rhs);
    }

    @Override
    public Instruction visitFloatToIntInst(final FloatToIntInst inst) {
        final var from = getNewOperand(inst.getFrom());
        return new FloatToIntInst(from);
    }

    @Override
    public Instruction visitGEPInst(final GEPInst inst) {
        final var ptr = getNewOperand(inst.getPtr());
        final var indices = inst.getIndices().stream()
            .map(this::getNewOperand).collect(Collectors.toList());
        return new GEPInst(ptr, indices);
    }

    @Override
    public Instruction visitIntToFloatInst(final IntToFloatInst inst) {
        final var from = getNewOperand(inst.getFrom());
        return new IntToFloatInst(from);
    }

    @Override
    public Instruction visitLoadInst(final LoadInst inst) {
        final var ptr = getNewOperand(inst.getPtr());
        return new LoadInst(ptr);
    }

    @Override
    public Instruction visitMemInitInst(final MemInitInst inst) {
        final var ptr = getNewOperand(inst.getArrayPtr());
        final var init = getNewOperand(inst.getInit());
        return new MemInitInst(ptr, init);
    }

    public abstract Instruction visitPhiInst(final PhiInst inst);

    @Override
    public Instruction visitReturnInst(final ReturnInst inst) {
        final var returnValueOpt = inst.getReturnValue().map(this::getNewOperand);
        return returnValueOpt.map(ReturnInst::new).orElse(new ReturnInst());
    }

    @Override
    public Instruction visitStoreInst(final StoreInst inst) {
        final var ptr = getNewOperand(inst.getPtr());
        final var val = getNewOperand(inst.getVal());
        return new StoreInst(ptr, val);
    }

    @Override
    public Instruction visitUnaryOpInst(final UnaryOpInst inst) {
        final var arg = getNewOperand(inst.getArg());
        return new UnaryOpInst(inst.getKind(), arg);
    }
}
