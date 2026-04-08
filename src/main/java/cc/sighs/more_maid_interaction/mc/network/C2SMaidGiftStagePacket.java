package cc.sighs.more_maid_interaction.mc.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class C2SMaidGiftStagePacket {
    private static final int MAX_STAGE_COUNT = 4096;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 36.0D;

    private final UUID maidUuid;
    private final int mode;
    private final ItemStack item;
    private final int count;

    public C2SMaidGiftStagePacket(UUID maidUuid, int mode, ItemStack item, int count) {
        this.maidUuid = maidUuid;
        this.mode = mode;
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        this.count = count;
    }

    public static C2SMaidGiftStagePacket add(UUID maidUuid, ItemStack item, int count) {
        return new C2SMaidGiftStagePacket(maidUuid, 0, item, count);
    }

    public static C2SMaidGiftStagePacket clear(UUID maidUuid) {
        return new C2SMaidGiftStagePacket(maidUuid, 1, ItemStack.EMPTY, 0);
    }

    public static void encode(C2SMaidGiftStagePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.maidUuid);
        buf.writeVarInt(packet.mode);
        buf.writeItem(packet.item);
        buf.writeVarInt(Math.max(0, Math.min(packet.count, MAX_STAGE_COUNT)));
    }

    public static C2SMaidGiftStagePacket decode(FriendlyByteBuf buf) {
        UUID maidUuid = buf.readUUID();
        int mode = buf.readVarInt();
        ItemStack item = buf.readItem();
        int count = Math.max(0, Math.min(buf.readVarInt(), MAX_STAGE_COUNT));
        return new C2SMaidGiftStagePacket(maidUuid, mode, item, count);
    }

    public static void handle(C2SMaidGiftStagePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context.getSender(), packet));
        context.setPacketHandled(true);
    }

    private static void handleServer(ServerPlayer player, C2SMaidGiftStagePacket packet) {
        if (player == null || packet == null || packet.maidUuid == null) {
            return;
        }

        Entity targetEntity = player.serverLevel().getEntity(packet.maidUuid);
        if (!(targetEntity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }

        if (!maid.isOwnedBy(player)) {
            return;
        }

        if (player.distanceToSqr(maid) > MAX_INTERACTION_DISTANCE_SQR) {
            return;
        }

        if (packet.mode == 1) {
            GiftStagingManager.refund(player, packet.maidUuid);
            return;
        }

        if (packet.mode != 0 || packet.count <= 0 || packet.item.isEmpty()) {
            return;
        }

        ItemStack template = packet.item.copy();
        template.setCount(1);
        GiftStagingManager.stageAdd(player, packet.maidUuid, template, packet.count);
    }
}
