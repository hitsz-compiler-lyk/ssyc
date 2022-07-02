# 风格约定

## 代码风格约定

### 鼓励使用新特性

比赛使用 Java 15, 在不超过此版本的基础上鼓励使用新特性, 包括但不限于:

- 使用 `var` 以在变量声明中省略类型
- 使用 `Stream` 与 `Optional`
- 使用 lambda 与 `::`
- 使用 `switch` 表达式
- 在 interface 中使用 default 方法

若需要新特性一览, 可参考 [Java Guide](https://github.com/forax/java-guide).

Use package visibility instead of protected.

### 可空变量一律使用 `Optional<T>`

> 尽管传统上认为将 Optional 作为成员储存是不合适的, 但实际应用中这样做很方便, 所以本项目允许 (并鼓励) 对可空成员使用 Optional.

如果一个变量不使用 Optional, 则总认为其是非空的并忽略 **任何** 形式的空指针检查 (包括 assert).

例外: 在有注释说明的情况下, 构造函数参数为了方便可以直接接收空引用, 但对应的成员的存储与获取必须使用 Optional.

如果真的需要检查一个变量是否为空, 可以使用 `Objects.requireNonNull(obj)`, 或者直接 `assert obj != null`.

```java
class UserInfo {
    // Usage of Objects.requireNonNull
    public UserInfo(String name, int age, String login, char[] password) {
        this.name = Objects.requireNonNull(name);
        this.age = age;
        this.login = Objects.requireNonNull(login);
        this.password = password.clone();
    }
}

record User(String name, int age) {
  public User {
    Objects.requireNonNull(name);
  }
  // the compiler automatically adds equals/hashCode/toString !
}
```

### 鼓励使用 assert 判断

除上提到的非空判断之外, 鼓励在代码中使用 assert 进行 pre-check 与 post-check.

### 拒绝异常标注

新创立的异常建议完全从 `RuntimeException` 派生, 除非有非常充分的理由.

对于 "代码的错误使用方式" (比如 get 一个成员但是它目前为 null), 使用断言而非异常. 只有对于 "输入数据中的错误" 才使用异常.

### 异常的使用方法

TODO

```
// Exceptions commonly used in Java
// - NullPointerException if a reference is null
// - IllegalArgumentException if an argument of a method is not valid
// - IllegalStateException if the object state doesn't allow to proceed,
//   by example if a file is closed, you can not read it
// - AssertionError if a code that should not be reached has been reached
```

如果把不定主意, 则优先选择 `RuntimeException`. 宁可不做选择, 也不做错误的选择.

## 命名约定

对于英文中的同近意词, 若在代码中含义不同, 请于每个模块的文档中指明. 通用的情况可以于本文档说明.

### `setXXX`

推荐只将简单的 setter 类修改方法命名为 `setXXX`, 如果该修改方法比较复杂 (包含维护反向引用之类的情况), 可以使用其他表示修改的英文动词 (譬如 `change`, `switch`), 并且注意视情况添加 [`CO` 后缀](./ir.md#co-方法-一致性方法).


## 文档风格约定

理论上每一个子包都需要在 `docs` 文件夹下有其对应的文档. 文档中应说明:

1. 子包的顶层结构与设计, 比如类关系
2. 实现过程中的参考, 比如参考的哪个库的哪一部分, 学习过程中看了哪些书/资料
3. 否决/弃用了哪些设计及其理由

方法等接口 API 建议在代码中使用 Javadoc.