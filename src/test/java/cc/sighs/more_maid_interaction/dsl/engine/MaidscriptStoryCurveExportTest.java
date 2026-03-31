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

public class MaidscriptStoryCurveExportTest {
    private static final String SCRIPT = """
            on_event "builtin.player.knee_pillow" {
              let trust = clamp(state.bond * 0.6 + state.sincerity * 0.4, 0.0, 1.0)
              let gain = clamp(0.02 + trust * 0.04, 0.02, 0.07)

              if story.has_flag("BREAKUP_DECLARED") {
                apply_delta { bond: -0.01, favor: -0.01, sincerity: 0.005, novelty: -0.005 }
                mood_bias { calm: 0.01, affectionate: -0.03 }
                say("我们还是先保持一点距离吧。")
                memory.add("story.after_breakup_distance")
                stop
              }

              apply_delta {
                favor: gain
                bond: gain * 0.9
                sincerity: gain * 0.4
                novelty: 0.015
              }
              mood_bias { affectionate: 0.10, calm: 0.03 }
              memory.add("story.warm_touch")
              say("靠在你身边的时候，我会觉得很安心。")
            }

            on_event "builtin.player.gift_flower" {
              let c = memory.count("gift.flower", 72000)
              let spam = clamp(c / 9.0, 0.0, 1.0)
              let decay = spam * 0.025

              let pressure = clamp(memory.count("story.money_pressure", 36000) * 0.02, 0.0, 0.08)
              let delta = clamp(0.04 - decay - pressure, -0.02, 0.05)

              apply_delta {
                favor: delta
                bond: delta * 0.6
                sincerity: 0.005
                novelty: 0.015 - decay
              }

              if story.has_flag("SUSPECT_CHEAT") {
                mood_bias { jealous: 0.08, calm: -0.04 }
                say("花很漂亮，但我更想听你说实话。")
              } else if story.has_flag("MONEY_PRESSURE") {
                mood_bias { calm: 0.02, affectionate: -0.01 }
                say("你不用靠礼物证明什么，我想要的是一起扛。")
              } else {
                mood_bias { affectionate: 0.06 }
                say("谢谢你，今天的心意我收到了。")
              }

              memory.add("gift.flower")
            }

            on_event "builtin.player.chat_to_maid" {
              let topic = event.payload.topic
              let signal = event.payload.signal
              let detail = event.payload.detail

              if contains(topic, "表白") {
                apply_delta { favor: 0.08, bond: 0.07, sincerity: 0.04, novelty: 0.02 }
                mood_bias { affectionate: 0.18, calm: 0.05 }
                memory.add("story.love_start")
                story.set_flag("LOVE_CONFIRMED")
                say("那我们就认真在一起吧。")
              } else if contains(topic, "失业") || contains(topic, "房租") || contains(topic, "赚钱") {
                let trust = clamp(state.sincerity * 0.5 + state.bond * 0.5, 0.0, 1.0)
                let pain = clamp(0.025 + (1.0 - trust) * 0.05 + event.intensity * 0.04, 0.03, 0.12)
                apply_delta {
                  favor: -pain * 0.9
                  bond: -pain * 0.7
                  sincerity: pain * 0.30
                  novelty: -0.01
                }
                mood_bias { calm: -0.03, affectionate: -0.04, annoyed: 0.05 }
                memory.add("story.money_pressure")
                story.set_flag("MONEY_PRESSURE")
                say("你说给不了我想要的生活，可我在意的不只是钱。")
              } else if contains(signal, "香水") || contains(signal, "暧昧") || contains(signal, "夜不归") {
                let suspicion = clamp(memory.count("story.suspicion", 48000) * 0.05, 0.0, 0.26)
                let hurt = clamp(0.03 + event.intensity * 0.05 + suspicion, 0.04, 0.19)
                apply_delta {
                  favor: -hurt
                  bond: -hurt * 0.85
                  sincerity: -hurt * 0.35
                  novelty: -0.02
                }
                mood_bias { jealous: 0.22, annoyed: 0.11, calm: -0.08 }
                memory.add("story.suspicion")
                story.set_flag("SUSPECT_CHEAT")
                say("这些细节让我越来越不安，你是不是瞒着我什么？")
              } else if contains(topic, "分手") {
                let attach = clamp(state.bond * 0.7 + memory.count("story.love_start", 72000) * 0.07, 0.0, 1.0)
                let shock = clamp(0.10 + event.intensity * 0.10 + attach * 0.08, 0.10, 0.30)
                apply_delta {
                  favor: -shock
                  bond: -shock * 0.92
                  sincerity: -shock * 0.45
                  novelty: -0.04
                }
                mood_bias { jealous: 0.18, annoyed: 0.20, affectionate: -0.10, calm: -0.10 }
                memory.add("story.breakup")
                story.set_flag("BREAKUP_DECLARED")
                say("你说我值得更好，可这句话比沉默更痛。")
              } else if story.has_flag("BREAKUP_DECLARED") {
                apply_delta { favor: -0.012, bond: -0.008, sincerity: 0.002, novelty: -0.004 }
                mood_bias { calm: 0.02, annoyed: 0.03, affectionate: -0.04 }
                say("先给彼此一点时间吧。")
              } else {
                apply_delta { bond: 0.007, sincerity: 0.004 }
                mood_bias { calm: 0.04 }
                say("嗯，我在听。")
              }
            }

            on_event "builtin.player.attack_maid" {
              let positive = clamp(
                memory.count("story.love_start", 72000) * 0.08 +
                memory.count("story.warm_touch", 72000) * 0.05,
                0.0,
                0.65
              )
              let punish = (0.06 + event.intensity * 0.16) * (1.0 - positive)
              apply_delta {
                favor: -punish
                bond: -punish * 0.75
                sincerity: -punish * 0.55
                novelty: -0.01
              }
              mood_bias { annoyed: 0.28, jealous: 0.10, calm: -0.08 }
              memory.add("story.attack")
              say("现在连你也要这样对我吗？")
            }

            on_event "builtin.maid.sleep_start" {
              apply_delta { sincerity: 0.02, novelty: -0.01, bond: 0.008 }
              mood_bias { calm: 0.12, annoyed: -0.08, jealous: -0.05 }
              say("今晚先休息吧，情绪再乱也要睡一觉。")
            }
            """;

