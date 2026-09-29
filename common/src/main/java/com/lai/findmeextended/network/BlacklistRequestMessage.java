package com.lai.findmeextended.network;

import com.lai.findmeextended.blacklist.BlacklistService;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

/** 客户端的黑名单操作请求：同步高亮、加入黑名单、移出黑名单。 */
public class BlacklistRequestMessage {

    /** 索取视距内的黑名单坐标，用于高亮；不使用 {@link #pos}。 */
    public static final int ACTION_SYNC = 0;
    /** 把目标容器加入黑名单。 */
    public static final int ACTION_ADD = 1;
    /** 把目标容器移出黑名单。 */
    public static final int ACTION_REMOVE = 2;

    private int action;
    private BlockPos pos;

    public BlacklistRequestMessage(int action, BlockPos pos) {
        this.action = action;
        this.pos = pos;
    }

    public BlacklistRequestMessage() {
    }

    public BlacklistRequestMessage fromBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        action = packetBuffer.readVarInt();
        pos = packetBuffer.readBlockPos();
        return this;
    }

    public void toBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        packetBuffer.writeVarInt(action);
        packetBuffer.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkManager.PacketContext> contextSupplier) {
        contextSupplier.get().queue(() -> {
            Player player = contextSupplier.get().getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            switch (action) {
                case ACTION_SYNC -> BlacklistService.sync(serverPlayer);
                case ACTION_ADD -> BlacklistService.add(serverPlayer, pos);
                case ACTION_REMOVE -> BlacklistService.remove(serverPlayer, pos);
                default -> {
                    // 未知动作直接忽略，避免旧版本客户端让服务端走到意料之外的分支。
                }
            }
        });
    }
}
