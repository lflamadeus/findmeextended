package com.lai.findmeextended.client;

import com.lai.findmeextended.FindMeMod;
import com.lai.findmeextended.blacklist.BlacklistTool;
import com.lai.findmeextended.blacklist.ChestCoordinates;
import com.lai.findmeextended.network.BlacklistRequestMessage;
import com.lai.findmeextended.network.BlacklistSyncMessage;
import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 客户端的黑名单工具：同步高亮坐标、潜行右键加入、潜行左键移出。
 * <p>
 * 所有改动都提交给服务端执行，客户端只负责判定「玩家想改哪个坐标」，并把服务端回报的坐标交给
 * {@link BlacklistHighlighter} 每帧画出来。
 */
public final class BlacklistClient {

    /** 手持工具期间索取一次黑名单坐标的间隔（tick）。 */
    private static final int SYNC_INTERVAL_TICKS = 20;

    private static final int LEFT_BUTTON = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private static final int RIGHT_BUTTON = GLFW.GLFW_MOUSE_BUTTON_RIGHT;

    private static List<BlockPos> highlighted = List.of();
    /** 上一次同步时所在的客户端世界，用来在换维度/换世界时立刻丢弃旧坐标。 */
    private static ClientLevel lastLevel;
    private static long ticks;
    private static long nextSyncTick;

    private BlacklistClient() {
    }

    /** 当前应当高亮的黑名单坐标（服务端回报的视距内坐标），供渲染读取。 */
    public static List<BlockPos> highlightedPositions() {
        return highlighted;
    }

    /** 服务端回报的黑名单快照与本次操作结果。 */
    public static void acceptSync(BlockPos target, List<BlockPos> positions,
                                  BlacklistSyncMessage.Outcome outcome) {
        highlighted = List.copyOf(positions);
        String key = messageKey(outcome);
        LocalPlayer player = Minecraft.getInstance().player;
        if (key == null || player == null) {
            return;
        }
        player.displayClientMessage(
                Component.translatable(key, target.getX(), target.getY(), target.getZ()), true);
    }

    /** 每个客户端 tick：手持工具时定期同步黑名单坐标。 */
    public static void tick(Minecraft client) {
        ticks++;
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            lastLevel = null;
            clearHighlight();
            return;
        }
        if (level != lastLevel) {
            // 换维度或换世界：旧坐标属于旧维度，立刻丢掉，等下一次同步补上新的。
            lastLevel = level;
            clearHighlight();
        }
        if (!BlacklistTool.isHeld(player)) {
            clearHighlight();
            return;
        }
        if (ticks >= nextSyncTick) {
            FindMeMod.CHANNEL.sendToServer(
                    new BlacklistRequestMessage(BlacklistRequestMessage.ACTION_SYNC, BlockPos.ZERO));
            nextSyncTick = ticks + SYNC_INTERVAL_TICKS;
        }
    }

    /**
     * 鼠标按下：潜行右键加入黑名单，潜行左键移出黑名单。
     * <p>
     * 返回 {@code interruptFalse()} 会取消这次按键的原版处理，按键映射根本不会按下，因此既不会打开
     * 容器界面，也不会开始挖方块。
     * <p>
     * 左键只有目标确实在黑名单里才拦截，否则潜行时用工具挖方块会被一起挡住；右键只拦截有方块实体的
     * 目标（潜在的容器），工作台、告示牌、放置方块之类留给原版。这里刻意不在客户端判断「目标是不是
     * 容器」：各模组在客户端暴露能力的情况并不一致，漏判会让玩家根本加不进黑名单，真正的判定交给
     * 服务端，结果通过动作栏提示回报。
     */
    public static EventResult onMouseClicked(Minecraft client, int button, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS || client.screen != null || client.level == null) {
            return EventResult.pass();
        }
        LocalPlayer player = client.player;
        if (player == null || !client.options.keyShift.isDown() || !BlacklistTool.isHeld(player)) {
            return EventResult.pass();
        }
        BlockPos target = targetedBlock(client);
        if (target == null) {
            return EventResult.pass();
        }
        if (button == RIGHT_BUTTON) {
            if (client.level.getBlockEntity(target) == null) {
                return EventResult.pass();
            }
            FindMeMod.CHANNEL.sendToServer(
                    new BlacklistRequestMessage(BlacklistRequestMessage.ACTION_ADD, target));
            return EventResult.interruptFalse();
        }
        if (button == LEFT_BUTTON) {
            if (!isBlacklisted(client, target)) {
                return EventResult.pass();
            }
            FindMeMod.CHANNEL.sendToServer(
                    new BlacklistRequestMessage(BlacklistRequestMessage.ACTION_REMOVE, target));
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }

    /** 鼠标指向的方块坐标，没有指向方块时返回 null。 */
    private static BlockPos targetedBlock(Minecraft client) {
        return client.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
    }

    /** 坐标是否在黑名单里；双箱子按归一化后的那一半判断。 */
    private static boolean isBlacklisted(Minecraft client, BlockPos pos) {
        return highlighted.contains(ChestCoordinates.canonical(client.level, pos));
    }

    /** 不再手持工具时丢掉高亮，并让下次手持立刻重新同步。 */
    private static void clearHighlight() {
        if (!highlighted.isEmpty()) {
            highlighted = List.of();
        }
        nextSyncTick = 0L;
    }

    /** 操作结果对应的提示文本；纯同步与静默忽略没有提示。 */
    private static String messageKey(BlacklistSyncMessage.Outcome outcome) {
        return switch (outcome) {
            case ADDED -> "text.findmeextended.blacklist.added";
            case REMOVED -> "text.findmeextended.blacklist.removed";
            case ALREADY_BLACKLISTED -> "text.findmeextended.blacklist.already";
            case NOT_BLACKLISTED -> "text.findmeextended.blacklist.absent";
            case NOT_A_CONTAINER -> "text.findmeextended.blacklist.not_container";
            case OUT_OF_RANGE -> "text.findmeextended.blacklist.out_of_range";
            case NONE, IGNORED -> null;
        };
    }
}
