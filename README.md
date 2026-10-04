# FindMeExtended

FindMeExtended 是一个面向 Minecraft 1.20.1、基于 Architectury 的模组，用于搜索附近容器中的物品，并快速将物品取出。

## 功能

- 搜索附近容器中的物品并取出：悬停物品后按键，可一次取一个、取一组，或尽可能取满背包。
- 容器黑名单：列入黑名单的容器不会被查找或取出，并在视距内持续高亮。
- Forge 端支持 AE2 容器：ME Drive、ME Chest，以及通过 AE2 `MEStorage` 能力提供存储的第三方容器。
- 提供只读黑名单接口，其他模组可以共用同一份数据。

## 安装

- 把 jar 放进 `mods/` 目录；需要 Architectury API 9.1.12 或更高版本，Fabric 端还需要 Fabric API。
- Forge 端可选安装 AE2 15.x，用于支持 ME 容器。
- 运行环境：Java 17 或更高版本。

## 取出物品

- 悬停物品后按「取出 1 个」/「取出一组」，从附近容器取回该物品。
- 按住 Alt 再按「取出一组」：尽可能取满背包。服务端按背包剩余空间（主背包 36 格）截断请求量，因此不会因为背包放不下而把物品丢在地上；附近存量不够时只取到能取的数量，并播放原有的「没取够」音效。
- 普通「取出一组」同样受剩余空间限制：背包快满时只取走放得下的数量，不再溢出到地上。

## 容器黑名单

手持「黑名单工具」（默认木剑，可在 `config/findmeextended.json` 的 `COMMON.BLACKLIST_TOOLS` 里改成任意物品 ID，可填多个）时：

- 视距内的黑名单容器持续高亮：亮黄轮廓（`CLIENT.BLACKLIST_HIGHLIGHT_COLOR`，默认 `#FFE533`）加深灰半透明填充（`CLIENT.BLACKLIST_FILL_COLOR` 默认 `#2D2D2D`、`CLIENT.BLACKLIST_FILL_ALPHA` 默认 0.52，设为 0 就只剩轮廓），穿墙可见、不闪烁。
- 潜行 + 右键点击容器：加入黑名单；潜行 + 左键点击容器：移出黑名单（目标不在黑名单里时左键照常挖方块）。

其他说明：

- 黑名单只认坐标，与方块实体无关：拆掉容器再在同一坐标放一个新的（换种类、换朝向都行）仍然算黑名单；坐标上已经没有容器时，会在下次手持工具高亮时自动清掉该条。
- 双箱子的两半都会高亮，共用同一条记录（统一记坐标较小的那一半），两半都点一遍也只是同一条记录，第二次会提示「已在该坐标的黑名单中」。
- 黑名单按维度存在存档里（`world/data/findmeextended_blacklist.dat`），同一世界、同一维度的所有玩家共用一份；客户端与服务端的 `BLACKLIST_TOOLS` 配置应保持一致（服务端以自己那份为准）。
- 旧版本生成的 `config/findmeextended.json` 不会有新字段，删掉让它重新生成、或手动加上 `BLACKLIST_TOOLS` / `BLACKLIST_HIGHLIGHT_COLOR` / `BLACKLIST_FILL_COLOR` / `BLACKLIST_FILL_ALPHA` 即可。
- AE2 的「整个网络的聚合视图」无法按单个容器排除：存储总线把某个容器挂进网络后，物品是通过网络视图读到的，把这个存储方块列入黑名单不会让它的物品从网络视图里消失。

## 供其他模组使用的接口

`com.lai.findmeextended.api.FindMeBlacklistApi` 是稳定的公开契约，可被反射调用：

- `Set<BlockPos> blacklistedPositions(ServerLevel level)`：该维度黑名单坐标的只读实时视图。
- `boolean isBlacklisted(ServerLevel level, BlockPos pos)`：单个坐标是否在黑名单里。

## 项目来源

本项目 fork 自 [Buuz135/FindMe](https://github.com/Buuz135/FindMe)，基于上游 `1.20.1-3.2.3`。上游以 CC0 1.0（公有领域奉献）发布，本仓库在保留上游著作权声明（Copyright (c) 2018 Buuz135）的前提下以 MIT 发布，详见 [LICENSE](LICENSE)；版本更新见 [changelog.txt](changelog.txt)。

## 构建环境

- Minecraft 1.20.1
- Architectury 9.1.12 / Fabric Loader 0.14.21 / Fabric API 0.91.0+1.20.1 / Forge 47.1.0
- Java 17 或更高版本
- `./gradlew build`，产物在各模块的 `build/libs/`
