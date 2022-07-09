package top.origami404.ssyc.ir.type;

import java.util.List;

public interface IRType {
    public IRTyKind getKind();

    /**
     * @return 该类型的对象占内存的大小 (字节)
     */
    public int getSize();

    public static final SimpleIRTy IntTy    = new SimpleIRTy(IRTyKind.Int, 4);
    public static final SimpleIRTy FloatTy  = new SimpleIRTy(IRTyKind.Float, 4);
    public static final SimpleIRTy VoidTy   = new SimpleIRTy(IRTyKind.Void);
    public static final SimpleIRTy BoolTy   = new SimpleIRTy(IRTyKind.Bool, 1);
    public static final SimpleIRTy BBlockTy = new SimpleIRTy(IRTyKind.BBlock);

    public static ArrayIRTy createArrayTy(int elementNum, IRType elementType) {
        return new ArrayIRTy(elementNum, elementType);
    }

    public static PointerIRTy createPtrTy(IRType baseType) {
        return new PointerIRTy(baseType);
    }

    public static FunctionIRTy createFuncTy(IRType returnType, List<IRType> paramTypes) {
        return new FunctionIRTy(returnType, paramTypes);
    }

    /**
     * 创建形如 [4 x i32]* 的类型, 即指向数组的指针类型, 一般用于类 C 语言的数组名的翻译
     * @param elementNum 数组元素数量
     * @param elementType 数组元素类型
     * @return 所求的指针类型
     */
    public static PointerIRTy createArrayPtrTy(int elementNum, IRType elementType) {
        return createPtrTy(createArrayPtrTy(elementNum, elementType));
    }

    default boolean isInt()     { return this.equals(IRType.IntTy);     }
    default boolean isFloat()   { return this.equals(IRType.FloatTy);   }
    default boolean isVoid()    { return this.equals(IRType.VoidTy);    }
    default boolean isBool()    { return this.equals(IRType.BoolTy);    }
    default boolean isPtr()     { return this instanceof PointerIRTy;   }
    default boolean isArray()   { return this instanceof ArrayIRTy;     }
    default boolean isFunc()    { return this instanceof FunctionIRTy;  }
}
