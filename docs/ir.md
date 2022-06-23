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

### 枚举实现的 "子类关系"

为了减少无意义的重复代码, 很多操作上高度相同的东西会被放在一个类里实现, 通过一个特殊的枚举来标识不同的类别. 这种时候凡是细化类别的操作跟判断大抵上都会放在枚举中实现. 譬如二元运算指令 `BinaryOpInst` 就通过 `InstKind` 来区分加减乘除, 并且凡是用作 "判断这条指令是不是操作整数的" 这种细化方法, 都会放在 `InstKind` 里.

## use-def 关系

> 本节内容与 LLVM IR 一致

IR 里的被使用者(Usee)是 `Value` 类, 任何一个 `Value` 类的实例都有可能被被人 "使用", 所以 `Value` 类提供了方法 `getUserList()` 来获得使用了这个实例的所有使用者. 使用者 (User) 是类 `User` 的实例, 任何一个 `User` 都有可能 "使用" 其他 `Value` 实例, 被使用者使用的 Value 称之为该使用者的 Operand (参数), 可以通过方法 `getOperandList()` 获得一个使用者的所有使用的实例.

一个 User(Value) 使用的其他 Value 称之为这个 Value 的 operand, 而使用这个 Value 的 User 称之为这个 Value 的 user.

```java
    User a;

    a.getOperandList()  //==>  获得这个 User 使用的所有 Value (即它的参数)
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
    assert inode.getOwner() == inode.getValue();
    assert inode.getParent() == bb.getIList();

    // inode.insertBeforeCO(other_inode)
    // inode.insertAfterCO(other_inode)
}

for (final var inst : bb.asElementView()) {
    // use inst
    assert inst instanceof INodeOwner;
    assert inst.getParent() == bb;

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

## 笔记: SSA 构造

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
