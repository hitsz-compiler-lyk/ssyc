# 后端架构简介

对于后端而言，主要分为了三个部分：
IR --> LIR (Low level IR)
LIR -- > ArmCode
寄存器分配

## 继承体系与类别总览

LIR的指令和操作数体系如下：

```
ArmFunction
    > ArmBlock
        > ArmInst
            > Unary
                : INeg, FNeg,
            > Binary
                : IAdd, ISub, IRsb, IMul, IDiv, FAdd, FSub, FMul, FDiv,
            > Terney
                : IMulAdd, IMulSub, FMulAdd, FMulSub,
            > FloatToInt
            > IntToFloat
            > Move
            > Call
            > Return
            > Load
            > Store
            > Branch
            > Cmp

Operand
    > Addr
    > Imm
        > IImm
        > FImm
    > Reg
        > IPhyReg
        > FPhyReg
        > IVirtualReg
        > FVirtualReg
```

对于ArmInst均是参考Arm指令设计而来, 其都共有着
cond: Any, Ge, Gt, Eq, Ne, Le, Lt
> 表示改指令在什么情况下执行（目前只有Branch跳转指令和MOV指令涉及）

operands：表示该指令所拥有的操作数
> 并不是所有的指令都拥有操作数 比如Return和Branch指令 每个指令所拥有的操作数个数也不一定相同，比如Binary就有三个操作数 而Ternay则有四个操作数

### 指令接口

对于指令的构造函数而言，如果第一次参数时ArmBlock则表示将该指令自动插入到该ArmBlock的尾部

对于每个指令而言，可以通过

```java
getRegUse()
```
获得使用寄存器

```java
getRegDef()
```
获得定值寄存器

使用寄存器和定值寄存器保证是一个寄存器类型，而不会是一个立即数或者是地址，这个概念来源于Live Interval计算（活跃区间计算）

对于未进行寄存器分配时的操作数，也有可能是物理寄存器，这是因为在函数传参和返回时会涉及到固定的物理寄存器

对于每个指令而言，有三个接口可以替换操作数

```java
replaceOperand(Operand oldOp, Operand op)
```
将指令中所有的操作数为操作数oladOp替换为操作数op

```java
replaceDefOperand(Operand oldOp, Operand op)
```
将指令中所有的使用操作数为操作数oladOp替换为操作数op

```java
replaceUseOperand(Operand oldOp, Operand op)
```
将指令中所有的定值操作数为操作数oladOp替换为操作数op

因为替换是把所有同类型的操作数都进行替换（可能会出现指令ADD VR1 VR2 VR2，为相同的虚拟寄存器的情况），因此如果不希望全部替换则可以只替换使用操作数的部分和定制操作数的部分（理论上对于使用操作数为同一个操作数时，同时替换肯定是最优解？）

对于寄存器分配可能会用到的指令为 ArmInstLoad 和 ArmInstStroe
```java
ArmInstLoad(Operand dst, Operand addr, Operand offset)
```
将地址[addr, offset]中的值存入寄存器drc中

```java
ArmInstStroe(Operand src, Operand addr, Operand offset)
```
将寄存器src的值存入[addr, offset]

对于寄存器分配往往addr为寄存器SP 获得offset的方法可以参考栈结构解释当中的内容

### 寄存器接口

对于创建一个物理寄存器 可以直接通过
```java
new IPhyReg("r0"),  new IPhyReg("sp"),  new IPhyReg(0),  new IPhyReg(13)
```
其中第一个和第三个等价 第二个和第四个等价

目前可以使用的寄存器:

通用寄存器: r0-r12 r14(lr)

向量寄存器: s0-s7 (其实存在s0-s31, 但其余寄存器的使用可能会出现向量化指令, 早期先只用s0-s7)

IVirtualReg 应该分配通用寄存器
FVirtualReg 应该分配向量寄存器

对于寄存器有接口:
```java
Set<ArmInst> getInstSet()
```
其可以返回该寄存器在哪些指令当中使用了，每次该寄存器被替换了则会在这个Set中自动删除

但实际上这个只对虚拟寄存器是有效的，因为逻辑上相同的虚拟寄存器也是同一个实例，但对于物理寄存器而言，逻辑上相同的物理寄存器并不一定是同一个实例。