    @Test
    public void exportDramaStoryCurveWithEventDetails() {
        InteractionEngine engine = new InteractionEngine(Personality.neutral(), new Stats(0.22, 0.20, 0.52, 0.26));
        MaidscriptEngineAdapter adapter = newAdapter(engine);

        StringBuilder csv = new StringBuilder(EmotionCurveCsv.header());
        int step = 0;

        EmotionState startEmotion = engine.emotion(engine.stats());

        step = runDatingStart(adapter, engine, csv, step);
        EmotionState datingEmotion = engine.emotion(engine.stats());
        double datingPressure = pressure(engine);

        step = runMoneyPressure(adapter, engine, csv, step);
        EmotionState moneyEmotion = engine.emotion(engine.stats());
        double moneyPressure = pressure(engine);

        step = runBetrayalHints(adapter, engine, csv, step);
        EmotionState betrayalEmotion = engine.emotion(engine.stats());
        double betrayalPressure = pressure(engine);

        step = runBreakup(adapter, engine, csv, step);
        EmotionState breakupEmotion = engine.emotion(engine.stats());
        double breakupPressure = pressure(engine);

        step = runAftershock(adapter, engine, csv, step);
        EmotionState finalEmotion = engine.emotion(engine.stats());
        double finalPressure = pressure(engine);

        Path out = EmotionCurveCsv.write("ms_story_breakup_betrayal_curve.csv", csv.toString());
        Assertions.assertTrue(out.toFile().exists());
        Assertions.assertTrue(out.toFile().length() > 2048);

        Assertions.assertTrue(datingEmotion.pleasure() > startEmotion.pleasure() + 0.10);
        Assertions.assertTrue(moneyEmotion.pleasure() < datingEmotion.pleasure() - 0.08);
        Assertions.assertTrue(betrayalPressure > moneyPressure + 0.08);
        Assertions.assertTrue(breakupPressure > moneyPressure + 0.25);
        Assertions.assertTrue(breakupEmotion.pleasure() <= betrayalEmotion.pleasure() + 0.05);
        Assertions.assertTrue(engine.stats().bond() < 0.55);
        Assertions.assertTrue(finalPressure < breakupPressure + 0.06);
        Assertions.assertTrue(finalEmotion.pleasure() < datingEmotion.pleasure() - 0.12);
    }

