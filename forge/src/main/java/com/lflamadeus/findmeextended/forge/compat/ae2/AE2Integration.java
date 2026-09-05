package com.lflamadeus.findmeextended.forge.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.capabilities.Capabilities;
import com.lflamadeus.findmeextended.FindMeMod;
import com.lflamadeus.findmeextended.forge.FindMeModForge;
import com.lflamadeus.findmeextended.network.PositionRequestMessage;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Optional AE2 integration for FindMe.
 *
 * <p>This class is loaded only after Forge confirms that AE2 is installed. Keeping AE2-specific
 * types in this class prevents FindMe from loading AE2 classes when AE2 is absent.</p>
 *
 * <p>The integration is additive: the original vanilla and Forge item-handler integrations stay
 * registered and retain their existing behavior.</p>
 */
public final class AE2Integration {

    private AE2Integration() {
    }

    /** Registers the AE2 checker and extractor. */
    public static void register() {
        FindMeMod.BLOCK_CHECKERS.add(AE2Integration::containsRequestedItem);
        FindMeMod.BLOCK_EXTRACTORS.add(AE2Integration::extractRequestedItem);
    }

    /**
     * Checks an AE2 container for the requested item.
     *
     * <p>AE2 drives and chests are checked cell by cell. This keeps FindMe's result tied to the
     * physical container containing the item instead of reporting every block on a shared network.
     * Third-party machines, including GTOCore's Multiblock ME Storage, are checked through their
     * direct {@link Capabilities#STORAGE} capability.</p>
     */
    private static boolean containsRequestedItem(BlockEntity blockEntity, ItemStack requestedStack) {
        if (requestedStack.isEmpty()) {
            return false;
        }

        if (blockEntity instanceof IChestOrDrive chestOrDrive) {
            if (!chestOrDrive.isPowered()) {
                return false;
            }

            for (int slot = 0; slot < chestOrDrive.getCellCount(); slot++) {
                MEStorage cellStorage = chestOrDrive.getCellInventory(slot);
                if (cellStorage != null && containsRequestedItem(cellStorage, requestedStack)) {
                    return true;
                }
            }
            return false;
        }

        MEStorage storage = resolveStorage(blockEntity);
        return storage != null && containsRequestedItem(storage, requestedStack);
    }

    /**
     * Extracts up to {@code amount} matching items from an AE2 container and gives them to the
     * player. The exact AE2 key is used, preserving the stored item's NBT and durability.
     */
    private static int extractRequestedItem(
            BlockEntity blockEntity,
            ItemStack requestedStack,
            int amount,
            Player player) {
        if (amount <= 0 || requestedStack.isEmpty()) {
            return 0;
        }

        if (!FindMeModForge.canBlockBeInteracted(
                blockEntity.getLevel(), blockEntity.getBlockPos(), player)) {
            return 0;
        }

        if (blockEntity instanceof IChestOrDrive chestOrDrive) {
            if (!chestOrDrive.isPowered()) {
                return 0;
            }

            IActionSource actionSource = IActionSource.ofPlayer(player, chestOrDrive);
            int extracted = 0;
            for (int slot = 0;
                    slot < chestOrDrive.getCellCount() && extracted < amount;
                    slot++) {
                MEStorage cellStorage = chestOrDrive.getCellInventory(slot);
                if (cellStorage != null) {
                    extracted += extractFromStorage(
                            cellStorage,
                            requestedStack,
                            amount - extracted,
                            player,
                            actionSource);
                }
            }
            return extracted;
        }

        MEStorage storage = resolveStorage(blockEntity);
        if (storage == null) {
            return 0;
        }

        // Include the host when possible so AE2 can apply normal security and ownership checks.
        IActionSource actionSource = blockEntity instanceof IActionHost actionHost
                ? IActionSource.ofPlayer(player, actionHost)
                : IActionSource.ofPlayer(player);
        return extractFromStorage(storage, requestedStack, amount, player, actionSource);
    }

    /** Checks one ME storage by iterating its available item keys. */
    private static boolean containsRequestedItem(MEStorage storage, ItemStack requestedStack) {
        AEItemKey requestedKey = AEItemKey.of(requestedStack);
        for (var entry : storage.getAvailableStacks().findFuzzy(requestedKey, FuzzyMode.IGNORE_ALL)) {
            if (entry.getLongValue() > 0 && isMatchingItem(entry.getKey(), requestedStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts matching item keys from one ME storage. A snapshot from getAvailableStacks is used,
     * so modifying the storage during extraction does not invalidate the iteration.
     */
    private static int extractFromStorage(
            MEStorage storage,
            ItemStack requestedStack,
            int amount,
            Player player,
            IActionSource actionSource) {
        int extractedTotal = 0;
        AEItemKey requestedKey = AEItemKey.of(requestedStack);

        for (var entry : storage.getAvailableStacks().findFuzzy(requestedKey, FuzzyMode.IGNORE_ALL)) {
            if (extractedTotal >= amount) {
                break;
            }
            if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            if (!isMatchingItem(itemKey, requestedStack)) {
                continue;
            }

            long extracted = storage.extract(
                    itemKey,
                    amount - extractedTotal,
                    Actionable.MODULATE,
                    actionSource);
            if (extracted <= 0) {
                continue;
            }

            // This is the same delivery helper used by FindMe's existing Forge extractor; it also
            // drops overflow safely when the player's inventory has no free space.
            ItemHandlerHelper.giveItemToPlayer(player, itemKey.toStack((int) extracted));
            extractedTotal += (int) extracted;
        }

        return extractedTotal;
    }

    /** Applies FindMe's existing item comparison rules to an AE2 key. */
    private static boolean isMatchingItem(AEKey key, ItemStack requestedStack) {
        return key instanceof AEItemKey itemKey && isMatchingItem(itemKey, requestedStack);
    }

    private static boolean isMatchingItem(AEItemKey itemKey, ItemStack requestedStack) {
        return PositionRequestMessage.compareItems(requestedStack, itemKey.toStack());
    }

    /**
     * Resolves a direct AE2 MEStorage capability from a block entity.
     *
     * <p>Forge providers may return either null or an empty LazyOptional for unsupported
     * capabilities, so both cases are handled. A side-less query is intentional: GTOCore's
     * Multiblock ME Storage exposes this capability when queried with a null side (and also on its
     * front side).</p>
     */
    @Nullable
    private static MEStorage resolveStorage(BlockEntity blockEntity) {
        LazyOptional<MEStorage> capability = blockEntity.getCapability(Capabilities.STORAGE, null);
        return capability == null ? null : capability.orElse(null);
    }
}
