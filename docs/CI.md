# CI

## CI 的总体架构

CI 采用 drone 进行构建。

- [.drone.yml](../.drone.yml) : CI 配置文件，定义了 CI 的触发条件和运行步骤。
- [Dockerfile](../CI/Dockerfile) && [build.sh](../CI/build.sh) : 用于 CI 的构建容器与构建脚本。
- [test.sh](../CI/test.sh) : 位于树莓派上的测试脚本。

## 触发条件

```yaml
trigger:
  branch:
    - main
    - test/*
```
在 `main` 分支以及以 `test/` 开头的分支上的提交操作都会触发 CI，且在 `clone` 步骤会切换至触发分支。

## 使用 CI 进行测试

若需要对当前工作运行 CI 进行测试，可如下操作：

```shell
git checkout -b test/foo
git commit --allow-empty -m "Empty Commit to start CI"
```

## CI 工作流

1. 下载依赖并构建项目，编译测试用例并连同测试文件打包。
2. 将打包好的测试文件发送至树莓派。
3. 运行测试文件，测试返回值并比对输出信息。
