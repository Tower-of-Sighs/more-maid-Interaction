package cc.sighs.more_maid_interaction.mc.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class GiftStagingManager {
    private static final int MAX_DISTINCT = 48;
    private static final int MAX_TOTAL = 4096;

    private static final Map<UUID, StageState> STAGED_BY_PLAYER = new LinkedHashMap<>();

    private GiftStagingManager() {
    }

    static int stageAdd(ServerPlayer player, UUID maidUuid, ItemStack template, int requestCount) {
        if (player == null || maidUuid == null || template == null || template.isEmpty() || requestCount <= 0) {
            return 0;
        }

        StageState state = STAGED_BY_PLAYER.get(player.getUUID());
        if (state != null && !state.maidUuid.equals(maidUuid)) {
            refund(player, state.maidUuid);
            state = null;
        }
        if (state == null) {
            state = new StageState(maidUuid);
            STAGED_BY_PLAYER.put(player.getUUID(), state);
        }

        StageEntry existing = state.find(template);
        if (existing == null && state.entries.size() >= MAX_DISTINCT) {
            return 0;
        }

        int total = state.totalCount();
        if (total >= MAX_TOTAL) {
            return 0;
        }

        int cappedRequest = Math.min(requestCount, MAX_TOTAL - total);
        int extracted = player.getAbilities().instabuild
                ? cappedRequest
                : removeMatchingFromInventory(player.getInventory(), template, cappedRequest);
        if (extracted <= 0) {
            return 0;
        }

        if (existing == null) {
            ItemStack one = template.copy();
            one.setCount(1);
            state.entries.add(new StageEntry(one, extracted));
        } else {
            existing.count += extracted;
        }
        return extracted;
    }

    static int refund(ServerPlayer player, UUID maidUuid) {
        if (player == null || maidUuid == null) {
            return 0;
        }
        StageState state = STAGED_BY_PLAYER.get(player.getUUID());
        if (state == null || !state.maidUuid.equals(maidUuid)) {
            return 0;
        }

        int returned = 0;
        if (!player.getAbilities().instabuild) {
            for (StageEntry entry : state.entries) {
                ItemStack back = entry.stack.copy();
                back.setCount(entry.count);
                returned += back.getCount();
                ItemHandlerHelper.giveItemToPlayer(player, back);
            }
        }

        STAGED_BY_PLAYER.remove(player.getUUID());
        return returned;
    }

    static List<ItemStack> pop(ServerPlayer player, UUID maidUuid) {
        if (player == null || maidUuid == null) {
            return List.of();
        }
        StageState state = STAGED_BY_PLAYER.get(player.getUUID());
        if (state == null || !state.maidUuid.equals(maidUuid)) {
            return List.of();
        }

        ArrayList<ItemStack> result = new ArrayList<>(state.entries.size());
        for (StageEntry entry : state.entries) {
            ItemStack stack = entry.stack.copy();
            stack.setCount(entry.count);
            result.add(stack);
        }
        STAGED_BY_PLAYER.remove(player.getUUID());
        return result;
    }

    private static int removeMatchingFromInventory(Inventory inventory, ItemStack template, int amount) {
        int remaining = amount;
        int removed = 0;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (remaining <= 0) {
                break;
            }
            ItemStack slotStack = inventory.getItem(slot);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameTags(slotStack, template)) {
                continue;
            }
            int take = Math.min(remaining, slotStack.getCount());
            slotStack.shrink(take);
            if (slotStack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= take;
            removed += take;
        }

        if (removed > 0) {
            inventory.setChanged();
        }
        return removed;
    }

    private static final class StageState {
        private final UUID maidUuid;
        private final List<StageEntry> entries = new ArrayList<>();

        private StageState(UUID maidUuid) {
            this.maidUuid = maidUuid;
        }

        private int totalCount() {
            int total = 0;
            for (StageEntry entry : entries) {
                total += entry.count;
            }
            return total;
        }

        private StageEntry find(ItemStack target) {
            for (StageEntry entry : entries) {
                if (ItemStack.isSameItemSameTags(entry.stack, target)) {
                    return entry;
                }
            }
            return null;
        }
    }

    private static final class StageEntry {
        private final ItemStack stack;
        private int count;

        private StageEntry(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }
}
