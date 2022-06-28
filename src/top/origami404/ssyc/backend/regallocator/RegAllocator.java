package top.origami404.ssyc.backend.regallocator;

import top.origami404.ssyc.backend.codegen.CodeGenManager;

public interface RegAllocator {
    public String getName();

    public void run(CodeGenManager manager);
}
