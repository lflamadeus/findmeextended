package com.lai.findmeextended.api;

import com.lai.findmeextended.blacklist.ContainerBlacklist;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * 供其他模组读取黑名单的公开接口。
 * <p>
 * 黑名单本体在 FindMeExtended 里维护（见 {@link ContainerBlacklist}）：手持黑名单工具可以增删，
 * 存档按维度保存、只认坐标。其他模组（例如配方材料发送器）在做「周围容器搜索」时，应该用这里的
 * 接口把黑名单坐标排除掉，保证两边的行为一致。
 * <p>
 * 这个类的类名、方法名与签名都是稳定的公开契约，可以被安全地反射调用；FindMeExtended 未安装时
 * 调用方应当自行降级为「没有黑名单」。
 */
public final class FindMeBlacklistApi {

    private FindMeBlacklistApi() {
    }

    /**
     * 该维度黑名单坐标的只读视图。
     * <p>
     * 返回的是实时视图而不是副本：调用方通常在服务端主线程上扫描容器，边扫边查最省事，
     * 也不需要为每次扫描复制一份集合。视图不可修改。
     */
    public static Set<BlockPos> blacklistedPositions(ServerLevel level) {
        return ContainerBlacklist.get(level).positions();
    }

    /** 坐标是否在黑名单里。 */
    public static boolean isBlacklisted(ServerLevel level, BlockPos pos) {
        return ContainerBlacklist.get(level).contains(pos);
    }
}
