package cc.sighs.more_maid_interaction.dsl;

import cc.sighs.more_maid_interaction.dsl.runtime.ExecutionResult;
import cc.sighs.more_maid_interaction.dsl.runtime.MaidscriptRuntime;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeCallable;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeInput;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeServices;
import cc.sighs.more_maid_interaction.dsl.runtime.ScriptRuntimeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class RuntimeTest {
    @Test
    public void executeEventAndCollectEffects() {
        String src = """
                on_event "builtin.player.knee_pillow" {
                  if !cooldown_ready("knee_pillow", 1200) {
                    say("cooldown")
                    stop
                  }

                  let gain = clamp(0.03 + state.bond * 0.1, 0.0, 0.2)
                  apply_delta { favor: gain, bond: gain * 0.5 }
                  mood_bias { affectionate: 0.15 }
                  memory.add("episode.knee_pillow")
                  story.set_flag("KNEE_PILLOW_UNLOCKED")
                  say("ok")
                }
                """;

        ToggleCooldownServices services = new ToggleCooldownServices();
        MaidscriptRuntime runtime = MaidscriptRuntime.fromSource(src);

        RuntimeInput input = new RuntimeInput(
                Map.of("bond", 0.5),
                Map.of(),
                "CALM",
                Map.of("CALM", 0.8),
                Map.of(),
                new RuntimeInput.EventData("builtin.player.knee_pillow", 0.7, Map.of()),
                Map.of(),
                services,
                2000
        );

        ExecutionResult first = runtime.execute("builtin.player.knee_pillow", input);
        Assertions.assertTrue(first.delta().getOrDefault("favor", 0.0) > 0);
        Assertions.assertEquals(1, first.memoryAdds().size());
        Assertions.assertTrue(first.storyFlagsSet().contains("KNEE_PILLOW_UNLOCKED"));
        Assertions.assertEquals("say", first.actions().get(first.actions().size() - 1).type());
        Assertions.assertEquals("ok", first.actions().get(first.actions().size() - 1).payload().get("text"));

        ExecutionResult second = runtime.execute("builtin.player.knee_pillow", input);
        Assertions.assertTrue(second.stopped());
        Assertions.assertEquals(0.0, second.delta().getOrDefault("favor", 0.0));
        Assertions.assertEquals("cooldown", second.actions().get(0).payload().get("text"));
    }

    @Test
    public void userFunctionWorks() {
        String src = """
                fn pick(soft, cold) {
                  if mood.top == "CALM" {
                    return soft
                  } else {
                    return cold
                  }
                }

                on_event "builtin.player.chat_to_maid" {
                  let line = pick("hello", "...")
                  say(line)
                }
                """;

        MaidscriptRuntime runtime = MaidscriptRuntime.fromSource(src);
        RuntimeInput input = RuntimeInput.empty("builtin.player.chat_to_maid");
        ExecutionResult result = runtime.execute("builtin.player.chat_to_maid", input);
        Assertions.assertEquals("hello", result.actions().get(0).payload().get("text"));
    }

    @Test
    public void stepLimitProtectsRuntime() {
        String src = """
                fn loop() {
                  loop()
                }

                on_event "builtin.player.chat_to_maid" {
                  loop()
                }
                """;

        MaidscriptRuntime runtime = MaidscriptRuntime.fromSource(src);
        RuntimeInput input = new RuntimeInput(
                Map.of(),
                Map.of(),
                "CALM",
                Map.of(),
                Map.of(),
                new RuntimeInput.EventData("builtin.player.chat_to_maid", 0, Map.of()),
                Map.of(),
                RuntimeServices.NOOP,
                64
        );

        Assertions.assertThrows(ScriptRuntimeException.class, () -> runtime.execute("builtin.player.chat_to_maid", input));
    }

    @Test
    public void giftPayloadItemHelpersWork() {
        String src = """
                on_event "builtin.player.gift_flower" {
                  if item.is("minecraft:diamond") {
                    say("single")
                  }
                  if item.has("minecraft:poppy") {
                    say("has_flower")
                  }
                }
                """;

        Map<String, Object> item = new HashMap<>();
        item.put("id", "minecraft:diamond");
        item.put("count", 2);
        item.put("is", (RuntimeCallable) args -> "minecraft:diamond".equals(String.valueOf(args.get(0))));
        item.put("has", (RuntimeCallable) args -> "minecraft:poppy".equals(String.valueOf(args.get(0))));

        MaidscriptRuntime runtime = MaidscriptRuntime.fromSource(src);
        RuntimeInput input = new RuntimeInput(
                Map.of(),
                Map.of(),
                "CALM",
                Map.of("CALM", 1.0),
                Map.of(),
                new RuntimeInput.EventData(
                        "builtin.player.gift_flower",
                        0.6,
                        Map.of("item_id", "minecraft:diamond", "item_count", 2)
                ),
                Map.of("item", item),
                RuntimeServices.NOOP,
                3000
        );

        ExecutionResult result = runtime.execute("builtin.player.gift_flower", input);
        Assertions.assertEquals(2, result.actions().size());
        Assertions.assertEquals("single", result.actions().get(0).payload().get("text"));
        Assertions.assertEquals("has_flower", result.actions().get(1).payload().get("text"));
    }

    @Test
    public void reassignmentWorks() {
        String src = """
                on_event "builtin.player.gift_flower" {
                  let gain = 1
                  gain = gain + 2
                  if gain == 3 {
                    say("ok")
                  }
                }
                """;

        MaidscriptRuntime runtime = MaidscriptRuntime.fromSource(src);
        ExecutionResult result = runtime.execute("builtin.player.gift_flower", RuntimeInput.empty("builtin.player.gift_flower"));
        Assertions.assertEquals("ok", result.actions().get(0).payload().get("text"));
    }

    private static final class ToggleCooldownServices implements RuntimeServices {
        private final Map<String, Boolean> used = new HashMap<>();

        @Override
        public boolean cooldownReady(String key, int ticks) {
            if (used.containsKey(key)) {
                return false;
            }
            used.put(key, true);
            return true;
        }
    }
}