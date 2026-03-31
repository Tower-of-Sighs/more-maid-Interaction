package cc.sighs.more_maid_interaction.dsl.engine;

import cc.sighs.more_maid_interaction.core.EmotionState;
import cc.sighs.more_maid_interaction.core.InteractionEngine;
import cc.sighs.more_maid_interaction.core.MoodModel;
import cc.sighs.more_maid_interaction.core.Personality;
import cc.sighs.more_maid_interaction.core.SocialContext;
import cc.sighs.more_maid_interaction.core.Stats;
import cc.sighs.more_maid_interaction.testing.EmotionCurveCsv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

public class MaidscriptCurveExportTest {
    private static final String SCRIPT = """
            on_event "builtin.player.knee_pillow" {
              if !cooldown_ready("knee_pillow", 160) {
                say("rest")
                stop
              }
              let trust = state.bond * 0.6 + state.sincerity * 0.4
              let gain = clamp(0.02 + trust * 0.06, 0.02, 0.09)
              apply_delta { favor: gain, bond: gain * 0.8, sincerity: gain * 0.45, novelty: 0.015 }
              mood_bias { affectionate: 0.12 }
              memory.add("episode.knee_pillow")
              say("knee")
            }

            on_event "builtin.player.gift_flower" {
              let cnt = memory.count("gift.flower", 7200)
              let decay = clamp(cnt / 8.0, 0.0, 0.8)
              apply_delta { favor: 0.05 - decay * 0.03, bond: 0.025, novelty: 0.02 - decay * 0.01 }
              mood_bias { calm: 0.05, affectionate: 0.03 }
              memory.add("gift.flower")
              say("flower")
            }

            on_event "builtin.player.attack_maid" {
              let positive = clamp(
                memory.count("episode.knee_pillow", 7200) * 0.09 +
                memory.count("gift.flower", 7200) * 0.06,
                0.0, 0.7
              )
              let punish = (0.06 + event.intensity * 0.20) * (1.0 - positive)
              apply_delta {
                favor: -punish
                bond: -punish * 0.8
                sincerity: -punish * 0.6
                novelty: -0.01
              }
              mood_bias { annoyed: 0.30, jealous: 0.14, calm: -0.08 }
              memory.add("conflict.attack")
              say("hurt")
            }

            on_event "builtin.maid.sleep_start" {
              apply_delta { sincerity: 0.02, novelty: -0.01, bond: 0.01 }
              mood_bias { calm: 0.10, annoyed: -0.06 }
              say("sleep")
            }

            on_event "builtin.player.chat_to_maid" {
              apply_delta { bond: 0.006, sincerity: 0.004 }
              mood_bias { calm: 0.03 }
              say("chat")
            }
            """;

    @Test
    public void exportMaidscriptArcCurve() {
        InteractionEngine engine = new InteractionEngine(Personality.neutral(), new Stats(0.20, 0.18, 0.52, 0.24));
        MaidscriptEngineAdapter adapter = newAdapter(engine);

        StringBuilder csv = new StringBuilder(EmotionCurveCsv.header());
        int step = 0;

        for (int i = 0; i < 100; i++) {
            BuiltinEventEnvelope e = switch (i % 3) {
                case 0 -> envelope(BuiltinEventIds.PLAYER_KNEE_PILLOW, 0.70, SocialContext.empty(), Map.of());
                case 1 -> envelope(BuiltinEventIds.PLAYER_GIFT_FLOWER, 0.65, SocialContext.empty(), Map.of("item_id", "minecraft:poppy"));
                default -> envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.40, SocialContext.empty(), Map.of("chat_text", "hello"));
            };
            MaidscriptEngineAdapter.ScriptedResult r = adapter.handle(e);
            EmotionCurveCsv.appendRow(
                    csv, ++step, "ms_arc", "warmup", e.eventId(), e.intensity(), e.socialContext(), engine, r.engineResult().score(),
                    firstSayOrEmpty(r)
            );
        }
        EmotionState warmEmotion = engine.emotion(engine.stats());
        double warmNeg = moodProb(engine, MoodModel.Mood.JEALOUS) + moodProb(engine, MoodModel.Mood.ANNOYED);

