# SSYC: Simple SysY Compiler

本项目为 SysY 语言的优化编译器，支持armv7(armv7ve)的后端。

SysY
是[全国大学生计算机系统能力大赛编译系统设计赛2022](https://compiler.educg.net/#/oldDetail?name=2022全国大学生计算机系统能力大赛编译系统设计赛)
的比赛语言.

[SysY的语言规范、runtime 和 比赛测例](https://gitlab.eduxiji.net/nscscc/compiler2022/-/tree/master)

比赛结束后, 出于用作讲解/教授的示例的作用, 本项目可能还会继续开发. 您可以查看 `just-finish`
标签以获得我们刚刚完赛时的代码. `main` 分支的开发进度将被继续推进, 其中可能包含架构调整, 代码质量改进和文档完善.

比赛结束之后的代码将会调整至 Java 17 标准而非 2022 年比赛时使用的 Java 15, 请在参考代码时注意鉴别.

## 开发环境简介

本项目没有使用任何标准的 Java 依赖管理/构建 工具, 而是使用 Bash 脚本完成构建. Bash 脚本具有简单、透明的优势,
适合本项目这种依赖少而简单, 无复杂构建需求的项目. 并且使用 Bash 脚本便于集成各式各样的工作流, 而不会被构建工具限制.

本项目之功能性测试使用 Docker 容器进行. 整个源码目录被挂载进容器之后在容器内被编译为 class 文件并执行,
依次读取测试数据文件夹 (当前为 `test-data/asm-handmade`) 内的 `*.sy` 文件并输出汇编文件 `*.s`, 随后在容器内被交叉编译器汇编为
ARM 可执行文件, 随后使用 qemu 执行并输出 `main` 返回值. 用测试脚本判断输出是否正确.

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
./m test [args]  # 执行测试
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
./m test <data-subdir> <test-item>
```

其中 `data-subdir` 指要测试 `test-data` 目录下的哪个子目录的内容, `test-item` 表示测试项目, 预计有三个:

- `clang`: 使用 clang 编译并运行测试样例
- `clang_O2`: 使用 clang -O2 编译并运行测试样例
- `ssyc_llvm`: 使用我们的编译器编译出 llvm ir, 并使用 llvm-as/llc 转为汇编文件, 随后汇编并运行测试样例
- `ssyc_asm`: 使用我们的编译器编译出汇编文件, 随后汇编并运行测试样例
- `ssyc_asm_O2`: 使用我们的编译器编译出汇编文件, 并执行所有的优化, 随后汇编并运行测试样例
- `ssyc_llvm_long`: 使用我们的编译器编译出 llvm ir, 并使用 llvm-as/llc 转为汇编文件, 随后汇编并运行测试样例,
  在出现用例结果错误时不会停止
- `ssyc_asm_long`: 使用我们的编译器编译出汇编文件, 随后汇编并运行测试样例, 在出现用例结果错误时不会停止
- `ssyc_asm_O2_long`: 使用我们的编译器编译出汇编文件, 并执行所有的优化, 随后汇编并运行测试样例, 在出现用例结果错误时不会停止
- `generate_stdout`: 使用 clang -O2 编译并运行用例, 保存运行结果, 相当于构造标准答案
- `retest`: 跳过编译汇编直接运行测试样例

## 程序本体参数说明

本程序接收三个参数: `<target> <input_file> <output_file>`. 其中 `target` 参数为输出类型, 可取 `ast`, `llvm`, `asm`
三者之一. `input_file` 与 `output_file` 分别为输出与输出文件名, 可以使用 `-` 来令程序使用标准输入/输出.

此外还接受flag:

- `-On n >= 1`: 开启优化执行
- `-l --log`: 打印日志

例子:

```bash
# 从标准输入读取, 将编译后的汇编 (asm) 输出至标准输出
./m run asm - -

# 读取 xxx.sy 文件作为输入, 将解析好的 AST (ast) 以 Lisp 格式输出到标准输出
./m run ast xxx.sy -

# 读取 xxx.sy 文件作为输入, 将生成的中间表示输出到 yyy.ir 文件
./m run llvm xxx.sy yyy.ir
```

## 测试结果说明

当前程序的测试主要以目测编译后程序的返回值为主. 一个输出的例子为:

```
$ ./m test benchmark ssyc_asm_O2_long
[06:19:56] Finish: ANTLR Generation                                                                                                        test.py:65
[06:19:59] Finish: Java compile                                                                                                            test.py:68
================= begin: ssyc =================
           Finish (1/8) bitset.sy                                                                                                          test.py:53
[06:20:00] Finish (2/8) brainfuck.sy                                                                                                       test.py:53
[06:20:01] Finish (3/8) conv.sy                                                                                                            test.py:53
[06:20:05] Finish (4/8) if-combine.sy                                                                                                      test.py:53
[06:20:24] Finish (5/8) many-params.sy                                                                                                     test.py:53
[06:20:26] Finish (6/8) matmul.sy                                                                                                          test.py:53
[06:20:29] Finish (7/8) mm.sy                                                                                                              test.py:53
[06:20:30] Finish (8/8) sl.sy                                                                                                              test.py:53
================= begin: llc =================
================= begin: gcc-as =================
================= begin: run-for-long-time =================
[06:20:35] Pass bitset                                   : ['Timer@0056-0064: 0H-0M-0S-662936us\n', 'TOTAL: 0H-0M-0S-662936us\n']          test.py:265
[06:20:59] Pass brainfuck                                : ['Timer@0116-0118: 0H-0M-23S-837557us\n', 'TOTAL: 0H-0M-23S-837557us\n']        test.py:265
[06:21:04] Pass conv                                     : ['Timer@0109-0116: 0H-0M-4S-727848us\n', 'TOTAL: 0H-0M-4S-727848us\n']          test.py:265
[06:21:06] Pass if-combine                               : ['Timer@0324-0328: 0H-0M-2S-462073us\n', 'TOTAL: 0H-0M-2S-462073us\n']          test.py:265
[06:21:08] Pass many-params                              : ['Timer@0134-0148: 0H-0M-1S-990390us\n', 'TOTAL: 0H-0M-1S-990390us\n']          test.py:265
[06:21:10] Pass matmul                                   : ['Timer@0023-0092: 0H-0M-1S-491740us\n', 'TOTAL: 0H-0M-1S-491740us\n']          test.py:265
[06:21:11] Pass mm                                       : ['Timer@0065-0084: 0H-0M-1S-362589us\n', 'TOTAL: 0H-0M-1S-362589us\n']          test.py:265
[06:21:12] Pass sl                                       : ['Timer@0013-0053: 0H-0M-0S-310300us\n', 'TOTAL: 0H-0M-0S-310300us\n']          test.py:265
```

## Benchmark

| 样例          | ssyc         | clang        | clang O2     |
|-------------|--------------|:-------------|:-------------|
| bitset      | 0S-662936us  | 10S-2838us   | 3S-476461us  |
| brainfuck   | 23S-837557us | 37S-259962us | 54S-295113us |
| conv        | 4S-727848us  | 26S-570227us | 21S-565170us |
| if-combine  | 2S-462073us  | 29S-282222us | 7S-645206us  |
| many-params | 1S-990390us  | 10S-726892us | 2S-790948us  |
| matmul      | 1S-491740us  | 4S-848694us  | 1S-680792us  |
| mm          | 1S-362589us  | 5S-276924us  | 1S-891107us  |
| sl          | 0S-310300us  | 1S-286663us  | 1S-169987us  |

测试采用的是 qemu-arm version 5.2.0, 因此与真实情况有一定的差距

clang 版本为 11.0.1-2, 测试CPU为 M1 pro

## 更多文档

对于代码本身的高层次说明详见 [docs](docs/) 文件夹内的文档. 对于代码细节的说明详见代码注释.

- 代码风格: <docs/style.md>
- IR 设计: <docs/ir.md>
- IR 优化设计: <docs/ir-optimze.md>
- 后端设计: <docs/backend.md>