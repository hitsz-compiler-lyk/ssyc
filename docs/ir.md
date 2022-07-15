# IR 架构简介

> 本文中的 "内存" "寄存器" 等术语均是 IR 意义下的, 而非汇编意义下的

IR 采用类 LLVM IR 的 "带无限寄存器的寄存器式 IR", 要求满足 SSA form, 并且支持 Alloc, Load, Store 与 `mem2reg`. 其内存形式 (in-memory form) 为一很大的, 通过 Java 对象的引用间接实现的 DAG.

LLVM IR 中的 `Argument` 实际上存的是函数的形参信息, 所以更名为 `Parameter`. 之所以其是一个 `Value`, 是因为这样函数内的指令便可以使用某个 `Parameter` 实例来指代函数中的某个形参了.

## 继承体系与类别总览

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
            > PhiInst
            > FloatToIntInst
            > IntToFloatInst
            > MemInitInst
    > BasicBlock
    > Parameter
    > Constant
        > IntConst
        > FloatConst
        > BoolConst
        > ArrayConst
            > ZeroArrayConst
    > Function (maybe builtin)

IRType (IRTyKind)
    > SimpleIRTy
        : Void, Bool, Int, Float,
    > ArrayIRTy
    > PointerIRTy
    > FunctionIRTy
```

其中每一层缩进代表一个逻辑上的 `is-a`/从属 关系, `>` 代表该关系使用 Java 中的继承来表示, `:` 代表该关系通过该类的某个类别枚举 (`xxxKind`) 来实现.

### 枚举实现的 "子类关系"

为了减少无意义的重复代码, 很多操作上高度相同的东西会被放在一个类里实现, 通过一个特殊的枚举来标识不同的类别. 这种时候凡是细化类别的操作跟判断大抵上都会放在枚举中实现. 譬如二元运算指令 `BinaryOpInst` 就通过 `InstKind` 来区分加减乘除, 并且凡是用作 "判断这条指令是不是操作整数的" 这种细化方法, 都会放在 `InstKind` 里.

### 关于 MemInitInst

加入此指令主要是方便局部与全局数组的初始化. 在汇编实现上, 局部大数组的初始化一般通过调用 `memset` (零初始化) 或是 `memcpy` 实现; 而全局大数组的初始化则是通过 `.text` 实现. 考虑代码如下:

```c
// Type your code here, or load an example.
int global_nonzero[10] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
int global_zero[10] = {};

