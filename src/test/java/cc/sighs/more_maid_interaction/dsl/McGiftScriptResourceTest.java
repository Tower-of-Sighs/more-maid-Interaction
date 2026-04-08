package cc.sighs.more_maid_interaction.dsl;

import cc.sighs.more_maid_interaction.dsl.runtime.ExecutionResult;
import cc.sighs.more_maid_interaction.dsl.runtime.MaidscriptRuntime;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeCallable;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeInput;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeServices;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class McGiftScriptResourceTest {
    @Test
    public void resourceScriptParsesAndExecutes() throws Exception {
        String source;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("maidscript/mc_gift_demo.ms")) {
            Assertions.assertNotNull(stream, "missing script resource: maidscript/mc_gift_demo.ms");
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", "minecraft:poppy");
        item.put("count", 3);
        item.put("is", (RuntimeCallable) args -> "minecraft:poppy".equals(String.valueOf(args.get(0))));
        item.put("has", (RuntimeCallable) args -> "minecraft:poppy".equals(String.valueOf(args.get(0))));

        MaidscriptRuntime runtime = MaidscriptRuntime.fromSource(source);
        RuntimeInput input = new RuntimeInput(
                Map.of(),
                Map.of(),
                "CALM",
                Map.of("CALM", 1.0),
                Map.of(),
                new RuntimeInput.EventData(
                        "builtin.player.gift_flower",
                        0.7,
                        Map.of(
                                "item_id", "minecraft:poppy",
                                "item_count", 3,
                                "total_count", 3,
                                "distinct_count", 1,
                                "quality_score", 0.85,
                                "items_csv", "minecraft:poppy*3",
                                "rare", false
                        )
                ),
                Map.of("item", item),
                RuntimeServices.NOOP,
                5000
        );

        ExecutionResult result = runtime.execute("builtin.player.gift_flower", input);
        Assertions.assertFalse(result.actions().isEmpty(), "script should emit say/bubble actions");
        Assertions.assertTrue(result.delta().containsKey("favor"), "script should produce favor delta");
    }
}