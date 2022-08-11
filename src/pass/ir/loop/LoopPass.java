package pass.ir.loop;

public interface LoopPass {
    void runOnLoop(CanonicalLoop loop);
}
