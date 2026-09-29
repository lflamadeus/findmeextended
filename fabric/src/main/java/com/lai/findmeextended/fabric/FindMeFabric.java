package com.lai.findmeextended.fabric;

import com.lai.findmeextended.FindMeMod;
import com.lai.findmeextended.network.PositionRequestMessage;
import dev.architectury.event.events.common.InteractionEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;

public class FindMeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FindMeMod.init();
        FindMeMod.BLOCK_CHECKERS.add((blockEntity, stack) -> {
            try (Transaction transaction = Transaction.openOuter()) {
                for (Direction value : Direction.values()) {
                    Storage<ItemVariant> storage = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, value);
                    if (storage == null) continue;
                    for (Iterator<StorageView<ItemVariant>> it = storage.iterator(); it.hasNext(); ) {
                        StorageView<ItemVariant> itemVariantStorageView = it.next();
                        ItemStack invStack = itemVariantStorageView.getResource().toStack();
                        if (!invStack.isEmpty() && PositionRequestMessage.compareItems(stack, invStack)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
        FindMeMod.BLOCK_EXTRACTORS.add((entity, stack, amount, player) -> {
            if (InteractionEvent.RIGHT_CLICK_BLOCK.invoker().click(player, InteractionHand.MAIN_HAND, entity.getBlockPos(), Direction.UP).isFalse()) {
                return 0;
            }
            try (Transaction transaction = Transaction.openOuter()) {
                var playerInventory = PlayerInventoryStorage.of(player);
                var totalExtracted = 0;
                for (Direction value : Direction.values()) {
                    Storage<ItemVariant> storage = ItemStorage.SIDED.find(entity.getLevel(), entity.getBlockPos(), entity.getBlockState(), entity, value);
                    if (storage == null) continue;
                    for (Iterator<StorageView<ItemVariant>> it = storage.iterator(); it.hasNext(); ) {
                        StorageView<ItemVariant> itemVariantStorageView = it.next();
                        ItemStack invStack = itemVariantStorageView.getResource().toStack();
                        if (!invStack.isEmpty() && PositionRequestMessage.compareItems(stack, invStack)) {
                            var extracted = storage.extract(ItemVariant.of(stack.copy()), amount - totalExtracted, transaction);
                            playerInventory.offerOrDrop(ItemVariant.of(stack.copy()), extracted, transaction);
                            totalExtracted += extracted;
                            var level = player.level();
                            level.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(),
                                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                        }
                        if (totalExtracted >= amount) {
                            break;
                        }
                    }
                    if (totalExtracted >= amount) {
                        break;
                    }
                }
                transaction.commit();
                return totalExtracted;
            }
        });

        /*
         * 黑名单工具用它判断「这个方块实体算不算容器」。原版容器在 common 侧按 Container 直接认，
         * 这里补上 Fabric 的 ItemStorage，模组容器才能加入黑名单。
         */
        FindMeMod.BLOCK_CONTAINER_CHECKERS.add(blockEntity -> {
            for (Direction direction : Direction.values()) {
                if (ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, direction) != null) {
                    return true;
                }
            }
            return false;
        });

    }
}
