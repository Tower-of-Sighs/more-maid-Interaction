package cc.sighs.more_maid_interaction.mc.network;

import cc.sighs.more_maid_interaction.MoreMaidInteraction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(MoreMaidInteraction.MODID, "network"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextPacketId(),
                C2SMaidInteractionEventPacket.class,
                C2SMaidInteractionEventPacket::encode,
                C2SMaidInteractionEventPacket::decode,
                C2SMaidInteractionEventPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId(),
                C2SMaidGiftStagePacket.class,
                C2SMaidGiftStagePacket::encode,
                C2SMaidGiftStagePacket::decode,
                C2SMaidGiftStagePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId(),
                C2SMaidGiftExecutePacket.class,
                C2SMaidGiftExecutePacket::encode,
                C2SMaidGiftExecutePacket::decode,
                C2SMaidGiftExecutePacket::handle
        );
    }

    private static int nextPacketId() {
        return packetId++;
    }
}
