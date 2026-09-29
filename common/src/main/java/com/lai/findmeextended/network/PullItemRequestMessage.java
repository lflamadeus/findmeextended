package com.lai.findmeextended.network;

import com.lai.findmeextended.FindMeMod;
import com.lai.findmeextended.IInventoryPuller;
import com.lai.findmeextended.blacklist.BlacklistFilter;
import com.lai.findmeextended.tracking.TrackingList;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.function.Supplier;

public class PullItemRequestMessage {

    /**
     * “尽可能填满背包”的请求量。
     * <p>
     * 服务端会按背包剩余空间截断，所以这里给一个足够大的数字即可，不必让客户端去数背包。
     */
    public static final int FILL_INVENTORY = Integer.MAX_VALUE;

    private ItemStack stack;
    private int amount;

    public PullItemRequestMessage(ItemStack stack, int amount) {
        this.stack = stack;
        this.amount = amount;
        TrackingList.trackItem(stack);
    }

    public PullItemRequestMessage() {

    }

    public static boolean compareItems(ItemStack first, ItemStack second) {
        if (FindMeMod.CONFIG.COMMON.IGNORE_ITEM_DAMAGE)
            return ItemStack.isSameItem(first, second);
        return ItemStack.isSameItemSameTags(first, second);
    }

    public PullItemRequestMessage fromBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        stack = ItemStack.EMPTY;
        stack = packetBuffer.readItem();
        amount = packetBuffer.readInt();
        return this;
    }

    public void toBytes(ByteBuf buf) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(buf);
        packetBuffer.writeItem(stack);
        packetBuffer.writeInt(amount);
    }

    public void handle(Supplier<NetworkManager.PacketContext> contextSupplier) {
        contextSupplier.get().queue(() -> {
            Player player = contextSupplier.get().getPlayer();
            if (amount <= 0 || stack.isEmpty()) {
                return;
            }
            // 请求量超过背包能装下的部分直接截掉：交付走的是 ItemHandlerHelper.giveItemToPlayer，
            // 装不下的会被丢在地上，而“尽可能填满背包”的语义就是不溢出。普通“取出一组”同样受此限制，
            // 背包快满时只会取走放得下的数量。
            int limit = Math.min(amount, remainingCapacity(player, stack));
            if (limit <= 0) {
                return;
            }
            AABB box = new AABB(player.blockPosition()).inflate(FindMeMod.CONFIG.COMMON.RADIUS_RANGE);
            // 黑名单坐标取一次，循环里只查哈希表；被列入黑名单的容器不会被取出任何物品。
            Set<BlockPos> blacklisted = BlacklistFilter.blacklistedPositions(player);
            var currentAmount = 0;
            for (BlockPos blockPos : PositionRequestMessage.getBlockPosInAABB(box)) {
                BlockEntity tileEntity = player.level().getBlockEntity(blockPos);
                if (tileEntity == null || BlacklistFilter.excludes(player.level(), blockPos, tileEntity, blacklisted)) {
                    continue;
                }
                for (IInventoryPuller blockExtractor : FindMeMod.BLOCK_EXTRACTORS) {
                    currentAmount += blockExtractor.pull(tileEntity, stack, limit - currentAmount, player);
                    if (currentAmount >= limit) {
                        break;
                    }
                }
                if (currentAmount >= limit) {
                    break;
                }
            }
            if (currentAmount < limit) {
                var level = player.level();
                level.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }
        });
        //contextSupplier.get().setPacketHandled(true);
    }

    /**
     * 玩家主背包还能再装下多少个这种物品。
     * <p>
     * 只数主背包的 36 格：Forge 的 {@code ItemHandlerHelper.giveItemToPlayer} 就是往
     * {@code PlayerMainInvWrapper} 里塞，副手与护甲槽不参与，数多了会把物品挤到地上。
     * 匹配规则与取出时一致（{@link #compareItems}），所以已有堆叠能装下的空间也会算进去。
     */
    private static int remainingCapacity(Player player, ItemStack stack) {
        int maxStackSize = stack.getMaxStackSize();
        int capacity = 0;
        for (ItemStack slot : player.getInventory().items) {
            if (slot.isEmpty()) {
                capacity += maxStackSize;
            } else if (compareItems(slot, stack)) {
                capacity += Math.max(0, maxStackSize - slot.getCount());
            }
        }
        return capacity;
    }
}