### 栈结构

对于在 Linux ARM 上，栈向低地址方向增长

对于从IR -> LIR的过程中的Alloc称为CAlloc 而寄存器分配过程中的Alloc称为Alloc

对于最终的某个函数的栈结构:

```
┌────────────────────────────┐
│                            │
│       ParameterX  4        │
├────────────────────────────┤
│                            │
│           ......           │
├────────────────────────────┤
│                            │
│       Parameter2  4        │
├────────────────────────────┤
│                            │
│       Parameter1  4        │
├────────────────────────────┤
│                            │
│         AllocY    4        │
├────────────────────────────┤
│                            │
│           ......           │
├────────────────────────────┤
│                            │
│         Alloc2    4        │
├────────────────────────────┤
│                            │
│         Alloc1    4        │
├────────────────────────────┤
│                            │
│                            │
│                            │
│         CAllocZ   Nz       │
│                            │
│                            │
├────────────────────────────┤
│                            │
│           ......           │
│                            │
├────────────────────────────┤
│                            │
│         CAlloc1   N1       │
│                            │
└────────────────────────────┘
```

其对应的指令效果如下:
```
                                ┌────────────────────────────┐
                                │                            │
                                │       ParameterX  4        │
                                ├────────────────────────────┤
                                │                            │
                                │           ......           │
                                ├────────────────────────────┤
                                │                            │
                                │       Parameter2  4        │
                                ├────────────────────────────┤
                                │                            │
 ┌────────────┐                 │       Parameter1  4        │
 │            ├────────┐        ├────────────────────────────┤
 │   Alloc1   │        │        │                            │
 └─────┬──────┘   ┌────┼───────►│         AllocY    4        │
    ...│...       │    │        ├────────────────────────────┤
       ▼          │    │        │                            │
 ┌────────────┐   │    │        │           ......           │
 │            │   │    │        ├────────────────────────────┤
 │  CAlloc 1  ├───┼──┐ │        │                            │
 └─────┬──────┘   │  │ │  ┌────►│         Alloc2    4        │
    ...│...       │  │ │  │     ├────────────────────────────┤
       ▼          │  │ │  │     │                            │
 ┌────────────┐   │  │ └──┼────►│         Alloc1    4        │
 │            │   │  │    │     ├────────────────────────────┤
 │   Alloc2   ├───┼──┼────┘     │                            │
 └─────┬──────┘   │  │          │                            │
    ...│...       │  │          │                            │
       ▼          │  │    ┌────►│         CAllocZ   Nz       │
 ┌────────────┐   │  │    │     │                            │
 │            │   │  │    │     │                            │
 │  CAlloc Z  ├───┼──┼────┘     ├────────────────────────────┤
 └─────┬──────┘   │  │          │                            │
    ...│...       │  │          │           ......           │
       ▼          │  │          │                            │
 ┌────────────┐   │  │          ├────────────────────────────┤
 │            │   │  │          │                            │
 │   AllocY   ├───┘  └─────────►│         CAlloc1   N1       │
 └────────────┘                 │                            │
                                └────────────────────────────┘
```
对于一个函数最终的栈而言, 其最低层是CAlloc所分配的空间，再往上是寄存器分配所导致的Alloc, 再往上是该函数传入的在栈中的参数

因此对于在IR -> LIR的过程中 只涉及了CAlloc 其工作原理是获得当前的栈偏移 offset = func.getStackSize() 其地址就为 [sp,  offset], 然后增加当前的栈大小，其为分配的空间大小n: func.addStackSize(n)

对于CAlloc指令所导致的栈分配都先放在栈底，然后再是寄存器分配中的栈分配，其原理和CAlloc是一致的，先获得栈偏移 offset = func.getStackSize() 其地址就为 [sp, offset], 然后增加当前的栈大小: func.addStackSize(4)

对于最终的CodeGen而言，每个函数先在最开始执行 SUB SP SP func.getStackSize() 在最后再加上 ADD SP SP func.getStackSize() 因此对于每个函数最顶端的参数的获取，实际上是需要做一个栈修复，只需要对于每个函数的startBlock中的Load指令的offset增加func.getStackSize()