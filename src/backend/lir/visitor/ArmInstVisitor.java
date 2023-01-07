package backend.lir.visitor;

import backend.lir.inst.*;

public interface ArmInstVisitor<T> {
    default T visit(ArmInst inst) {
        if (inst instanceof ArmInstBinary) { return visitArmInstBinary((ArmInstBinary) inst); }
        if (inst instanceof ArmInstBranch) { return visitArmInstBranch((ArmInstBranch) inst); }
        if (inst instanceof ArmInstCall) { return visitArmInstCall((ArmInstCall) inst); }
        if (inst instanceof ArmInstCmp) { return visitArmInstCmp((ArmInstCmp) inst); }
        if (inst instanceof ArmInstFloatToInt) { return visitArmInstFloatToInt((ArmInstFloatToInt) inst); }
        if (inst instanceof ArmInstIntToFloat) { return visitArmInstIntToFloat((ArmInstIntToFloat) inst); }
        if (inst instanceof ArmInstLoad) { return visitArmInstLoad((ArmInstLoad) inst); }
        if (inst instanceof ArmInstLiteralPoolPlacement) { return visitArmInstLtorg((ArmInstLiteralPoolPlacement) inst); }
        if (inst instanceof ArmInstMove) { return visitArmInstMove((ArmInstMove) inst); }
        if (inst instanceof ArmInstParamLoad) { return visitArmInstParamLoad((ArmInstParamLoad) inst); }
        if (inst instanceof ArmInstReturn) { return visitArmInstReturn((ArmInstReturn) inst); }
        if (inst instanceof ArmInstStackAddr) { return visitArmInstStackAddr((ArmInstStackAddr) inst); }
        if (inst instanceof ArmInstStackLoad) { return visitArmInstStackLoad((ArmInstStackLoad) inst); }
        if (inst instanceof ArmInstStackStore) { return visitArmInstStackStore((ArmInstStackStore) inst); }
        if (inst instanceof ArmInstStore) { return visitArmInstStore((ArmInstStore) inst); }
        if (inst instanceof ArmInstTernary) { return visitArmInstTernary((ArmInstTernary) inst); }
        if (inst instanceof ArmInstUnary) { return visitArmInstUnary((ArmInstUnary) inst); }
        throw new RuntimeException("Unknown class: " + inst.getClass().getSimpleName());
    }

    T visitArmInstBinary(ArmInstBinary inst);
    T visitArmInstBranch(ArmInstBranch inst);
    T visitArmInstCall(ArmInstCall inst);
    T visitArmInstCmp(ArmInstCmp inst);
    T visitArmInstFloatToInt(ArmInstFloatToInt inst);
    T visitArmInstIntToFloat(ArmInstIntToFloat inst);
    T visitArmInstLoad(ArmInstLoad inst);
    T visitArmInstLtorg(ArmInstLiteralPoolPlacement inst);
    T visitArmInstMove(ArmInstMove inst);
    T visitArmInstParamLoad(ArmInstParamLoad inst);
    T visitArmInstReturn(ArmInstReturn inst);
    T visitArmInstStackAddr(ArmInstStackAddr inst);
    T visitArmInstStackLoad(ArmInstStackLoad inst);
    T visitArmInstStackStore(ArmInstStackStore inst);
    T visitArmInstStore(ArmInstStore inst);
    T visitArmInstTernary(ArmInstTernary inst);
    T visitArmInstUnary(ArmInstUnary inst);
}
