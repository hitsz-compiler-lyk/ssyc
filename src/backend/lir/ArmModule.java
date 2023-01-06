package backend.lir;

import ir.GlobalVar;
import ir.constant.ArrayConst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArmModule {
    public Map<String, GlobalVar> getGlobalVariables() {
        return globalVariables;
    }

    public Map<String, ArrayConst> getArrayConstants() {
        return arrayConstants;
    }

    public List<ArmFunction> getFunctions() {
        return functions;
    }

    private final Map<String, GlobalVar> globalVariables = new HashMap<>();
    private final Map<String, ArrayConst> arrayConstants = new HashMap<>();
    private final List<ArmFunction> functions = new ArrayList<>();
}
