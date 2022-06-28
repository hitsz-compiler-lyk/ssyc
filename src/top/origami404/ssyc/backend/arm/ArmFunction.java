package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;

public class ArmFunction implements IListOwner<ArmBlock, ArmFunction> {
    private String name;

    private IList<ArmBlock, ArmFunction> blocks;

    @Override
    public String toString() {
        return "";
    }

    @Override
    public IList<ArmBlock, ArmFunction> getIList() {
        return blocks;
    }

    public String getName() {
        return name;
    }
}
