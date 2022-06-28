package top.origami404.ssyc.backend.codegen;

import java.util.ArrayList;

import top.origami404.ssyc.backend.arm.ArmFunction;

public class CodeGenManager {
    private ArrayList<ArmFunction> functions;

    public CodeGenManager() {
        functions = new ArrayList<ArmFunction>();
    }

    public ArrayList<ArmFunction> getFunctions() {
        return functions;
    }
}
