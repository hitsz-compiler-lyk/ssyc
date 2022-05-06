package top.origami404.ssyc.ir.arg;

import java.util.HashMap;
import java.util.Map;

public class Function extends Argument {
        this.name = name;
        this.blocks = new HashMap<>();
        this.newBlock("entry");
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

    private String name;
    private Map<String, BBlock> blocks;
    
}
