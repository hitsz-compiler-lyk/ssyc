package pass.ir;

import ir.BasicBlock;
import ir.GlobalVar;
import ir.Module;
import ir.Value;
import ir.analysis.AnalysisInfo;
import ir.inst.Instruction;
import ir.inst.LoadInst;
import ir.inst.PhiInst;
import ir.inst.StoreInst;
import utils.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GlobalVariableToValue implements IRPass {
    @Override
    public void runPass(final Module module) {
        if (module.getNonExternalFunction().size() != 1) {
            return;
        }

        final var main = module.getNonExternalFunction().iterator().next();

        for (final var block : main) {
            final var currDef = new GVCurrDef();
            // only should run once
            Log.ensure(!block.containsAnalysisInfo(GVCurrDef.class));
            block.addAnalysisInfo(currDef);
        }

        final var entryInfo = main.getEntryBBlock().getAnalysisInfo(GVCurrDef.class);
        module.getVariables().forEach(entryInfo::init);

        for (final var block : main) {
            final var currDef = block.getAnalysisInfo(GVCurrDef.class);
            for (final var inst : block) {
                dealWithPossibleLoad(block, currDef, inst);
                dealWithPossibleStore(currDef, inst);
            }
        }

        main.forEach(this::fillIncompletedPhiForBlock);
        main.forEach(BasicBlock::adjustPhiEnd);

        IRPass.copyForChange(module.getVariables()).stream()
            .filter(GlobalVar::isVariable)
            .forEach(module.getVariables()::remove);
    }

    void dealWithPossibleLoad(BasicBlock currBlock, GVCurrDef currDef, Instruction instruction) {
        if (instruction instanceof final LoadInst load) {

            final var ptr = load.getPtr();
            if (ptr instanceof final GlobalVar gv) {

                if (gv.isVariable()) {
                    final var def = findOrInsertPhi(currBlock, currDef, gv);
                    load.replaceAllUseWith(def);
                    load.freeAll();
                }
            }
        }
    }

    private Value findOrInsertPhi(BasicBlock currBlock, GVCurrDef currDef, GlobalVar gv) {
        if (currDef.contains(gv)) {
            return currDef.get(gv);
        } else {
            final var phi = currDef.makePhiAndAddToIncomplete(gv);
            currBlock.addPhi(phi);
            return phi;
        }
    }

    void dealWithPossibleStore(GVCurrDef currDef, Instruction instruction) {
        if (instruction instanceof final StoreInst store) {

            final var ptr = store.getPtr();
            if (ptr instanceof final GlobalVar gv) {

                if (gv.isVariable()) {
                    final var value = store.getVal();
                    currDef.kill(gv, value);
                    store.freeAll();
                }
            }
        }
    }

    void fillIncompletedPhiForBlock(BasicBlock block) {
        final var currDef = block.getAnalysisInfo(GVCurrDef.class);

        final var oldPhis = IRPass.copyForChange(currDef.getIncompletePhis());
        for (final var entry : oldPhis.entrySet()) {
            final var gv = entry.getKey();
            final var phi = entry.getValue();

            fillIncompletedPhi(gv, phi, block);
        }

        block.adjustPhiEnd();
    }

    Value fillIncompletedPhi(GlobalVar gv, PhiInst phi, BasicBlock block) {
        final var incomingValues = block.getPredecessors().stream()
            .map(pred -> findDefinition(pred, gv))
            .collect(Collectors.toList());

        phi.setIncomingCO(incomingValues);
        return phi;
    }

    Value findDefinition(BasicBlock block, GlobalVar gv) {
        final var currDef = block.getAnalysisInfo(GVCurrDef.class);
        if (currDef.contains(gv)) {
            return currDef.get(gv);
        }

        final var phi = currDef.makePhi(gv);
        block.addPhi(phi);
        return fillIncompletedPhi(gv, phi, block);
    }
}

class GVCurrDef implements AnalysisInfo {
    public void init(GlobalVar gv) {
        currDef.put(gv, gv.getInit());
    }

    public boolean contains(GlobalVar gv) {
        return currDef.containsKey(gv);
    }

    public PhiInst makePhiAndAddToIncomplete(GlobalVar gv) {
        final var phi = makePhi(gv);
        incompletePhis.put(gv, phi);
        return phi;
    }

    public PhiInst makePhi(GlobalVar gv) {
        Log.ensure(!currDef.containsKey(gv));

        final var phi = new PhiInst(gv.getType().getBaseType(), gv.getSymbol());
        currDef.putIfAbsent(gv, phi);
        return phi;
    }

    public Value get(GlobalVar gv) {
        Log.ensure(currDef.containsKey(gv));
        return currDef.get(gv);
    }

    public void kill(GlobalVar gv, Value value) {
        currDef.put(gv, value);
    }

    public Map<GlobalVar, PhiInst> getIncompletePhis() {
        return incompletePhis;
    }

    private final Map<GlobalVar, PhiInst> incompletePhis = new HashMap<>();
    private final Map<GlobalVar, Value> currDef = new HashMap<>();
}
