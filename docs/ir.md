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
```

每一个指令可以有 

```sh
Argument
    |- Value
        |- VarReg (Int, Float)
        |- Imm    (Int, Float)
    |- PtrReg     (Int, Float, Array)
```