    private static int runDatingStart(MaidscriptEngineAdapter adapter, InteractionEngine engine, StringBuilder csv, int step) {
        BuiltinEventEnvelope confess = envelope(
                BuiltinEventIds.PLAYER_CHAT_TO_MAID,
                0.62,
                SocialContext.empty(),
                Map.of("topic", "表白", "detail", "想和你认真在一起")
        );
        step = append(adapter, engine, csv, step, "dating_start", confess, "剧情:表白确定关系");

        for (int i = 0; i < 56; i++) {
            BuiltinEventEnvelope e = switch (i % 4) {
                case 0 -> envelope(BuiltinEventIds.PLAYER_KNEE_PILLOW, 0.68, SocialContext.empty(),
                        Map.of("topic", "约会", "detail", "晚风很温柔"));
                case 1 -> envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.45, SocialContext.empty(),
                        Map.of("topic", "日常", "detail", "聊未来计划"));
                case 2 -> envelope(BuiltinEventIds.PLAYER_GIFT_FLOWER, 0.60, SocialContext.empty(),
                        Map.of("item_id", "minecraft:poppy", "detail", "路过花田摘的"));
                default -> envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.42, SocialContext.empty(),
                        Map.of("topic", "日常", "detail", "分享小事"));
            };
            step = append(adapter, engine, csv, step, "dating_start", e, "恋爱升温");
        }
        return step;
    }

    private static int runMoneyPressure(MaidscriptEngineAdapter adapter, InteractionEngine engine, StringBuilder csv, int step) {
        for (int i = 0; i < 48; i++) {
            BuiltinEventEnvelope e;
            if (i % 6 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.82, SocialContext.empty(),
                        Map.of("topic", "失业", "detail", "最近接不到单，怕给不了你想要的生活"));
            } else if (i % 5 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.74, SocialContext.empty(),
                        Map.of("topic", "房租", "detail", "下个月房租和生活费都紧张"));
            } else if (i % 7 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_GIFT_FLOWER, 0.40, SocialContext.empty(),
                        Map.of("item_id", "minecraft:dandelion", "detail", "便宜但还是想表达心意"));
            } else {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.58, SocialContext.empty(),
                        Map.of("topic", "赚钱", "detail", "想换工作却没有回应"));
            }
            step = append(adapter, engine, csv, step, "money_pressure", e, "经济压力累积");
        }
        return step;
    }

    private static int runBetrayalHints(MaidscriptEngineAdapter adapter, InteractionEngine engine, StringBuilder csv, int step) {
        for (int i = 0; i < 64; i++) {
            BuiltinEventEnvelope e;
            if (i % 3 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.84,
                        new SocialContext(2, 0.62, 0.58),
                        Map.of("topic", "对话", "signal", "陌生香水", "detail", "衣领上有不熟悉的香味"));
            } else if (i % 3 == 1) {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.86,
                        new SocialContext(3, 0.76, 0.64),
                        Map.of("topic", "对话", "signal", "暧昧消息", "detail", "手机弹出亲密备注"));
            } else if (i % 9 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_GIFT_FLOWER, 0.52,
                        new SocialContext(2, 0.55, 0.52),
                        Map.of("item_id", "minecraft:allium", "detail", "想掩饰争吵后的裂痕"));
            } else {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.80,
                        new SocialContext(2, 0.68, 0.60),
                        Map.of("topic", "对话", "signal", "夜不归", "detail", "解释越来越含糊"));
            }
            step = append(adapter, engine, csv, step, "betrayal_hint", e, "出轨疑云升级");
        }
        return step;
    }

    private static int runBreakup(MaidscriptEngineAdapter adapter, InteractionEngine engine, StringBuilder csv, int step) {
        BuiltinEventEnvelope breakup = envelope(
                BuiltinEventIds.PLAYER_CHAT_TO_MAID,
                0.95,
                new SocialContext(2, 0.72, 0.63),
                Map.of("topic", "分手", "detail", "你值得更好的生活，我给不了")
        );
        step = append(adapter, engine, csv, step, "breakup", breakup, "剧情:提出分手");

        for (int i = 0; i < 22; i++) {
            BuiltinEventEnvelope e;
            if (i % 6 == 0) {
                e = envelope(BuiltinEventIds.MAID_SLEEP_START, 0.45, SocialContext.empty(),
                        Map.of("detail", "先冷静一下"));
            } else if (i % 5 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.62,
                        new SocialContext(1, 0.42, 0.48),
                        Map.of("topic", "解释", "detail", "我不是不爱你，只是我配不上你"));
            } else {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.58,
                        new SocialContext(1, 0.38, 0.45),
                        Map.of("topic", "沉默", "detail", "消息越回越慢"));
            }
            step = append(adapter, engine, csv, step, "breakup", e, "分手冲击");
        }
        return step;
    }

    private static int runAftershock(MaidscriptEngineAdapter adapter, InteractionEngine engine, StringBuilder csv, int step) {
        for (int i = 0; i < 40; i++) {
            BuiltinEventEnvelope e;
            if (i % 4 == 0) {
                e = envelope(BuiltinEventIds.MAID_SLEEP_START, 0.48, SocialContext.empty(),
                        Map.of("detail", "尝试修复睡眠和节律"));
            } else if (i % 5 == 0) {
                e = envelope(BuiltinEventIds.PLAYER_GIFT_FLOWER, 0.46, SocialContext.empty(),
                        Map.of("item_id", "minecraft:blue_orchid", "detail", "迟到的道歉"));
            } else {
                e = envelope(BuiltinEventIds.PLAYER_CHAT_TO_MAID, 0.40, SocialContext.empty(),
                        Map.of("topic", "冷静", "detail", "给彼此时间"));
            }
            step = append(adapter, engine, csv, step, "aftershock", e, "余震阶段");
        }
        return step;
    }

    private static int append(
            MaidscriptEngineAdapter adapter,
            InteractionEngine engine,
            StringBuilder csv,
            int step,
            String phase,
            BuiltinEventEnvelope envelope,
            String marker
    ) {
        MaidscriptEngineAdapter.ScriptedResult result = adapter.handle(envelope);
        String note = marker + " | " + extractDialogue(result) + " | " + payloadSnippet(envelope);
        EmotionCurveCsv.appendRow(
                csv,
                step + 1,
                "ms_story_breakup_betrayal",
                phase,
                envelope.eventId(),
                envelope.intensity(),
                envelope.socialContext(),
                engine,
                result.engineResult().score(),
                note
        );
        return step + 1;
    }

    private static MaidscriptEngineAdapter newAdapter(InteractionEngine engine) {
        MaidscriptScriptRegistry registry = MaidscriptScriptRegistry.fromSource("ms_story_breakup_betrayal.ms", SCRIPT);
        return new MaidscriptEngineAdapter(engine, registry, new ScriptMemoryStore(1600, 120), 5000);
    }

    private static BuiltinEventEnvelope envelope(String eventId, double intensity, SocialContext social, Map<String, Object> payload) {
        return new BuiltinEventEnvelope(eventId, intensity, payload, Map.of("source", "story_test"), social);
    }

    private static String extractDialogue(MaidscriptEngineAdapter.ScriptedResult result) {
        if (result.scriptResult().actions().isEmpty()) {
            return "(no_say)";
        }
        Object text = result.scriptResult().actions().get(0).payload().get("text");
        return text == null ? "(no_text)" : String.valueOf(text);
    }

    private static String payloadSnippet(BuiltinEventEnvelope envelope) {
        String topic = String.valueOf(envelope.payload().getOrDefault("topic", ""));
        String signal = String.valueOf(envelope.payload().getOrDefault("signal", ""));
        String detail = String.valueOf(envelope.payload().getOrDefault("detail", ""));
        return "topic=" + topic + "; signal=" + signal + "; detail=" + detail;
    }

    private static double pressure(InteractionEngine engine) {
        return engine.mood().distribution().getOrDefault(MoodModel.Mood.ANNOYED, 0.0)
                + engine.mood().distribution().getOrDefault(MoodModel.Mood.JEALOUS, 0.0);
    }
}
