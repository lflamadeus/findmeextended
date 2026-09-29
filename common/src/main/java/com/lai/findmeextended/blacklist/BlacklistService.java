package com.lai.findmeextended.blacklist;

import com.lai.findmeextended.FindMeMod;
import com.lai.findmeextended.network.BlacklistSyncMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** 服务端黑名单：处理增删请求、清理失效条目，并把高亮用的坐标同步给客户端。 */
public final class BlacklistService {

    private static final Logger LOGGER = LoggerFactory.getLogger("findmeextended");

    private BlacklistService() {
    }

    /** 同步视距内的黑名单容器坐标，顺带清理已经不存在的条目。 */
    public static void sync(ServerPlayer player) {
        if (!canManage(player)) {
            respond(player, BlockPos.ZERO, List.of(), BlacklistSyncMessage.Outcome.IGNORED);
            return;
        }
        ContainerBlacklist blacklist = ContainerBlacklist.get(player.serverLevel());
        respond(player, BlockPos.ZERO, collectVisible(player, blacklist), BlacklistSyncMessage.Outcome.NONE);
    }

    /** 把目标容器加入黑名单。 */
    public static void add(ServerPlayer player, BlockPos pos) {
        if (!canManage(player)) {
            respond(player, pos, List.of(), BlacklistSyncMessage.Outcome.IGNORED);
            return;
        }
        ServerLevel level = player.serverLevel();
        ContainerBlacklist blacklist = ContainerBlacklist.get(level);
        BlockPos target = ChestCoordinates.canonical(level, pos);
        BlacklistSyncMessage.Outcome outcome;
        if (!isWithinViewDistance(player, target) || !level.isLoaded(target)) {
            outcome = BlacklistSyncMessage.Outcome.OUT_OF_RANGE;
        } else if (!FindMeMod.isContainer(level.getBlockEntity(target))) {
            outcome = BlacklistSyncMessage.Outcome.NOT_A_CONTAINER;
        } else if (blacklist.add(target)) {
            outcome = BlacklistSyncMessage.Outcome.ADDED;
        } else {
            outcome = BlacklistSyncMessage.Outcome.ALREADY_BLACKLISTED;
        }
        respond(player, target, collectVisible(player, blacklist), outcome);
    }

    /** 把目标容器移出黑名单。 */
    public static void remove(ServerPlayer player, BlockPos pos) {
        if (!canManage(player)) {
            respond(player, pos, List.of(), BlacklistSyncMessage.Outcome.IGNORED);
            return;
        }
        ServerLevel level = player.serverLevel();
        ContainerBlacklist blacklist = ContainerBlacklist.get(level);
        BlockPos target = ChestCoordinates.canonical(level, pos);
        // 与加入一样按视距设限：黑名单是同一维度所有玩家共用的，不该允许改动视距之外的条目。
        boolean removed = isWithinViewDistance(player, target) && blacklist.remove(target);
        respond(player, target, collectVisible(player, blacklist),
                removed ? BlacklistSyncMessage.Outcome.REMOVED
                        : BlacklistSyncMessage.Outcome.NOT_BLACKLISTED);
    }

    /**
     * 玩家是否可以管理黑名单。
     * <p>
     * 要求手持工具：黑名单只服务于容器搜索与取出，未手持工具时没有任何理由改动它。服务端按自己的
     * 配置判定，不信任客户端的说法。
     */
    private static boolean canManage(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (!BlacklistTool.isHeld(player)) {
            // 客户端与服务端的黑名单工具配置不一致时，表现是「工具完全没反应」；
            // 这一行是排查该情况最快的线索（默认日志级别下不会输出）。
            LOGGER.debug("[FindMeExtended] 玩家 {} 未手持黑名单工具，忽略黑名单请求",
                    player.getGameProfile().getName());
            return false;
        }
        return true;
    }

    /**
     * 收集视距内仍然有效的黑名单坐标。
     * <p>
     * 这里只遍历黑名单本身，不做区块扫描：黑名单通常只有几条到几十条，代价与列表长度同阶，
     * 比逐格或逐区块枚举方块实体低好几个数量级。顺带把「方块实体已经不在」或「已经不是容器」的条目
     * 清掉，避免无效坐标长期积累——清理只在玩家手持工具、主动索取高亮时发生，所以拆掉容器后立刻
     * 在原坐标放回新容器仍然是黑名单（黑名单只认坐标）。
     */
    private static List<BlockPos> collectVisible(ServerPlayer player, ContainerBlacklist blacklist) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> visible = new ArrayList<>();
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos pos : blacklist.positions()) {
            // isLoaded 只查区块是否已加载，不会加载区块；必须先判断，否则 getBlockEntity 会把区块读进来。
            if (!isWithinViewDistance(player, pos) || !level.isLoaded(pos)) {
                continue;
            }
            if (!FindMeMod.isContainer(level.getBlockEntity(pos))) {
                stale.add(pos);
                continue;
            }
            visible.add(pos);
        }
        if (!stale.isEmpty()) {
            blacklist.removeAll(stale);
        }
        return visible;
    }

    /**
     * 判断坐标是否落在玩家的视距内。
     * <p>
     * 服务端视距（区块数 × 16 格）就是客户端能看到的最远范围；客户端渲染距离更小时高亮自然被裁掉，
     * 不需要两端再对一次数。
     */
    private static boolean isWithinViewDistance(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.getServer();
        int viewDistance = server == null ? 10 : server.getPlayerList().getViewDistance();
        int radius = Math.max(1, viewDistance) * 16;
        BlockPos center = player.blockPosition();
        return Math.abs(pos.getX() - center.getX()) <= radius
                && Math.abs(pos.getY() - center.getY()) <= radius
                && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    /** 把处理结果和最新的高亮列表发回给玩家。 */
    private static void respond(ServerPlayer player, BlockPos target, List<BlockPos> positions,
                                BlacklistSyncMessage.Outcome outcome) {
        if (player == null) {
            return;
        }
        FindMeMod.CHANNEL.sendToPlayer(player, new BlacklistSyncMessage(target, positions, outcome));
    }
}
