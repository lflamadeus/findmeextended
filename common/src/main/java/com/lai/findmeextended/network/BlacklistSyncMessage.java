package com.lai.findmeextended.network;

import com.lai.findmeextended.client.BlacklistClient;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端返回的黑名单快照与本次操作结果。
 * <p>
 * {@code positions} 是玩家视距内仍然有效的黑名单坐标，客户端用它补充高亮标记；{@code target} 是本次
 * 操作的目标坐标（纯同步时为 {@link BlockPos#ZERO}），只用于给玩家提示。
 */
public class BlacklistSyncMessage {

    /** 单次同步最多携带的坐标数量，仅作为解码上限。 */
    public static final int MAX_POSITIONS = 4096;

    /** 本次请求的处理结果。 */
    public enum Outcome {
        /** 纯同步，没有需要提示的操作。 */
        NONE,
        ADDED,
        REMOVED,
        ALREADY_BLACKLISTED,
        NOT_BLACKLISTED,
        NOT_A_CONTAINER,
        OUT_OF_RANGE,
        /** 服务端认为玩家没有手持黑名单工具（或反转搜索不可用）。 */
        IGNORED
    }

    private BlockPos target;
    private List<BlockPos> positions;
    private Outcome outcome;

    public BlacklistSyncMessage(BlockPos target, List<BlockPos> positions, Outcome outcome) {
        this.target = target;
        this.positions = List.copyOf(positions);
        this.outcome = outcome;
    }

    public BlacklistSyncMessage() {
    }

    public BlacklistSyncMessage fromBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        target = packetBuffer.readBlockPos();
        Outcome[] outcomes = Outcome.values();
        int outcomeIndex = packetBuffer.readVarInt();
        outcome = outcomeIndex >= 0 && outcomeIndex < outcomes.length ? outcomes[outcomeIndex] : Outcome.NONE;
        int size = packetBuffer.readVarInt();
        if (size < 0 || size > MAX_POSITIONS) {
            throw new IllegalArgumentException("黑名单坐标数量超出限制: " + size);
        }
        positions = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            positions.add(packetBuffer.readBlockPos());
        }
        return this;
    }

    public void toBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        packetBuffer.writeBlockPos(target);
        packetBuffer.writeVarInt(outcome.ordinal());
        int size = Math.min(positions.size(), MAX_POSITIONS);
        packetBuffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            packetBuffer.writeBlockPos(positions.get(index));
        }
    }

    public void handle(Supplier<NetworkManager.PacketContext> contextSupplier) {
        Minecraft.getInstance().execute(() -> BlacklistClient.acceptSync(target, positions, outcome));
    }
}