        for (int i = 0; i < 70; i++) {
            BuiltinEventEnvelope e = (i % 4 == 0)
                    ? envelope(BuiltinEventIds.PLAYER_GIFT_FLOWER, 0.65, new SocialContext(2, 0.60, 0.60), Map.of())
                    : envelope(BuiltinEventIds.PLAYER_ATTACK_MAID, 0.90, new SocialContext(3, 0.90, 0.85), Map.of("damage", 10.0));
            MaidscriptEngineAdapter.ScriptedResult r = adapter.handle(e);
            EmotionCurveCsv.appendRow(
                    csv, ++step, "ms_arc", "conflict", e.eventId(), e.intensity(), e.socialContext(), engine, r.engineResult().score(),
                    firstSayOrEmpty(r)
            );
        }
        EmotionState conflictEmotion = engine.emotion(engine.stats());
        double conflictNeg = moodProb(engine, MoodModel.Mood.JEALOUS) + moodProb(engine, MoodModel.Mood.ANNOYED);

        for (int i = 0; i < 220; i++) {
            BuiltinEventEnvelope e;
            if (i % 7 == 0) {
                e = envelope(BuiltinEventIds.MAID_SLEEP_START, 0.50, SocialContext.empty(), Map.of());
            } else if (i % 2 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_GIFT_FLOWER, 0.62, SocialContext.empty(), Map.of("item_id", "minecraft:dandelion"));
            } else {
                e = envelope(BuiltinEventIds.PLAYER_KNEE_PILLOW, 0.72, SocialContext.empty(), Map.of());
            }
            MaidscriptEngineAdapter.ScriptedResult r = adapter.handle(e);
            EmotionCurveCsv.appendRow(
                    csv, ++step, "ms_arc", "repair", e.eventId(), e.intensity(), e.socialContext(), engine, r.engineResult().score(),
                    firstSayOrEmpty(r)
            );
        }
        EmotionState finalEmotion = engine.emotion(engine.stats());
        double finalNeg = moodProb(engine, MoodModel.Mood.JEALOUS) + moodProb(engine, MoodModel.Mood.ANNOYED);

        Path out = EmotionCurveCsv.write("ms_arc_curve.csv", csv.toString());
        Assertions.assertTrue(out.toFile().exists());
        Assertions.assertTrue(out.toFile().length() > 1024);
        Assertions.assertTrue(conflictEmotion.pleasure() < warmEmotion.pleasure() - 0.10);
        Assertions.assertTrue(conflictNeg > warmNeg + 0.12);
        Assertions.assertTrue(finalEmotion.pleasure() > conflictEmotion.pleasure() + 0.08);
        Assertions.assertTrue(finalNeg < conflictNeg - 0.08);
    }

    @Test
    public void exportMaidscriptAutomationCurve() {
        InteractionEngine engine = new InteractionEngine(Personality.neutral(), new Stats(0.20, 0.18, 0.52, 0.24));
        MaidscriptEngineAdapter adapter = newAdapter(engine);
        Random rng = new Random(2026033177L);
        StringBuilder csv = new StringBuilder(EmotionCurveCsv.header());

        String[] pool = {
                BuiltinEventIds.PLAYER_KNEE_PILLOW,
                BuiltinEventIds.PLAYER_GIFT_FLOWER,
                BuiltinEventIds.PLAYER_CHAT_TO_MAID,
                BuiltinEventIds.MAID_SLEEP_START,
                BuiltinEventIds.PLAYER_ATTACK_MAID
        };
        double[] base = {0.20, 0.24, 0.26, 0.14, 0.16};

        int moodSwitch = 0;
        MoodModel.Mood lastMood = engine.mood().topMood();

        for (int t = 0; t < 5000; t++) {
            double[] weights = adaptive(base, rng, t);
            String eventId = pickWeighted(pool, weights, rng);
            SocialContext ctx = (BuiltinEventIds.PLAYER_ATTACK_MAID.equals(eventId))
                    ? new SocialContext(2 + rng.nextInt(2), 0.60 + rng.nextDouble() * 0.35, 0.50 + rng.nextDouble() * 0.35)
                    : new SocialContext(rng.nextInt(2), 0.05 + rng.nextDouble() * 0.20, 0.15 + rng.nextDouble() * 0.25);
            double intensity = BuiltinEventIds.PLAYER_ATTACK_MAID.equals(eventId)
                    ? 0.80 + rng.nextDouble() * 0.18
                    : 0.40 + rng.nextDouble() * 0.35;
            BuiltinEventEnvelope envelope = envelope(eventId, intensity, ctx, Map.of());
            MaidscriptEngineAdapter.ScriptedResult r = adapter.handle(envelope);

            EmotionCurveCsv.appendRow(
                    csv, t + 1, "ms_auto_5000", autoPhase(t), eventId, intensity, ctx, engine, r.engineResult().score(),
                    firstSayOrEmpty(r)
            );

            MoodModel.Mood now = engine.mood().topMood();
            if (now != lastMood) {
                moodSwitch++;
                lastMood = now;
            }
        }

        Path out = EmotionCurveCsv.write("ms_auto_5000.csv", csv.toString());
        Assertions.assertTrue(out.toFile().exists());
        Assertions.assertTrue(out.toFile().length() > 1024);
        Assertions.assertTrue(moodSwitch > 60);
    }

    private static MaidscriptEngineAdapter newAdapter(InteractionEngine engine) {
        MaidscriptScriptRegistry registry = MaidscriptScriptRegistry.fromSource("ms_curve.ms", SCRIPT);
        return new MaidscriptEngineAdapter(engine, registry, new ScriptMemoryStore(1200, 120), 3000);
    }

    private static BuiltinEventEnvelope envelope(String eventId, double intensity, SocialContext social, Map<String, Object> payload) {
        return new BuiltinEventEnvelope(eventId, intensity, payload, Map.of("source", "test"), social);
    }

    private static double moodProb(InteractionEngine engine, MoodModel.Mood mood) {
        return engine.mood().distribution().getOrDefault(mood, 0.0);
    }

    private static String firstSayOrEmpty(MaidscriptEngineAdapter.ScriptedResult r) {
        if (r.scriptResult().actions().isEmpty()) {
            return "";
        }
        Object t = r.scriptResult().actions().get(0).payload().get("text");
        return t == null ? "" : String.valueOf(t);
    }

    private static String autoPhase(int tick) {
        if (tick % 1000 < 180) {
            return "pressure";
        }
        if (tick % 1300 > 950) {
            return "repair_window";
        }
        return "normal";
    }

    private static double[] adaptive(double[] base, Random rng, int tick) {
        double[] w = base.clone();
        if (tick % 1000 < 180) {
            w[4] += 0.14;
            w[1] -= 0.04;
        } else if (tick % 1300 > 950) {
            w[0] += 0.06;
            w[1] += 0.06;
            w[3] += 0.02;
            w[4] -= 0.05;
        }
        for (int i = 0; i < w.length; i++) {
            w[i] = Math.max(0.001, w[i] + (rng.nextDouble() - 0.5) * 0.02);
        }
        return w;
    }

    private static String pickWeighted(String[] events, double[] weights, Random rng) {
        double sum = 0;
        for (double w : weights) sum += Math.max(0, w);
        if (sum <= 0) return events[0];
        double r = rng.nextDouble() * sum;
        double c = 0;
        for (int i = 0; i < weights.length; i++) {
            c += Math.max(0, weights[i]);
            if (r <= c) return events[i];
        }
        return events[events.length - 1];
    }
}
