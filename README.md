# SSYC: Simple SysY Compiler

## 开发环境简介

本项目推荐使用 VSCode 开发. 若您使用 VSCode, 请遵循提示安装 `extensions.json` 内推荐的插件.

本项目没有使用任何标准的 Java 依赖管理/构建 工具, 而是使用 Bash 脚本完成构建. Bash 脚本具有简单、透明的优势, 适合本项目这种依赖少而简单, 无复杂构建需求的项目. 并且使用 Bash 脚本便于集成各式各样的工作流, 而不会被构建工具限制.

本项目之功能性测试使用 Docker 容器进行. 整个源码目录被挂载进容器之后在容器内被编译为 class 文件并执行, 依次读取测试数据文件夹 (当前为 `test-data/asm-handmade`) 内的 `*.sy` 文件并输出汇编文件 `*.s`, 随后在容器内被交叉编译器汇编为 ARM 可执行文件, 随后使用 qemu 执行并输出 `main` 返回值. 随着开发的进行将逐步修改测试行为为判断输出是否正确.

## 开发环境使用

主要的构建命令在 [`m`](m) 中定义, 具体为:

```bash
./m install      # 于 Maven 下载生成前端所需的 ANTLR4 可执行文件与运行时依赖
./m clean        # 清理构建结果
./m build        # 将 java 文件构建为 class 文件
./m run [args]   # 执行构建出的 class 文件, args 将被传入 java 程序中
./m jar          # 将 class 文件打包为 jar
./m jar-run      # 执行打包后的 jar
./m build_test   # 构建测试用的 Docker 容器的镜像
./m test         # 执行测试
./m full [args]  # 一并清理, 构建与运行程序
```

若您初次 clone 本项目, 请先执行下面的命令下载依赖:

```bash
./m install
```

随后您可以使用下面的命令构建并运行程序, `[args]` 的取值详见[下文](#程序本体参数说明):

```bash
./m full [args]
```

> 若要使补全正常工作, 则您必须至少运行一次 `./m build` 以生成 `SysYLexer.java` 等前端代码.

若您首次运行测试, 请先安装 Docker 并确认当前用户在 `docker` 组中, 随后运行下面的命令构建测试容器镜像:

```bash
./m build_test
```

镜像较大, 请稍作等待或尝试改善网络条件.

测试容器镜像构建完成后, 便可以执行下面的命令运行测试:

```
./m test
```

## 程序本体参数说明

本程序接收三个参数: `<target> <input_file> <output_file>`. 其中 `target` 参数为输出类型, 可取 `ast`, `ir`, `asm` 三者之一. `input_file` 与 `output_file` 分别为输出与输出文件名, 可以使用 `-` 来令程序使用标准输入/输出.

例子:

```bash
# 从标准输入读取, 将编译后的汇编 (asm) 输出至标准输出
./m run asm - -

# 读取 xxx.sy 文件作为输入, 将解析好的 AST (ast) 以 Lisp 格式输出到标准输出
./m run ast xxx.sy -

# 读取 xxx.sy 文件作为输入, 将生成的中间表示输出到 yyy.ir 文件
./m run xxx.sy yyy.ir
```

## 测试结果说明

当前程序的测试主要以目测编译后程序的返回值为主. 一个输出的例子为:

```
$ ./m test
Test test-data/asm-handmade/0-number.sy       return 1
Test test-data/asm-handmade/1-add-sub.sy      return 1
Test test-data/asm-handmade/2-mul-div-mod.sy  return 1
Test test-data/asm-handmade/3-variable.sy     return 1
Test test-data/asm-handmade/4-if.sy           return 1
Test test-data/asm-handmade/5-while.sy        return 1
```

此即为所有代码均编译错误, 因为在 `asm-handmade` 文件夹内的程序均期望返回 0. 自动判断与比对输出将在日后支持.

## 更多文档

对于代码本身的高层次说明详见 [docs](docs/) 文件夹内的文档. 对于代码细节的说明详见代码注释.

- 时间规划: <docs/deadline.md>
- 代码风格: <docs/style.md>
- IR 设计: <docs/ir.md>
-