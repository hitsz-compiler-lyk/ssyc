package top.origami404.ssyc.ir.type;

import java.util.List;

public interface IRType {
    public IRTyKind getKind();

    public static final SimpleIRTy IntTy    = new SimpleIRTy(IRTyKind.Int);  
    public static final SimpleIRTy FloatTy  = new SimpleIRTy(IRTyKind.Float);
    public static final SimpleIRTy VoidTy   = new SimpleIRTy(IRTyKind.Void); 
    public static final SimpleIRTy BoolTy   = new SimpleIRTy(IRTyKind.Bool); 

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
}
