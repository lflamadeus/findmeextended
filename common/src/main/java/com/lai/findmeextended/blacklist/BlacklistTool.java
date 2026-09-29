package com.lai.findmeextended.blacklist;

import com.lai.findmeextended.FindMeMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 判断玩家是否手持「黑名单工具」。
 * <p>
 * 工具默认是木剑，可在 {@code config/findmeextended.json} 的 {@code COMMON.BLACKLIST_TOOLS} 里改成
 * 任意物品 ID（可以填多个）。手持它时：视距内的黑名单容器持续高亮，潜行右键加入黑名单，
 * 潜行左键移出黑名单。
 */
public final class BlacklistTool {

    private static final Logger LOGGER = LoggerFactory.getLogger("findmeextended");

    /** 上一次解析用的配置内容，用来判断缓存是否还有效。 */
    private static volatile List<String> cachedIds = List.of();
    private static volatile Set<Item> cachedItems = Set.of();

    private BlacklistTool() {
    }

    /**
     * 玩家主手是否拿着黑名单工具。
     * <p>
     * 只认主手：黑名单工具是一个「管理模式」，同时允许副手会让潜行右键的归属变得含糊。
     */
    public static boolean isHeld(Player player) {
        return player != null && items().contains(player.getMainHandItem().getItem());
    }

    /**
     * 解析并缓存配置里的工具物品。
     * <p>
     * 配置只在启动时读取一次，所以正常情况下这里只会解析一次；用列表内容比对是为了让玩家改完配置
     * 重启后立刻生效，也避免每 tick 都去查注册表。
     */
    private static Set<Item> items() {
        List<String> ids = FindMeMod.CONFIG.COMMON.BLACKLIST_TOOLS;
        if (ids == null) {
            return Set.of();
        }
        if (ids.equals(cachedIds)) {
            return cachedItems;
        }
        Set<Item> resolved = new HashSet<>(ids.size());
        for (String id : ids) {
            ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
            Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR) {
                LOGGER.warn("[FindMeExtended] 黑名单工具 {} 不是有效物品，已忽略", id);
                continue;
            }
            resolved.add(item);
        }
        Set<Item> snapshot = Set.copyOf(resolved);
        cachedIds = List.copyOf(ids);
        cachedItems = snapshot;
        return snapshot;
    }
}
