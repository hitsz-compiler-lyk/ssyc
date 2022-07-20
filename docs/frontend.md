## LVal 的翻译

在语法中, `LVal` 是一个如下的成分:

```

```

它可以出现在很多地方, 用作不同的用途. 更具体地说, 它可以有三个用法: 

1. 用作定义/声明: 如出现在函数形参/数组定义中
2. 用作左值: 如出现在赋值语句左边
3. 用作右值: 如出现在表达式中

而根据前端实现里对不同种类的量的实现, 我们可以总结出下表:

|类型|定义|左值|右值|
|-|-|-|-|
|局部变量|获得它对应的 Variable|versionInfo.kill|versionInfo.getDef|
|局部数组|获得它对应的 Variable 与 Shape|GEP, Store|GEP, Load|
|全局变量|获得它对应的 Variable|Store|Load|
|全局数组|获得它对应的 Variable 与 Shape|Load, GEP, Store|Load, GEP, Load|
|函数变量形参|获得它对应的 Variable|versionInfo.kill|versionInfo.getDef|
|函数数组形参|获得它对应的 Variable 与 Shape|GEP, Store|GEP, Load|

其中在定义阶段, 在获取到其 Variable 与 Shape 之后, 又根据定义对象的不同有不同的处理方法. 对于变量而言, 定义有六种情况:

|      |   全局|       局部|
|-|-|-|
|常量  |全局常量|  局部常量|
|变量  |全局变量|  局部变量|
|数组  |全局数组|  局部数组|

它们总是需要将 identifier 与 variable 的对应关系加入到作用域中, 也总是需要更新 variable 与其当前的定义的关系 (不过是要在不同的表中, 有的情况是 finalInfo, 有的情况是 versionInfo)

- 全局常量: 更新 scope, 加入 finalInfo, 加入 globalConst
- 局部常量: 更新 scope, 加入 finalInfo
- 全局变量: 更新 scope, (以指针形式) 加入 finalInfo, 加入 globalVariable (绑定0/初始值)
- 局部变量: 更新 scope, 加入 versionInfo (绑定0/初始值)
- 全局数组: 更新 scope, 加入 finalInfo, 加入 globalVariable (绑定0/初始值)
    - 有非空初始值的情况下, 初始值加入 globalConst
- 局部数组: 更新 scope, 加入 finalInfo
    - 有非空初始值的情况下, 插入 MemInit, 初始值加入 globalConst

对于函数形参而言, 它有两种情况, 不过都跟局部变量/数组定义大同小异:

- 函数变量形参: 构建 Parameter
- 函数数组形参: 构建 Parameter

于是, 为了处理上面这些多变的例子, 我们定义如下几个函数:

- `visitLVal`: 将 LVal 从语法树形式解析出来, 获得它的类型, 对应的 Variable 和各个中括号中的值
- `findVariable`: 将 Variable 对应到当前其对应的定义
- `getRight`: 根据其当前的定义与索引, 获得可以作为右值的值
- `getLeft`: 根据其当前的定义与索引, 以及将要替代它的新定义, 替换其值/定义

## 数组翻译举例

考虑下面的样例:

```c
// 全局量的定义
int global_var_int = 1;
const int global_var_int_const = 2;

float global_var_float = 0.3f;
float global_var_float_const = 0.4f;

int global_array_int[2][3] = {{1, 2, 3}, {4, 5, 6}};
const int global_array_int_const[2][3] = {{1, 2, 3}, {4, 5, 6}};

float global_array_float[2][3] = {{1.0f, 2.0f, 3.0f}, {4.0f, 5.0f, 6.0f}};
const float global_array_float_const[2][3] = {{1.0f, 2.0f, 3.0f}, {4.0f, 5.0f, 6.0f}};

// 函数传参
void func(int a[][3], int b[2][3], int c) {
    return a[0][1]                  // 函数内使用数组形参 (省略第一维)
         + b[0][2]                  // 函数内使用数组形参 (包含第一维)
         + c                        // 函数内使用变量形参
         + global_array_int[0][0];  // 函数内使用全局数组
}

int main() {
    // 局部量的定义
    int local_var_int = 10;
    const int local_var_int_const = 11;

    float local_var_float = 12.0f;
    const float local_var_float_const = 13.0f;

    int local_array_int[2][3] = {{10, 20, 30}, {40, 50, 60}};
    const int local_array_int_const[2][3] = {{10, 20, 30}, {40, 50, 60}};

    float local_array_float[2][3] = {{10.0f, 20.0f, 30.0f}, {40.0f, 50.0f, 60.0f}};
    const float local_array_float_const[2][3] = {{10.0f, 20.0f, 30.0f}, {40.0f, 50.0f, 60.0f}};

    int local_array_with_runtime_init[2][3] = {
        {local_var_int, local_var_int_const, local_array_int[0][1]},
        {70, 80, 90}
    };


    // 局部量的使用
    putint(local_var_int);
    putint(local_var_int_const);

    putfloat(local_var_float);
    putfloat(local_var_float_const);

    putint(local_array_int[0][1]);
    putint(local_array_int_const[0][1]);

    putfloat(local_array_float[0][1]);
    putfloat(local_array_float_const[0][1]);


    // 全局量的使用
    putint(global_var_int);
    putint(global_var_int_const);

    putint(global_var_float);
    putint(global_var_float_const);

    putint(global_array_int[0][1]);
    putint(global_array_int_const[0][1]);

    putfloat(global_array_float[0][1]);
    putfloat(global_array_float_const[0][1]);


    // 函数传参
    func(local_array_int, global_array_int, local_int_var);

    return 0;
}

```

它将会被翻译为如下的 IR 形式:

```cpp
Module xxx:
    arrayConsts:
        @global_array_int$init                  (ArrayConst)
        @global_array_int_const$init            (ArrayConst)
        @global_array_float$init                (ArrayConst)
        @global_array_float_const$init          (ArrayConst)
        @local_array_int$init                   (ArrayConst)
        @local_array_int_const$init             (ArrayConst)
        @local_array_float$init                 (ArrayConst)
        @local_array_float_const$init           (ArrayConst)

    globalVariables:
        @global_var_int                         (*int)
            init: 1                             (IntConst)

        // @global_var_int_const 不存在, 因为它是常量变量, 会被折叠

        @global_var_float                       (*float)
            init: 0.3f                          (FloatConst)

        // @global_var_float_const 不存在, 因为它是常量变量, 会被折叠

        @global_array_int                       (**[3 x int])
            init: @global_array_int$init        (ArrayConst)

        // @global_array_int_const 存在, 因为它虽然是常量, 但是是数组常量,
        // 数组常量不能保证在所有情况下都被折叠 (考虑使用变量作为索引的情况), 因此要保留
        @global_array_int_const                 (**[3 x int])
            init: @global_array_int_const$init  (ArrayConst)

        @global_array_float                     (**[3 x float])


    functions:
        func:

        main:


```

## Phi 的插入

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