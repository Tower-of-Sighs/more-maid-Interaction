package cc.sighs.more_maid_interaction.dsl.engine;

import cc.sighs.more_maid_interaction.core.InteractionEngine;
import cc.sighs.more_maid_interaction.core.Personality;
import cc.sighs.more_maid_interaction.core.Stats;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class MaidscriptEngineAdapterTest {
    @Test
    public void positiveHistoryBuffersAttackPenalty() {
        String script = """
                on_event "builtin.player.knee_pillow" {
                  let gain = 0.06 + state.bond * 0.02
                  apply_delta { favor: gain, bond: gain * 0.7, sincerity: gain * 0.4 }
                  memory.add("episode.knee_pillow")
                  say("knee")
                }

                on_event "builtin.player.gift_flower" {
                  let cnt = memory.count("gift.flower", 72000)
                  let penalty = clamp(cnt / 5.0, 0.0, 0.6)
                  apply_delta { favor: 0.05 - penalty * 0.03, bond: 0.03, novelty: 0.02 }
                  memory.add("gift.flower")
                  say("flower")
                }

                on_event "builtin.player.attack_maid" {
                  let positive = clamp(
                    memory.count("episode.knee_pillow", 72000) * 0.15 +
                    memory.count("gift.flower", 72000) * 0.10,
                    0.0,
                    0.8
                  )
                  let punishment = (0.16 + event.intensity * 0.12) * (1.0 - positive)
                  apply_delta {
                    favor: -punishment
                    bond: -punishment * 0.9
                    sincerity: -punishment * 0.7
                  }
                  if punishment > 0.20 {
                    say("hurt")
                  } else {
                    say("forgive")
                  }
                  memory.add("conflict.attack")
                }
                """;

        MaidscriptScriptRegistry registry = MaidscriptScriptRegistry.fromSource("global.ms", script);

        InteractionEngine engineWithHistory = new InteractionEngine(Personality.neutral(), new Stats(0.35, 0.25, 0.45, 0.2));
        MaidscriptEngineAdapter adapterWithHistory = new MaidscriptEngineAdapter(engineWithHistory, registry);
        adapterWithHistory.handle(new BuiltinEventEnvelope("builtin.player.knee_pillow", 0.7, Map.of(), Map.of(), null));
        adapterWithHistory.handle(new BuiltinEventEnvelope("builtin.player.gift_flower", 0.7, Map.of(), Map.of(), null));
        MaidscriptEngineAdapter.ScriptedResult attackWithHistory = adapterWithHistory.handle(
                new BuiltinEventEnvelope("builtin.player.attack_maid", 0.8, Map.of("damage", 8.0), Map.of(), null)
        );

        InteractionEngine engineNoHistory = new InteractionEngine(Personality.neutral(), new Stats(0.35, 0.25, 0.45, 0.2));
        MaidscriptEngineAdapter adapterNoHistory = new MaidscriptEngineAdapter(engineNoHistory, registry);
        MaidscriptEngineAdapter.ScriptedResult attackNoHistory = adapterNoHistory.handle(
                new BuiltinEventEnvelope("builtin.player.attack_maid", 0.8, Map.of("damage", 8.0), Map.of(), null)
        );

        double lossWithHistory = attackWithHistory.engineResult().before().favor() - attackWithHistory.engineResult().after().favor();
        double lossNoHistory = attackNoHistory.engineResult().before().favor() - attackNoHistory.engineResult().after().favor();

        Assertions.assertTrue(lossNoHistory > lossWithHistory, "positive history should reduce punishment");

        Object lineWithHistory = attackWithHistory.scriptResult().actions().get(0).payload().get("text");
        Object lineNoHistory = attackNoHistory.scriptResult().actions().get(0).payload().get("text");
        Assertions.assertEquals("forgive", lineWithHistory);
        Assertions.assertEquals("hurt", lineNoHistory);
    }

    @Test
    public void cooldownAndStopWorkAcrossEvents() {
        String script = """
                on_event "builtin.player.knee_pillow" {
                  if !cooldown_ready("knee", 1200) {
                    say("cd")
                    stop
                  }
                  say("ok")
                }
                """;

        MaidscriptScriptRegistry registry = MaidscriptScriptRegistry.fromSource("cooldown.ms", script);
        InteractionEngine engine = new InteractionEngine(Personality.neutral(), Stats.zero());
        MaidscriptEngineAdapter adapter = new MaidscriptEngineAdapter(engine, registry);

        MaidscriptEngineAdapter.ScriptedResult first = adapter.handle(BuiltinEventEnvelope.of("builtin.player.knee_pillow"));
        MaidscriptEngineAdapter.ScriptedResult second = adapter.handle(BuiltinEventEnvelope.of("builtin.player.knee_pillow"));

        Assertions.assertEquals("ok", first.scriptResult().actions().get(0).payload().get("text"));
        Assertions.assertTrue(second.scriptResult().stopped());
        Assertions.assertEquals("cd", second.scriptResult().actions().get(0).payload().get("text"));
    }
}
