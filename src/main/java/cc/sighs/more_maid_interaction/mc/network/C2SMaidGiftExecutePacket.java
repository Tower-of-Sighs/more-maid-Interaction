package cc.sighs.more_maid_interaction.mc.network;

import cc.sighs.more_maid_interaction.MoreMaidInteraction;
import cc.sighs.more_maid_interaction.dsl.runtime.ReactionAction;
import cc.sighs.more_maid_interaction.mc.ai.MaidAiEventService;
import cc.sighs.more_maid_interaction.mc.dsl.MaidScriptEventService;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class C2SMaidGiftExecutePacket {
    private static final int MAX_ENTRY_COUNT = 48;
    private static final int MAX_ENTRY_STACK = 4096;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 36.0D;

    private final UUID maidUuid;
    private final List<ItemStack> gifts;

    public C2SMaidGiftExecutePacket(UUID maidUuid, List<ItemStack> gifts) {
        this.maidUuid = maidUuid;
        this.gifts = gifts == null ? List.of() : List.copyOf(gifts);
    }

    public static void encode(C2SMaidGiftExecutePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.maidUuid);
        int size = Math.min(packet.gifts.size(), MAX_ENTRY_COUNT);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = packet.gifts.get(i);
            if (stack == null || stack.isEmpty()) {
                buf.writeItem(ItemStack.EMPTY);
                continue;
            }
            ItemStack copy = stack.copy();
            copy.setCount(Math.max(1, Math.min(copy.getCount(), MAX_ENTRY_STACK)));
            buf.writeItem(copy);
        }
    }

    public static C2SMaidGiftExecutePacket decode(FriendlyByteBuf buf) {
        UUID maidUuid = buf.readUUID();
        int size = Math.max(0, Math.min(buf.readVarInt(), MAX_ENTRY_COUNT));
        List<ItemStack> gifts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = buf.readItem();
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, Math.min(stack.getCount(), MAX_ENTRY_STACK)));
                gifts.add(stack);
            }
        }
        return new C2SMaidGiftExecutePacket(maidUuid, gifts);
    }

    public static void handle(C2SMaidGiftExecutePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context.getSender(), packet.maidUuid, packet.gifts));
        context.setPacketHandled(true);
    }

    private static void handleServer(ServerPlayer player, UUID maidUuid, List<ItemStack> requestedGifts) {
        if (player == null || maidUuid == null) {
            return;
        }

        Entity targetEntity = player.serverLevel().getEntity(maidUuid);
        if (!(targetEntity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }

        if (!maid.isOwnedBy(player)) {
            player.displayClientMessage(Component.translatable("message.more_maid_interaction.not_owner"), true);
            return;
        }

        if (player.distanceToSqr(maid) > MAX_INTERACTION_DISTANCE_SQR) {
            player.displayClientMessage(Component.translatable("message.more_maid_interaction.too_far"), true);
            return;
        }

        List<ItemStack> stagedGifts = GiftStagingManager.pop(player, maidUuid);
        List<ItemStack> effectiveGifts = stagedGifts.isEmpty() ? requestedGifts : stagedGifts;
        boolean consumeFromInventory = stagedGifts.isEmpty();

        GiftDeliverySummary summary = deliverGiftItems(player, maid, effectiveGifts, consumeFromInventory);
        if (summary.totalDelivered <= 0) {
            player.displayClientMessage(Component.translatable("message.more_maid_interaction.gift_execute_failed"), true);
            return;
        }

        Map<String, Object> payload = buildGiftPayload(summary);

        // 1) 先走可控的脚本（确定性逻辑 + 可调数值）
        var scripted = MaidScriptEventService.handleGift(maid.getUUID(), payload);
        if (scripted != null) {
            for (ReactionAction action : scripted.scriptResult().actions()) {
                if (!"say".equals(action.type()) && !"bubble".equals(action.type())) {
                    continue;
                }
                Object text = action.payload().get("text");
                if (text == null) {
                    continue;
                }
                player.displayClientMessage(
                        Component.translatable("message.more_maid_interaction.maid_line", maid.getName(), String.valueOf(text)),
                        false
                );
            }
        }

        // 2) 再走 AI 增强（复用车万女仆 AI 的 Site/Provider/Key；仅轻量微调 + 额外台词）
        MaidAiEventService.requestGiftReaction(maid, player, payload);

        player.displayClientMessage(
                Component.translatable("message.more_maid_interaction.gift_execute_ok", summary.totalDelivered, maid.getName()),
                true
        );

        MoreMaidInteraction.LOGGER.info(
                "Gift executed: player={}, maid={}, delivered={}, distinct={}, primary={} ",
                player.getGameProfile().getName(),
                maidUuid,
                summary.totalDelivered,
                summary.deliveredById.size(),
                summary.primaryItemId
        );
    }

    private static GiftDeliverySummary deliverGiftItems(ServerPlayer player, EntityMaid maid, List<ItemStack> requestedGifts, boolean consumeFromInventory) {
        Map<String, Integer> deliveredById = new LinkedHashMap<>();
        int totalDelivered = 0;
        double qualityScoreAccum = 0;

        for (ItemStack request : requestedGifts) {
            if (request == null || request.isEmpty()) {
                continue;
            }

            int requested = Math.max(1, Math.min(request.getCount(), MAX_ENTRY_STACK));
            ItemStack template = request.copy();
            template.setCount(1);

            int extracted = consumeFromInventory
                    ? (player.getAbilities().instabuild
                    ? requested
                    : removeMatchingFromInventory(player.getInventory(), template, requested))
                    : requested;
            if (extracted <= 0) {
                continue;
            }

            ItemStack transfer = template.copy();
            transfer.setCount(extracted);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), transfer, false);
            int delivered = extracted - remainder.getCount();
            if (delivered <= 0) {
                if (!player.getAbilities().instabuild) {
                    ItemHandlerHelper.giveItemToPlayer(player, transfer);
                }
                continue;
            }

            if (!remainder.isEmpty() && !player.getAbilities().instabuild) {
                ItemHandlerHelper.giveItemToPlayer(player, remainder);
            }

            ItemStack deliveredSample = template.copy();
            deliveredSample.setCount(delivered);
            String itemId = itemId(deliveredSample);
            deliveredById.put(itemId, deliveredById.getOrDefault(itemId, 0) + delivered);
            totalDelivered += delivered;
            qualityScoreAccum += estimateGiftValue(deliveredSample) * delivered;

            maid.spawnItemParticles(deliveredSample, Math.min(6, delivered));
        }

        String primaryItemId = "";
        int primaryCount = 0;
        for (Map.Entry<String, Integer> entry : deliveredById.entrySet()) {
            if (entry.getValue() > primaryCount) {
                primaryItemId = entry.getKey();
                primaryCount = entry.getValue();
            }
        }

        double qualityScore = totalDelivered <= 0 ? 0 : qualityScoreAccum / totalDelivered;
        return new GiftDeliverySummary(deliveredById, totalDelivered, primaryItemId, primaryCount, qualityScore);
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

    private static String itemId(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "minecraft:air" : key.toString();
    }

    private static double estimateGiftValue(ItemStack stack) {
        String id = itemId(stack).toLowerCase(Locale.ROOT);
        if (id.contains("nether_star") || id.contains("dragon_egg")) return 1.40;
        if (id.contains("netherite") || id.contains("enchanted_golden_apple")) return 1.25;
        if (id.contains("diamond") || id.contains("emerald")) return 1.05;
        if (id.contains("poppy") || id.contains("dandelion") || id.contains("tulip") || id.contains("orchid")) return 0.78;
        if (id.contains("cake") || id.contains("cookie") || id.contains("golden_apple")) return 0.82;
        if (id.contains("rotten_flesh") || id.contains("poisonous_potato")) return 0.15;
        return 0.55;
    }

    private static Map<String, Object> buildGiftPayload(GiftDeliverySummary summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("item_id", summary.primaryItemId);
        payload.put("item_count", summary.primaryItemCount);
        payload.put("total_count", summary.totalDelivered);
        payload.put("distinct_count", summary.deliveredById.size());
        payload.put("quality_score", summary.qualityScore);
        payload.put("items_csv", joinItems(summary.deliveredById));
        payload.put("rare", summary.qualityScore >= 0.95);
        return payload;
    }

    private static String joinItems(Map<String, Integer> deliveredById) {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> entry : deliveredById.entrySet()) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('*').append(entry.getValue());
            i++;
        }
        return builder.toString();
    }

    private record GiftDeliverySummary(
            Map<String, Integer> deliveredById,
            int totalDelivered,
            String primaryItemId,
            int primaryItemCount,
            double qualityScore
    ) {
    }
}