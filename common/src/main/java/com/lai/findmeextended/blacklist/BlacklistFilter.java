package com.lai.findmeextended.blacklist;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Set;

/**
 * 搜索与取出共用的黑名单判定。
 * <p>
 * 查找物品和取回物品都要跳过黑名单坐标。黑名单坐标在循环外取一次，循环里只做哈希查表，
 * 避免在逐格遍历里反复访问存档数据。
 */
public final class BlacklistFilter {

    private BlacklistFilter() {
    }

    /** 取玩家所在维度的黑名单坐标；不是服务端世界时返回空集合。 */
    public static Set<BlockPos> blacklistedPositions(Player player) {
        return player.level() instanceof ServerLevel serverLevel
                ? ContainerBlacklist.get(serverLevel).positions()
                : Set.of();
    }

    /**
     * 坐标是否应当被跳过。
     * <p>
     * 双箱子的两半共用同一份库存，而黑名单只记坐标较小的那一半：只查坐标本身会让另一半漏网，
     * 于是同一份库存还能被搜到、被取出。这里在方块实体是箱子时再比一次归一化坐标，
     * 其余方块只做一次哈希查表。
     */
    public static boolean excludes(Level level, BlockPos pos, BlockEntity blockEntity,
                                   Set<BlockPos> blacklisted) {
        if (blacklisted.isEmpty()) {
            return false;
        }
        if (blacklisted.contains(pos)) {
            return true;
        }
        return blockEntity instanceof ChestBlockEntity
                && blacklisted.contains(ChestCoordinates.canonical(level, pos));
    }
}
