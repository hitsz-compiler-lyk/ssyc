package top.origami404.ssyc.ir.type;

public enum BType implements Type {
    IntType, FloatType, VoidType;

    public static BType toBType(String typeName) {
        return switch (typeName) {
            case "int"      -> BType.IntType;
            case "float"    -> BType.FloatType;
            case "void"     -> BType.VoidType;
            default -> throw new RuntimeException("Illgeal type name for BType: " + typeName);
        };
    }

    @Override
    public boolean canBeAssignedTo(Type other) {
        if (other instanceof BType t) {
            if (t == VoidType || this == VoidType) {
                return false;
            } else {
                return this == t;
            }
        } else {
            return false;
        }
    }
}
