# IR 架构简介

> 本文中的 "内存" "寄存器" 等术语均是 IR 意义下的, 而非汇编意义下的

IR 采用类 LLVM IR 的 "带无限寄存器的寄存器式 IR", 要求满足 SSA form, 并且支持 Alloc, Load, Store 与 `mem2reg`.

主要类继承层级如下:

```sh
Inst
   |- ArithInst     # 算术类型
   |- LoadInst      # 从内存中加载值到寄存器 
   |- StoreInst     # 将寄存器中的值存入内存
   |- AllocaInst    # 开辟内存区域
   |- BranchInst    # 跳转
   |- PhiInst       # Phi 语句
   |- ReturnInst
```

每一个指令可以有 

```sh
Argument
    |- Value
        |- VarReg (Int, Float)
        |- Imm    (Int, Float)
    |- PtrReg     (Int, Float, Array)
    |- Function   (Function)
    |- BBlock     (BBlock)
```

## 函数设计

每一个函数都至少有两个基本块 `entry` 跟 `exit`, 对函数体内 `return` 语句的翻译总是翻译为 `br exit`. 在 `exit` 块内统一对返回值做 Phi (如果有返回值的话). `ReturnInst` 在且只在 `exit` 块末尾出现, 且总为该块最后一条指令, 且不是 Terminaor.

## 术语约定

内部表示的名字 (包括 IR 的人类可读输出格式) 均称为 `repr` (取 Representation 之意). 从源代码中来的名字, 或未经修饰的名字, 统一称为 `name`. 一般而言, `repr` = `修饰前缀` + `name`.

对象的 `getName` 一律返回 `name`; 对象的 `toString` 一律返回 `repr`.

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