int main() {
    int local_nonzero[20] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    int local_nonzero_small[5] = {1, 2, 3, 4};
    int local_zero[10] = {};
    int local_uninitited[10];
}
```

对应的汇编输出为:

```armasm
main:
        @ 保存现场
        push    {r4, r5, r11, lr}
        add     r11, sp, #8
        sub     sp, sp, #192

        @ local_nonzero (调用 memcpy)
        ldr     r1, .LCPI0_0
        sub     r0, r11, #88
        mov     r2, #80
        bl      memcpy

        @ local_nonzero_small (手动 load 进去)
        ldr     r1, .LCPI0_1
        add     r2, sp, #92
        ldm     r1, {r3, r4, r5, r12, lr}
        stm     r2, {r3, r4, r5, r12, lr}
        add     r1, sp, #52
        mov     r2, #0
        mov     r3, #40
        str     r0, [sp, #8]                    @ 4-byte Spill

        @ local_zero (调用 memset)
        mov     r0, r1
        mov     r1, r2
        str     r2, [sp, #4]                    @ 4-byte Spill
        mov     r2, r3
        bl      memset

        @ local_uninitited
        @ 它运行时不需要做任何事
        @ 只要在开栈帧的时候开大点就可以了

        @ 函数返回
        ldr     r1, [sp, #4]                    @ 4-byte Reload
        str     r0, [sp]                        @ 4-byte Spill
        mov     r0, r1
        sub     sp, r11, #8
        pop     {r4, r5, r11, lr}
        bx      lr

@ 数据区
.LCPI0_0:
        .long   .L__const.main.local_nonzero
.LCPI0_1:
        .long   .L__const.main.local_nonzero_small
global_nonzero:
        .long   1                               @ 0x1
        .long   2                               @ 0x2
        .long   3                               @ 0x3
        .long   4                               @ 0x4
        .long   5                               @ 0x5
        .long   6                               @ 0x6
        .long   7                               @ 0x7
        .long   8                               @ 0x8
        .long   9                               @ 0x9
        .long   10                              @ 0xa

global_zero:
        .zero   40

.L__const.main.local_nonzero:
        .long   1                               @ 0x1
        .long   2                               @ 0x2
        .long   3                               @ 0x3
        .long   4                               @ 0x4
        .long   5                               @ 0x5
        .long   6                               @ 0x6
        .long   7                               @ 0x7
        .long   8                               @ 0x8
        .long   9                               @ 0x9
        .long   10                              @ 0xa
        .zero   40

.L__const.main.local_nonzero_small:
        .long   1                               @ 0x1
        .long   2                               @ 0x2
        .long   3                               @ 0x3
        .long   4                               @ 0x4
        .long   0                               @ 0x0
```

可见大部分情况下, 对数组初始化都是需要调用标准库的函数的. 按常理来讲, 这种情况下, 应该在 IR 里生成一条 `call` 指令, 调用 对应的函数 (`memset`/`memcpy`). 但问题是这些函数的签名需要 IR 能支持 C 中的 `void*` 类型 (在 LLVM IR 里, 它对应 `i8*`), 而为了这一两个函数在 IR 中加入新的类型过于麻烦, 而且直接生成 `call` 也并不能很好地表示初始化的语义, 所以往 IR 中添加了这一条特殊的指令.

## use-def 关系

> 本节内容与 LLVM IR 一致

IR 里的被使用者(Usee)是 `Value` 类, 任何一个 `Value` 类的实例都有可能被被人 "使用", 所以 `Value` 类提供了方法 `getUserList()` 来获得使用了这个实例的所有使用者. 使用者 (User) 是类 `User` 的实例, 任何一个 `User` 都有可能 "使用" 其他 `Value` 实例, 被使用者使用的 Value 称之为该使用者的 Operand (参数), 可以通过方法 `getOperands()` 获得一个使用者的所有使用的实例.

一个 User(Value) 使用的其他 Value 称之为这个 Value 的 operand, 而使用这个 Value 的 User 称之为这个 Value 的 user.

```java
    User a;

    a.getOperands()  //==>  获得这个 User 使用的所有 Value (即它的参数)
    a.getUserList()     //==>  获得使用这个 Value 的所有 User (即它被谁当作了参数)
```

## 反向引用

### IList 与 INode

`IList` 的 `I` 代表 `intrusive`, 是侵入式链表的缩写. 所谓侵入式链表, 就是在这个链表里的元素本身必须要作出修改才能成为这个链表里的元素. 在实现上, 这个修改就是得把 `INode`, 侵入式链表的节点, 作为该元素的一个成员.

实现该侵入式链表的原因有二:

1. Java 标准库中的 `ArrayList` 或是 `LinkedList` 对修改过于敏感 -- 一旦在迭代过程中, 外部 (有可能是循环里递归的其他函数) 对该链表作出修改的话, 很容易便会使得迭代器失效, 抛出异常, 即使这种修改理论上不会影响到该迭代器
2. 在编译器实现过程中有许多情况都会需要获取到某个元素的 "父母元素". 比如说有一个 `Insturtion` 对象, 想要获得它所在的 `BasicBlock` 对象. 如果对每个这样子的需求都手动保存一个成员的话过于繁琐

因此, 我们的目标是实现一个带反向引用的, 对修改不敏感的链表. 而为了实现反向引用, 必须使得链表中的元素得以知晓其在链表中的位置, 而这就意味着链表元素本身需要维护一些链表的相关信息, 而这只有侵入式链表能实现.

关于 `IList` 中的一些命名约定, 参见 [owner vs parent](#所有者-owner-与父对象-parent), [CO 后缀]().

`IList` 的使用举例如下:

```
       ┌───────────────────────────────────────────────┐
       │ BasicBlock(IListOwner)                        │
       │                                               │
       │                                               │
       │                                               │
       │                                               │
       │                                               │
       │                                               │
owner  │ ┌────────────────────┐                        │
┌──────┼─┤ ilist              │                        │
│      │ │                    │                        │
└──────► │                    │                        │
       │ │                    │                        │
       │ │                    │                        │
       │ └──┬──────▲─▲─▲──────┘                        │
       │    │      │ │ │                               │
       └────┼──────┼─┼─┼───────────────────────────────┘
       begin│      │ │ │
            │      │ │ │
            │      │ │ │
            │      │ │ └─────────────────────────────────────────────────────────────────────┐
 ┌──────────┘      │ │                                                                       │
 │                 │ └──────────────────────────────────┐                                    │
 │                 │                                    │                                    │
 │                 │                                    │                                    │
 │                 │                                    │                                    │
 │                 │                                    │                                    │
 │     ┌───────────┼──────────────┐         ┌───────────┼──────────────┐         ┌───────────┼──────────────┐
 │     │ Inst 1    │ (INodeOwner) │         │ Inst 2    │ (INodeOwner) │         │ Inst 3    │ (INodeOwner) │
 │     │           │              │         │           │              │         │           │              │
 │     │           │              │         │           │              │         │           │              │
 │     │           │              │         │           │              │         │           │              │
 │     │           │              │         │           │              │         │           │              │
 │     │           │              │         │           │              │         │           │              │
 │     │           │              │         │           │              │         │           │              │
 │     │           │              │         │           │              │         │           │              │
 │     │           │parent        │         │           │parent        │         │           │parent        │
 │     │           │              │         │           │              │         │           │              │
 │     │ ┌─────────┴─┐next        │         │ ┌─────────┴─┐next        │         │ ┌─────────┴─┐            │
 └─────┼─► inode     ├────────────┼─────────┴─► inode     ├────────────┼─────────┴─► inode     │            │
       │ │           │            │       prev│           │            │       prev│           │            │
       │ │           ◄────────────┼─────────┬─┤           ◄────────────┼─────────┬─┤           │            │
       │ └─┬─────────┘            │         │ └─┬─────────┘            │         │ └─┬─────────┘            │
       │   │value(owner)          │         │   │value(owner)          │         │   │value(owner)          │
       └───┼──▲───────────────────┘         └───┼──▲───────────────────┘         └───┼──▲───────────────────┘
           │  │                                 │  │                                 │  │
           └──┘                                 └──┘                                 └──┘
```

```java
// 这里使用的 a == b 确定是表示 a, b 是同一个对象
IListOwner<Instruction, BasicBlock> bb = new BasicBlock();
for (final var inode : bb.asINodeView()) {
    // use inode
    Log.ensure(inode.getOwner() == inode.getValue());
    Log.ensure(inode.getParent() == bb.getIList());

    // inode.insertBeforeCO(other_inode)
    // inode.insertAfterCO(other_inode)
}

for (final var inst : bb) {
    // use inst
    Log.ensure(inst instanceof INodeOwner);
    Log.ensure(inst.getParent() == bb);

    // inst.insertBeforeCO(other_inst);
    // inst.insertAfterCO(other_inst);
}
```

### `CO` 方法 (一致性方法)

正如上面提到的, IR 中大量存在相互引用与反向引用. 这意味着对于绝大部分修改性方法而言, 它们都需要特别注意一个问题: "我修改自己的时候, 需不需要维护对面的反向引用?".

为了便于区分 "会同时维护反向引用的一致性" 的方法与 "我只改自己别人的我不管" 的方法, 我们约定在前者的名称后面加上后缀 `CO`. 凡是标记了 `CO` 的修改性方法都 **决不能使得 IR 变得无效**. 为了确保这一点, 推荐标注 `CO` 的方法里多使用其他 `CO` 方法, 并且审慎地使用不带 `CO` 的方法.

`CO` 意为 `Consistent`, 表明该方法是 "一致" 的.

举个例子, 考虑双向链表节点 `INode` (删去了大量无关代码以专注于该问题, 并非真实实现):

```java
public class INode<E, P> {
    // 这是非 CO 方法, 简单, 直接, 只干基本的事
    public void setNext(INode<E, P> next) {
        this.next = Optional.ofNullable(next);
    }

    public void setPrev(INode<E, P> prev) {
        this.prev = Optional.ofNullable(prev);
    }

    // 这是带 CO 的方法, 要前前后后维护一堆关系, 小心谨慎地确保修改前后一切状态都正常
    /**
     * 在 this 的前面插入新节点
     * prev <-> (newPrev) <-> this <-> next
     * @param newPrev
     */
    public void insertBeforeCO(INode<E, P> newPrev) {
        final var oldPrev = prev;

        // 如果当前节点是链表的头节点, 那么当往前插入时, 还要修改链表的头节点
        oldPrev.ifPresentOrElse(
            n -> n.setNext(newPrev),
            () -> parent.ifPresent(p -> p.setBegin(newPrev)));
        newPrev.setPrev(oldPrev.orElse(null));

        newPrev.setNext(this);
        this.setPrev(newPrev);

        // 更新链表的大小
        parent.ifPresent(l -> l.adjustSize(+1));
    }

    private Optional<INode<E, P>> prev;     // 前一个节点
    private Optional<INode<E, P>> next;     // 下一个节点
    private Optional<IList<E, P>> parent;   // 包含该节点的链表
}

```

任何类的构造函数必须是一致的.

## 术语约定

### 名字 (name) 与表示 (representation)

内部表示的名字 (包括 IR 的人类可读输出格式) 均称为 `repr` (取 Representation 之意). 从源代码中来的名字, 或未经修饰的名字, 统一称为 `name`. 一般而言, `repr` = `修饰前缀` + `name`.

对象的 `getName` 一律返回 `name`; 对象的 `toString` 一律返回 `repr`.

### 类型 (type) 与类别 (kind)

凡是与源语言语义有关的, 比如表示一段二进制串的可用操作, 或者是 IR 里的值的种类的, 称之为类型 (Type). 凡是用于代码实现里用来做 RTTI, 或者标志同一个类的实例的种类的, 称之为类别 (kind).

比如一条 IR 指令有类型也有类别. 其类型 (`inst.getType()`) 就是指其 IR 里返回值的类型, 比如 `INT`, `FLOAT`, `BOOL`, `[4 * INT]`. 其类别 (`Inst.getKind()`) 就是它具体是哪一种 IR, 比如 `IAdd`, `ISub`, `FCmpGt`.

### 所有者 (owner) 与父对象 (parent)

称类 `A` 为类 `B` 的所有者 (owner), 若 A 中包含一个 `B` 的实例. 称类 `P` 为类 `B` 的父对象 (parent), 若 P 中包含一个 `IList<B>` (即多个 `B` 对象). 更一般地, 所有者一般只是指有作为类成员的关系, 而父对象一般不但有实现上的包含, 还有概念上的包含.

## 笔记: Alloc, Load, Store, GEP

Alloc 是用来获得一块特定大小的内存的 (通过提供特定的类型, 分配该类型大小的一块内存, 其返回类型永远是一个指向该特定类型的指针).

Load 解引用某个指针获得其值 (可视为去掉 `*`), Store 将某个值存放到指针所指的区域 (可视为加上 `*`).

GEP (get element pointer) 可以去掉嵌套的指针与数组类型, 将高维的指针偏移为某个基本类型的低维指针. 返回值就是该指针.

所以, 一般而言, 对数组的访问的 ir 一般是长这样的:

```
%arr = alloc [4 x [5 x i32]]      # %0 的类型是 [4 x [5 x i32]]*

# 第一个 0 表示 "将 %arr 偏移 0 个基类型([4 x [5 x i32]]), 获得了一个 [4 x [5 x i32]]*"
# 第二个 1 表示 "将上一步得到的指针偏移 1 个基类型([5 x i32]), 获得了一个 [5 x i32]*"
# 第三个 3 表示 "将上一步得到的指针偏移 3 个基类型(i32), 获得了一个 i32*"
%target = gep %arr, (0, 1, 3)

store %target 233   # 将 233 存进去
```

```c
int a[4][5];
a[1][3] = 233;
```

翻译为汇编的时候, 行优先情况下按字节的偏移量可以这样计算:

```
offset = 0 * sizeof([4 x [5 x i32]]) + 1 * sizeof([5 x i32]) + 3 * sizeof(i32)
       = 0 * 80 + 1 * 20 + 3 * 4
       = 32
```

## IR 合法性验证

在 Value 类中加入一个 `public void verify()` 方法. 如果有违反会抛出 (新的) `IRVerifyException` 异常.

之所以不默认在构造函数里检查, 是因为有时候 IR 在刚被构建, 还没离开前端的时候, 有一些约束是不满足的.

做**最严格**的 IR 合法性验证.

- 基本验证
    - [x] 每一个 Value 都要有 name
    - [x] name 的长度至少为 2 (因为要包括一个前缀 `@` 或 `%`, 然后剩下的名字长度至少为 1)
    - ~~指针类型的基本类型只能是数组类型或 Int 或 Float (换言之, IR 中不应该存在多重指针/布尔指针)~~
      - 对于全局数组变量而言, 因为数组类型退化后是其元素类型的指针, 而作为全局变量又得自带一个指针, 所以这种情况下可能出现多重指针
    - [x] 数组类型的元素类型只能是数组类型或 Int 或 Float (换言之, IR 中不应该存在指针的数组/布尔数组)
    - [x] use-def 关系:
        - 如果你在我的 operandList 里, 那我一定要在你的 userList 里; 反之亦然.
    - [x] IList 所有关系:
        - IList 必然有 owner
        - IList 的节点的 parent 必须都是自己
        - INode 必须要有 value/owner
        - INode 必须是其 parent 的节点
            - 如果 INode 无 parent, 发出警告
        - INode 的前后关系
            - 如果 prev 非空, 那 prev 的 next 必须是自己, 否则它必须是 IList 的开头 (或者不在 IList 中)
            - 如果 next 非空, 那 next 的 prev 必须是自己, 否则它必须是 IList 的末尾 (或者不在 IList 中)
- 函数验证
    - [x] 名字必须以 `@` 开头
    - ~~一定要有 parent (module)~~ 函数似乎没有 Parent, 可能需要进一步思考要不要加
    - ~~必须有且只有一个 entry 块 (命名为 `<函数名>_entry` 的块)~~
        - 现在直接将 "entry 块" 定义为函数基本块列表的第一个块
        - 因为优化的原因, 是存在一开始加入的那个基本块被删除的情况的, 所以函数里完全可能没有叫 `entry` 的块
    - [x] Parameter 信息与自己的 IRType 信息吻合 (数量一致, 类型相同)
    - [x] 基本块的 labelName 互不相同
    - 返回值与自己的类型吻合 (这个在 Ret 指令的检查里做)
    - 你是我的后继那我必须是你的前继, 反之亦然 (这个在 Ret 指令的检查里做)
- 基本块验证
    - [x] 名字必须以 `%` 开头
    - [x] 一定要有 parent (function)
    - [x] 必须有且只有一条 Terminator 指令 (Br/BrCond/Ret), 并且只能在 IList 的末尾
    - [x] Phi 指令在且仅在开头, 并且 phiEnd 指向第一条非 Phi 指令
    - [x] 前继要互不相同
- 指令验证
    - 通用指令验证
        - [x] 名字必须以 `%` 开头
        - [x] 一定要有 parent (basic block)
        - [x] 所有指令不能成自环; 即 operandList 与 userList 不能是自己
        - [x] 类型为 Void 的指令不可以有 user
        - 类型不能是 BasicBlock/Parameter (在各个具体指令的类型检查里查了)
    - 比较与运算类指令验证 (Binray/Unary/IntToFloat/FloatToInt/Cmp)
        - [x] 类型检查:
            - 自己的类型与操作数的类型匹配
            - 自己的类型与 InstKind 匹配
        - [x] 确保操作数不全部都是常量 (这种情况应该被直接折叠)
        - [x] 对整数除法指令确保不直接除以常量 0 (间接/运行时的那我也没办法)
    - 跳转类指令 (Br/BrCond/Ret)
        - 必须都是 Void 类型
        - [x] 跳转的对象不是自己 (TODO: 需要思考)
        - [x] return 语句与函数类型信息吻合
        - [x] 自己必须在跳转目标的前继列表中
        - [x] 条件跳转的条件不能是常量 (否则就被折叠了)
        - [x] 条件跳转的两个目标基本块不能相同 (否则就被折叠了)
    - Phi 指令:
        - [x] Phi 的 incoming 必须与当前块的前继数量相同
        - phi 不可以自己指向自己 (这个在通用指令验证里做了)
        - phi 的 `isIncompleted()` 必须返回 false
    - 内存相关指令 (Alloc/Load/Store/GEP/MemInit)
        - Alloc 指令
            - 类型必须为指针类型
            - [x] 参数必然是数组类型, 因为源语言里没有 "变量的指针", 也不需要动态分配变量
        - Load 指令
            - [x] 类型必须为 Int/Float (因为源语言里没有布尔类型, 并且也不会把数组的一部分数组拿出来玩)
            - [x] 参数必然为指向基本类型的指针类型 (任何对数组具体位置的引用都要通过 GEP 算)
        - Store 指令
            - 类型必须为 Void
            - [x] 参数必须为指向基本类型的指针类型
        - GEP 指令
            - [x] 最后必须是一个指向基本类型的指针类型
            - indices 的数量必须和 ptr 的类型吻合 (满足上一条自动满足这一条)
        - MemInit 指令
            - 必须是 Void 类型
            - [x] 绑定的 ArrayConst 的类型必须与 ptrArr 的 baseType 吻合
    - Call 指令
        - [x] 自己的类型必须与被调用函数的返回类型相同
        - [x] 自己的参数的数量与类型必须与被调用函数的形参数量与类型相同
- 常量验证
    - [x] INT_0 和 FLOAT_0 只能有一个
    - ArrayConst 常量的元素数量必须和类型匹配 (它就是这么实现的)
- 全局变量验证 (GlobalVar)
    - [x] 名字必须以 `@` 开头
    - 必须都是指针类型 (它就是这么实现的)


## 笔记: Phi

定义:

- (基本块 B 的) 前继: 能通过 Br/BrCond 指令直接跳转到 B 的基本块
- (基本块 B 的) 后继: 作为 B 中存在的 Br/BrCond 的参数的基本块

插入 phi 指令的根本原因是要在静态单赋值的 IR 中表达源语言里的 "值会随着控制流不同而变化的变量" 的概念. 它相当于在严格的 SSA 里给这种概念开一个小洞, 使得 SSA 得以表达这种变量的概念. 于是自然每一个 Phi 都有与其所在基本块的前继的数量相同的 incoming info. 它指示了当控制流从不同前继到达此基本块时, phi 在运行时的值应该取什么.

值得注意的是, incomingValue 所在的基本块不一定就是其对应的前继. 

例子: 考虑下面的 C 语言代码:

```c
int a = input();
int b = input();
int c = input();

if (b == 0 && c == 0) {
    a = input();
}

output(a);
```

其会被翻译为:

```llvm
entry:
    %a_0 = call %input();
    %b_0 = call %input();
    %c_0 = call %input();
    br %cond_1;

cond_1:
    %cmp_1 = icmpeq %b_0, 0;
    br %cmp_1 %cond_2 %exit;
cond_2:
    %cmp_2 = icmpeq %c_0, 0;
    br %cmp_2 %then %exit;
    
then:
    %a_1 = call %input();
    br %exit
    
exit:
    %a_2 = phi [%a_0 %cond_1], [%a_0 %cond_2], [%a_1 %then];
    call %output(%a_0);
    halt;
```

对 `exit` 块, 它有三个前继, 分别为 `cond_1`, `cond_2`, `then`; 于是在翻译源代码中 if 后面对 `a` 的使用时, 就得在 IR 中插入一条 phi 指令. 这条 phi 指令的含义便是 "当控制流从 `cond_1`" 来的时候, 我的值就是 `a_0` 的值; 从 `cond_2` 来的时候, 我的值就是 `a_0` 的值; 从 `then` 来的时候, 我的值就是 `a_1` 的值".

这个例子也可以佐证 "incomingValue 所在的基本块不一定就是其对应的前继" 这句话. 因为 `a_0` 指令实际上是 `entry` 块中的指令, 但它在 `a_2` 这条 phi 指令中对应的 incoming block 却是 `cond_1`. 

从这个例子我们还可以知道, 不同 incoming block 还可能具有相同的 incoming value.