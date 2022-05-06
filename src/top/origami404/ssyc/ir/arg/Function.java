package top.origami404.ssyc.ir.arg;

import java.util.HashMap;
import java.util.Map;

public class Function {
    public Function(String name) {
        this.name = name;
        this.blocks = new HashMap<>();
        this.newBlock("entry");
    }

    public String getName() {
        return name;
    }

    public void newBlock(String name) {
        blocks.put(name, new BBlock(name));
    }

    public BBlock getBlock(String name) {
        return blocks.get(name);
    }

    private String name;
    private Map<String, BBlock> blocks;
    
}
