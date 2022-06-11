package top.origami404.ssyc.ir.type;

public enum IRTyKind {
    Void, Bool, Int, Float, 
    Pointer, Array, Function

    ;

    public boolean isVoid()     { return this == Void;      }
    public boolean isBool()     { return this == Bool;      }
    public boolean isInt()      { return this == Int;       }
    public boolean isFloat()    { return this == Float;     }
    public boolean isPtr()      { return this == Pointer;   }
    public boolean isArray()    { return this == Array;     }
    public boolean isFunc()     { return this == Function;  }
}
