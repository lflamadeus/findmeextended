package com.lai.findmeextended.network;

import com.lai.findmeextended.FindMeMod;
import com.lai.findmeextended.blacklist.BlacklistFilter;
import com.lai.findmeextended.tracking.TrackingList;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class PositionRequestMessage {

    private ItemStack stack;

    public PositionRequestMessage(ItemStack stack) {
        this.stack = stack;
        TrackingList.trackItem(stack);
    }

    public PositionRequestMessage() {
    }

    public static List<BlockPos> getBlockPosInAABB(AABB axisAlignedBB) {
        List<BlockPos> blocks = new ArrayList<BlockPos>();
        for (double y = axisAlignedBB.minY; y < axisAlignedBB.maxY; ++y) {
            for (double x = axisAlignedBB.minX; x < axisAlignedBB.maxX; ++x) {
                for (double z = axisAlignedBB.minZ; z < axisAlignedBB.maxZ; ++z) {
                    blocks.add(new BlockPos((int) x, (int) y, (int) z));
                }
            }
        }
        return blocks;
    }

    public PositionRequestMessage fromBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        stack = ItemStack.EMPTY;
        stack = packetBuffer.readItem();
        return this;
    }

    public void toBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        packetBuffer.writeItem(stack);
    }

    public static boolean compareItems(ItemStack first, ItemStack second) {
        if (FindMeMod.CONFIG.COMMON.IGNORE_ITEM_DAMAGE)
            return ItemStack.isSameItem(first, second);
        return ItemStack.isSameItemSameTags(first, second);
    }

    public void handle(Supplier<NetworkManager.PacketContext> contextSupplier) {
        contextSupplier.get().queue(() -> {
            Player player = contextSupplier.get().getPlayer();
            AABB box = new AABB(player.blockPosition()).inflate(FindMeMod.CONFIG.COMMON.RADIUS_RANGE);
            // 黑名单坐标取一次，循环里只查哈希表；被列入黑名单的容器不参与查找，也就不会被高亮。
            Set<BlockPos> blacklisted = BlacklistFilter.blacklistedPositions(player);
            List<BlockPos> blockPosList = new ArrayList<>();
            for (BlockPos blockPos : getBlockPosInAABB(box)) {
                BlockEntity tileEntity = player.level().getBlockEntity(blockPos);
                if (tileEntity == null || BlacklistFilter.excludes(player.level(), blockPos, tileEntity, blacklisted)) {
                    continue;
                }
                if (FindMeMod.BLOCK_CHECKERS.stream().anyMatch(predicate -> predicate.test(tileEntity, stack))) {
                    blockPosList.add(blockPos);
                }
            }
            if (!blockPosList.isEmpty())
                FindMeMod.CHANNEL.sendToPlayer((ServerPlayer) player,new PositionResponseMessage(blockPosList));

        });
        //contextSupplier.get().setPacketHandled(true);
    }

}
