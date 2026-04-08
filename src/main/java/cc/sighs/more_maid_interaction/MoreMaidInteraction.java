package cc.sighs.more_maid_interaction;

import cc.sighs.more_maid_interaction.mc.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MoreMaidInteraction.MODID)
public class MoreMaidInteraction {
    public static final String MODID = "more_maid_interaction";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MoreMaidInteraction() {
        ModNetwork.register();
    }
}
