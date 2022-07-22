package top.origami404.ssyc.backend.regallocator;

import top.origami404.ssyc.backend.codegen.CodeGenManager;

public interface RegAllocator {
    String getName();

    void run(CodeGenManager manager);
}
