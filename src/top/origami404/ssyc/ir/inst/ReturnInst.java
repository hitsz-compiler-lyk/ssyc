package top.origami404.ssyc.ir.inst;

import java.util.Optional;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class ReturnInst extends Instruction {
    public ReturnInst(BasicBlock block) {
        this(block, null);
    }

    public ReturnInst(BasicBlock block, Value returnVal) {
        super(block, InstKind.Ret, IRType.VoidTy);
        if (returnVal != null) {
            addOperandCO(returnVal);
        }
    }

    public Optional<Value> getReturnValue() {
        if (getOperandSize() > 0) {
            return Optional.of(getOperand(0));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var block = getParentOpt().orElseThrow();
        final var func = block.getParentOpt().orElseThrow();
        final var returnType = func.getType().getReturnType();
        final var returnValue = getReturnValue();

        ensureNot(returnType.isVoid() && returnValue.isPresent(),
                "A function returns Void shouldn't have a non-empty return stmt");
        ensureNot(!returnType.isVoid() && returnValue.isEmpty(),
                "A function returns non-Void shouldn't have an empty return stmt");
        ensure(returnValue.map(Value::getType).map(returnType::equals).orElse(true),
                "A function's return type should match the return value's type");
    }
}
