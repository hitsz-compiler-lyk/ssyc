package pass.ir;

import frontend.info.CurrDefInfo;
import ir.Module;

/** 清理不会再被使用的 currDef 以防止其拿着某个变量的 user 阻止其被消除 */
public class RemoveCurrDef implements IRPass {
    @Override
    public void runPass(final Module module) {
        for (final var function : module.getNonExternalFunction()) {
            for (final var block : function) {
                if (block.containsAnalysisInfo(CurrDefInfo.class)) {
                    final var info = block.getAnalysisInfo(CurrDefInfo.class);
                    info.freeFromUseDef();
                    block.removeAnalysisInfo(CurrDefInfo.class);
                }
            }
        }
    }
}
