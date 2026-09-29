package com.lai.findmeextended.blacklist;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 一个维度内的容器黑名单，只记录方块坐标。
 * <p>
 * 存成维度存档（{@code world/data/findmeextended_blacklist.dat}），同一世界同一维度的所有玩家共用
 * 一份。之所以只认坐标而不认方块实体：容器被拆掉再在原坐标放一个新的（换个朝向、换个种类）时，
 * 玩家的意图仍然是「这个位置别动」，所以判定必须与方块实体无关。
 * <p>
 * 列入黑名单的容器不会被搜索、不会被取出，也不会出现在查找结果的高亮里。
 */
public final class ContainerBlacklist extends SavedData {

    private static final String DATA_NAME = "findmeextended_blacklist";
    private static final String POSITIONS_KEY = "Positions";

    private final Set<BlockPos> positions = new HashSet<>();
    /** 只读视图，避免外部通过迭代器绕过 {@link #setDirty()} 改动内容。 */
    private final Set<BlockPos> readOnlyPositions = Collections.unmodifiableSet(positions);

    private ContainerBlacklist() {
    }

    /** 取得该维度存档里的黑名单，还没有就新建一份。 */
    public static ContainerBlacklist get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ContainerBlacklist::load,
                ContainerBlacklist::new, DATA_NAME);
    }

    private static ContainerBlacklist load(CompoundTag tag) {
        ContainerBlacklist blacklist = new ContainerBlacklist();
        for (long packed : tag.getLongArray(POSITIONS_KEY)) {
            blacklist.positions.add(BlockPos.of(packed));
        }
        return blacklist;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] packed = new long[positions.size()];
        int index = 0;
        for (BlockPos pos : positions) {
            packed[index++] = pos.asLong();
        }
        // 哈希表的遍历顺序不稳定，排序后存档内容才可预期，也便于人工查看。
        Arrays.sort(packed);
        tag.putLongArray(POSITIONS_KEY, packed);
        return tag;
    }

    /** 坐标是否在黑名单里。 */
    public boolean contains(BlockPos pos) {
        return positions.contains(pos);
    }

    /** 加入黑名单，返回是否真的新增（本来就在里面时返回 false）。 */
    public boolean add(BlockPos pos) {
        if (!positions.add(pos)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** 移出黑名单，返回是否真的移除（本来就不在里面时返回 false）。 */
    public boolean remove(BlockPos pos) {
        if (!positions.remove(pos)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** 批量移出，用于清理已经不存在的容器。 */
    public void removeAll(Collection<BlockPos> stale) {
        if (positions.removeAll(stale)) {
            setDirty();
        }
    }

    /** 当前条目数。 */
    public int size() {
        return positions.size();
    }

    /** 只读遍历。元素是不可变的 {@link BlockPos}，可以安全地长期持有。 */
    public Set<BlockPos> positions() {
        return readOnlyPositions;
    }
}
