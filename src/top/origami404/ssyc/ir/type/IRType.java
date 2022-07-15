package top.origami404.ssyc.ir.type;

import java.util.List;

 public interface IRType {
     IRTyKind getKind();

    /**
     * @return 该类型的对象占内存的大小 (字节)
     */
     int getSize();

     SimpleIRTy IntTy    = new SimpleIRTy(IRTyKind.Int, 4);
     SimpleIRTy FloatTy  = new SimpleIRTy(IRTyKind.Float, 4);
     SimpleIRTy VoidTy   = new SimpleIRTy(IRTyKind.Void);
     SimpleIRTy BoolTy   = new SimpleIRTy(IRTyKind.Bool, 1);
     SimpleIRTy BBlockTy = new SimpleIRTy(IRTyKind.BBlock);

     static ArrayIRTy createArrayTy(int elementNum, IRType elementType) {
        return new ArrayIRTy(elementNum, elementType);
    }

     static PointerIRTy createDecayType(ArrayIRTy type) {
        return IRType.createPtrTy(type.getElementType());
    }

     static PointerIRTy createPtrTy(IRType baseType) {
        return new PointerIRTy(baseType);
    }

     static FunctionIRTy createFuncTy(IRType returnType, List<IRType> paramTypes) {
        return new FunctionIRTy(returnType, paramTypes);
    }

    default boolean isInt()     { return this.equals(IRType.IntTy);     }
    default boolean isFloat()   { return this.equals(IRType.FloatTy);   }
    default boolean isVoid()    { return this.equals(IRType.VoidTy);    }
    default boolean isBool()    { return this.equals(IRType.BoolTy);    }
    default boolean isPtr()     { return this instanceof PointerIRTy;   }
    default boolean isArray()   { return this instanceof ArrayIRTy;     }
    default boolean isFunc()    { return this instanceof FunctionIRTy;  }
    default boolean canBeElement() { return isInt() || isFloat() || isArray(); }
}
