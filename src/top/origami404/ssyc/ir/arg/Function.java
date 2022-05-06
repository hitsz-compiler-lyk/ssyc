package top.origami404.ssyc.ir.arg;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import top.origami404.ssyc.ir.type.FuncType;
import top.origami404.ssyc.ir.inst.PhiInst;
import top.origami404.ssyc.ir.inst.ReturnInst;

public class Function extends Argument {
    public Function(String name, FuncType type) {
        super(Kind.Function);

        this.name = name;
        this.blocks = new HashMap<>();
        this.newBlock("entry");
        this.newBlock("exit");
        
        this.returnVar = switch (type.getReturnType()) {
            case VoidType   -> Optional.empty();
            case IntType    -> Optional.of(VarReg.newIntTemp());
            case FloatType  -> Optional.of(VarReg.newFloatTemp());
        };
    }

    public String getName() {
        return name;
    }

    public void newBlock(String name) {
        final var blockName = this.name + "_" + name;
        blocks.put(name, new BBlock(blockName));
    }

    public BBlock getBlock(String name) {
        return blocks.get(name);
    }

    public void registerReturnValue(VarReg val) {
        assert returnVar.isPresent() : "Cannot return a value from a void function";

        final var oldRetVar = returnVar.get();

        // 合并新旧返回值
        final var exitBlock = getBlock("exit");
        final var newRetVar = VarReg.newSameTemp(oldRetVar);
        exitBlock.insertInst(new PhiInst(newRetVar, oldRetVar, val));
        
        // 更新储存着返回值的寄存器
        returnVar = Optional.of(newRetVar);
    }

    // should be called once and only once at the end
    public void insertReturn() {
        final var exitBlock = getBlock("exit");

        if (returnVar.isEmpty()) {
            exitBlock.insertInst(new ReturnInst());
        } else {
            final var retVar = returnVar.get();
            exitBlock.insertInst(new ReturnInst(retVar));
        }
    }

    private String name;
    private Map<String, BBlock> blocks;
    private Optional<VarReg> returnVar;
}
