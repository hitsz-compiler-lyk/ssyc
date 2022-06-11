# IR 架构简介

> 本文中的 "内存" "寄存器" 等术语均是 IR 意义下的, 而非汇编意义下的

IR 采用类 LLVM IR 的 "带无限寄存器的寄存器式 IR", 要求满足 SSA form, 并且支持 Alloc, Load, Store 与 `mem2reg`. 其内存形式 (in-memory form) 为一很大的, 通过 Java 对象的引用间接实现的 DAG.

## 类设计

### 继承体系

```
Value
    > User
        > Instruction (InstKind)
            > BinaryOpInst
                : IAdd, ISub, IMul, IDiv, IMod, FAdd, FSub, FMul, FDiv, 
            > UnaryOpInst
                : INeg, FNeg,
            > CmpInst
                : ICmpEq, ICmpNe, ICmpGt, ICmpGe, ICmpLt, ICmpLe, FCmpEq, FCmpNe, FCmpGt, FCmpGe, FCmpLt, FCmpLe,
            > BrCondInst
            > BranchInst
            > CallInst
            > ReturnInst
            > AllocInst
            > LoadInst
            > StoreInst
            > GEPInst
    > BasicBlock
    > Parameter
    > Constant
        > IntConstant
        > FloatConstant

IRType (IRTyKind)
    > SimpleIRTy
        : Void, Bool, Int, Float, 
    > ArrayIRTy
    > PointerIRTy
    > FunctionIRTy
```

其中每一层缩进代表一个逻辑上的 `is-a`/从属 关系, `>` 代表该关系使用 Java 中的继承来表示, `:` 代表该关系通过该类的某个类别枚举 (`xxxKind`) 来实现. 

#### 枚举实现的 "子类关系"

为了减少无意义的重复代码, 很多操作上高度相同的东西会被放在一个类里实现, 通过一个特殊的枚举来标识不同的类别. 这种时候凡是细化类别的操作跟判断大抵上都会放在枚举中实现. 譬如二元运算指令 `BinaryOpInst` 就通过 `InstKind` 来区分加减乘除, 并且凡是用作 "判断这条指令是不是操作整数的" 这种细化方法, 都会放在 `InstKind` 里.

### IR 中的类型

### use-def 关系

> 本节内容与 LLVM IR 一致

IR 里的被使用者(Usee)是 `Value` 类, 任何一个 `Value` 类的实例都有可能被被人 "使用", 所以 `Value` 类提供了方法 `getUserList()` 来获得使用了这个实例的所有使用者. 使用者 (User) 是类 `User` 的实例, 任何一个 `User` 都有可能 "使用" 其他 `Value` 实例, 被使用者使用的 Value 称之为该使用者的 Operand (参数), 可以通过方法 `getOperandList()` 获得一个使用者的所有使用的实例.

一个 User(Value) 使用的其他 Value 称之为这个 Value 的 operand, 而使用这个 Value 的 User 称之为这个 Value 的 user.

```java
    User a;

    a.getOperandList()  //==>  获得这个 User 使用的所有 Value (即它的参数)
    a.getUserList()     //==>  获得使用这个 Value 的所有 User (即它被谁当作了参数)
```

### `Parameter`

LLVM IR 中的 `Argument` 实际上存的是函数的形参信息, 所以更名为 `Parameter`. 之所以其是一个 `Value`, 是因为这样函数内的指令便可以使用某个 `Parameter` 实例来指代函数中的某个形参了.

## 术语约定

### 名字 (name) 与表示 (representation)

内部表示的名字 (包括 IR 的人类可读输出格式) 均称为 `repr` (取 Representation 之意). 从源代码中来的名字, 或未经修饰的名字, 统一称为 `name`. 一般而言, `repr` = `修饰前缀` + `name`.

对象的 `getName` 一律返回 `name`; 对象的 `toString` 一律返回 `repr`.

### 类型 (type) 与类别 (kind)

凡是与源语言语义有关的, 比如表示一段二进制串的可用操作, 或者是 IR 里的值的种类的, 称之为类型 (Type). 凡是用于代码实现里用来做 RTTI, 或者标志同一个类的实例的种类的, 称之为类别 (kind). 

比如一条 IR 指令有类型也有类别. 其类型 (`inst.getType()`) 就是指其 IR 里返回值的类型, 比如 `INT`, `FLOAT`, `BOOL`, `[4 * INT]`. 其类别 (`Inst.getKind()`) 就是它具体是哪一种 IR, 比如 `IAdd`, `ISub`, `FCmpGt`.

## SSA Construction

Main ref: [Simple and Efficient Construction of Static Single Assignment Form](https://pp.info.uni-karlsruhe.de/uploads/publikationen/braun13cc.pdf)

### 块内局部变量

对于一个基本块内而言, 可以直接通过版本化的方式来构造 SSA. 版本化后的块称为填充的 (filled). 但是简单的版本化无法解决块外定义的变量.

### 块外变量

若块只有一个前继, 那么直接递归搜索直到找到定义. 如果有多个前继, 那就分别向上搜索并插入 phi.

为了防止无穷递归, 事先在当前块头插入一个空白 phi 当作变量的定义.

### 未完整的 CFG

称一个块是封闭 (sealed) 的, 当且仅当其不可能再有新的前继. 若一个块未封闭, 那么对于待找变量, 先插入一个空白 phi 并记为 incomplete. 等到块封闭了, 再一起解决.

> 填充好的块能当别人的前继, 封闭好的块能知道自己所有的前继

在不含 goto 的语言里, 一遍过, 总能直到块是什么时候封闭的. 并且可以保证封闭的块的前继是封闭的.

### 消除多余 Phi

同一个 phi 内有多个相同变量, 或者一个 phi 的参数只含结果跟另一个, 这种phi都是多余的可消去的.

## 函数翻译设计

> 待评估的想法

每一个函数都至少有两个基本块 `entry` 跟 `exit`, 对函数体内 `return` 语句的翻译总是翻译为 `br exit`. 在 `exit` 块内统一对返回值做 Phi (如果有返回值的话). `ReturnInst` 在且只在 `exit` 块末尾出现, 且总为该块最后一条指令, 且不是 Terminaor.
