package cc.sighs.more_maid_interaction.mc.network;

import cc.sighs.more_maid_interaction.MoreMaidInteraction;
import cc.sighs.more_maid_interaction.mc.interaction.BuiltinMaidInteractionEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class C2SMaidInteractionEventPacket {
    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 36.0D;

    private final UUID maidUuid;
    private final String eventId;

    public C2SMaidInteractionEventPacket(UUID maidUuid, String eventId) {
        this.maidUuid = maidUuid;
        this.eventId = eventId;
    }

    public static void encode(C2SMaidInteractionEventPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.maidUuid);
        buf.writeUtf(packet.eventId, MAX_EVENT_ID_LENGTH);
    }

    public static C2SMaidInteractionEventPacket decode(FriendlyByteBuf buf) {
        return new C2SMaidInteractionEventPacket(buf.readUUID(), buf.readUtf(MAX_EVENT_ID_LENGTH));
    }

    public static void handle(C2SMaidInteractionEventPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context.getSender(), packet.maidUuid, packet.eventId));
        context.setPacketHandled(true);
    }

    private static void handleServer(ServerPlayer player, UUID maidUuid, String eventId) {
        if (player == null || maidUuid == null || eventId == null || eventId.isBlank()) {
            return;
        }

        BuiltinMaidInteractionEvent interactionEvent = BuiltinMaidInteractionEvent.byEventId(eventId).orElse(null);
        if (interactionEvent == null) {
            MoreMaidInteraction.LOGGER.warn("Discarding unknown maid interaction event id: {}", eventId);
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

        MoreMaidInteraction.LOGGER.info(
                "Maid interaction triggered: player={}, maid={}, eventId={}",
                player.getGameProfile().getName(),
                maidUuid,
                interactionEvent.eventId()
        );

        player.displayClientMessage(
                Component.translatable(
                        "message.more_maid_interaction.event_sent",
                        Component.translatable(interactionEvent.displayNameKey()),
                        maid.getDisplayName()
                ),
                true
        );
    }
}
