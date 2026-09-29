# FindMeExtended

FindMeExtended 是一个面向 Minecraft 1.20.1、基于 Architectury 的模组，用于搜索附近容器中的物品，并快速将物品取出。

## 项目来源

本项目 fork 自 [Buuz135/FindMe](https://github.com/Buuz135/FindMe)。原项目许可证保持不变，详见 [LICENSE](LICENSE)。

## 主要改动

- 项目更名为 FindMeExtended，版本为 1.0.2，作者为 LAI。
- Java 包名改为 `com.lai.findmeextended`，并同步更新模组 ID、资源命名和构建产物名称，减少与原项目的标识冲突。
- 保留原有附近容器查找和取出功能。
- 取出物品新增“填满背包”：按住 Alt 再按「取出一组」，会尽可能把该物品取满背包（详见下文）。
- Forge 端增加 AE2 容器支持，包括 ME Drive、ME Chest，以及通过 AE2 `MEStorage` 能力提供存储的第三方容器。
- 保留 Fabric 与 Forge 双平台结构，并同步更新平台元数据和 Mixin 配置。
- 新增容器黑名单：列入黑名单的容器不会被查找、也不会被取出，其他模组（例如配方材料发送器）可以读取同一份黑名单。

## 容器黑名单

手持「黑名单工具」（默认木剑，可在 `config/findmeextended.json` 的 `COMMON.BLACKLIST_TOOLS` 里改成任意物品 ID，可填多个）时：

- 视距内的黑名单容器持续高亮：每个方块一圈描边（`CLIENT.BLACKLIST_HIGHLIGHT_COLOR`，默认亮红 `#FF3B30`），每帧都画、不闪烁，关闭深度测试所以被墙挡住也能看见。
- 潜行 + 右键点击容器：加入黑名单。
- 潜行 + 左键点击容器：移出黑名单（此时左键不会挖方块；目标不在黑名单里时左键照常挖）。

其他说明：

- 高亮用的是原版方块选中框那套描边（`LevelRenderer.renderLineBox` + `RenderType.lines()`），不是粒子：粒子小、会淡出，只能靠定期补发看起来连续，补发之间还会明暗跳变。
- 黑名单只认坐标、与方块实体无关：拆掉容器再在同一坐标放一个新的（换种类、换朝向都行）仍然算黑名单。
- 坐标上已经没有容器（方块实体不存在，或者已经不是容器）时，会在下次手持工具高亮时自动清掉该条，避免无效坐标长期积累。清理只遍历黑名单本身、不扫描区块。
- 双箱子的两半共用同一份库存，黑名单统一记坐标较小的那一半；两半都点一遍也只是同一条记录，第二次会提示“已在该坐标的黑名单中”。
- 黑名单按维度存在存档里（`world/data/findmeextended_blacklist.dat`），同一世界、同一维度的所有玩家共用一份。客户端与服务端的 `BLACKLIST_TOOLS` 配置应保持一致（服务端以自己那份为准）。
- 旧版本生成的 `config/findmeextended.json` 不会有新字段，删掉让它重新生成、或手动加上 `BLACKLIST_TOOLS` / `BLACKLIST_HIGHLIGHT_COLOR` 即可。
- 注意：AE2 的“整个网络的聚合视图”无法按单个容器排除——存储总线把某个容器挂进网络后，物品是通过网络视图读到的，把这个存储方块列入黑名单不会让它的物品从网络视图里消失。

## 取出物品

- 悬停物品后按「取出1个」/「取出一组」：从附近容器取回该物品。
- 按住 Alt 再按「取出一组」：尽可能取满背包。服务端按背包剩余空间（主背包 36 格，与交付路径一致）截断请求量，因此**不会**因为背包放不下而把物品丢在地上；附近存量不够时就只取到能取的数量，并播放原有的“没取够”音效。
- 普通「取出一组」同样受剩余空间限制：背包快满时只会取走放得下的数量，不再溢出到地上。

## 供其他模组使用的接口

`com.lai.findmeextended.api.FindMeBlacklistApi` 是稳定的公开契约，可被反射调用（配方材料发送器就是这样读取黑名单、让反转搜索共用同一份数据的）：

- `Set<BlockPos> blacklistedPositions(ServerLevel level)`：该维度黑名单坐标的只读实时视图。
- `boolean isBlacklisted(ServerLevel level, BlockPos pos)`：单个坐标是否在黑名单里。

## 原仓库

https://github.com/Buuz135/FindMe

## 构建环境

- Minecraft 1.20.1
- Architectury 9.1.12
- Fabric Loader 0.14.21
- Forge 47.1.0
- Java 17 编译目标
