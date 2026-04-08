package cc.sighs.more_maid_interaction.mc.client;

import cc.sighs.more_maid_interaction.MoreMaidInteraction;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MoreMaidInteraction.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientKeyMappings {
    public static final String KEY_CATEGORY = "key.categories.more_maid_interaction";

    public static final KeyMapping OPEN_INTERACTION_PANEL = new KeyMapping(
            "key.more_maid_interaction.open_interaction",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY
    );

    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_INTERACTION_PANEL);
    }
}
