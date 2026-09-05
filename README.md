# FindMeExtended

FindMeExtended 是一个面向 Minecraft 1.20.1、基于 Architectury 的模组，用于搜索附近容器中的物品，并快速将物品取出。

## 项目来源

本项目 fork 自 [Buuz135/FindMe](https://github.com/Buuz135/FindMe)。原项目许可证保持不变，详见 [LICENSE](LICENSE)。

## 主要改动

- 项目更名为 FindMeExtended，版本为 1.0.1，作者为 LAI。
- 更新 Java 包名、模组 ID、资源命名和构建产物名称，减少与原项目的标识冲突。
- 保留原有附近容器查找和取出功能。
- Forge 端增加 AE2 容器支持，包括 ME Drive、ME Chest，以及通过 AE2 `MEStorage` 能力提供存储的第三方容器。
- 保留 Fabric 与 Forge 双平台结构，并同步更新平台元数据和 Mixin 配置。

## 原仓库

https://github.com/Buuz135/FindMe

## 构建环境

- Minecraft 1.20.1
- Architectury 9.1.12
- Fabric Loader 0.14.21
- Forge 47.1.0
- Java 17 编译目标
