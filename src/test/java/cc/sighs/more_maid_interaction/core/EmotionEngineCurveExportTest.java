package cc.sighs.more_maid_interaction.core;

import cc.sighs.more_maid_interaction.testing.EmotionCurveCsv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Random;

public class EmotionEngineCurveExportTest {
    @Test
    public void exportJavaArcAndArchetypeCurves() {
        Path arc = exportArcCurve();
        Assertions.assertTrue(arc.toFile().exists());
        Assertions.assertTrue(arc.toFile().length() > 1024);

        ArchetypeStats caring = exportArchetypeCurve("caring", new double[]{0.14, 0.21, 0.22, 0.14, 0.10, 0.09, 0.08, 0.01, 0.01}, 2026033101L);
        ArchetypeStats neutral = exportArchetypeCurve("neutral", new double[]{0.11, 0.16, 0.15, 0.10, 0.13, 0.11, 0.10, 0.08, 0.06}, 2026033102L);
        ArchetypeStats toxic = exportArchetypeCurve("toxic", new double[]{0.03, 0.05, 0.06, 0.03, 0.11, 0.06, 0.06, 0.28, 0.32}, 2026033103L);

        Assertions.assertTrue(caring.pleasure > toxic.pleasure + 0.10);
        Assertions.assertTrue(neutral.pleasure > toxic.pleasure + 0.08);
        Assertions.assertTrue(toxic.annoyed > caring.annoyed + 0.03);
        Assertions.assertTrue(toxic.bond < neutral.bond - 0.05);
    }

    @Test
    public void exportJavaAutomationRandomCurve() {
        Random rng = new Random(2026033199L);
        InteractionEngine engine = new InteractionEngine(
                EmotionEngineFixtures.personality(),
                new Stats(0.20, 0.18, 0.52, 0.24)
        );

        InteractionEvent[] pool = EmotionEngineFixtures.fullPool();
        double[] baseWeights = new double[]{0.12, 0.17, 0.14, 0.11, 0.14, 0.10, 0.09, 0.07, 0.06};

        StringBuilder csv = new StringBuilder(EmotionCurveCsv.header());
        int moodSwitch = 0;
        MoodModel.Mood lastMood = engine.mood().topMood();
        final int steps = 6000;

        for (int t = 0; t < steps; t++) {
            double[] weights = adaptiveWeights(baseWeights, rng, t);
            InteractionEvent e = EmotionEngineFixtures.pickWeighted(rng, pool, weights);
            SocialContext ctx = randomSocialContext(rng, t);
            InteractionEngine.Result r = engine.apply(e, ctx);
            EmotionEngineFixtures.assertStateStable(engine, "auto_step_" + t);

            String phase = phaseFromTick(t);
            EmotionCurveCsv.appendRow(
                    csv, t + 1, "java_auto_6000", phase, e.name(), e.intensity(), ctx, engine, r.score(),
                    "adaptive_random_replay"
            );

            MoodModel.Mood now = engine.mood().topMood();
            if (now != lastMood) {
                moodSwitch++;
                lastMood = now;
            }
        }

        Path out = EmotionCurveCsv.write("java_auto_6000.csv", csv.toString());
        Assertions.assertTrue(out.toFile().exists());
        Assertions.assertTrue(out.toFile().length() > 1024);
        Assertions.assertTrue(moodSwitch > 80, "mood transitions too static");
    }

    private static Path exportArcCurve() {
        InteractionEngine engine = new InteractionEngine(
                EmotionEngineFixtures.personality(),
                new Stats(0.22, 0.18, 0.52, 0.25)
        );
        StringBuilder csv = new StringBuilder(EmotionCurveCsv.header());
        int step = 0;

        InteractionEvent[] warmPool = EmotionEngineFixtures.positivePool();
        for (int i = 0; i < 120; i++) {
            InteractionEvent e = warmPool[i % warmPool.length];
            InteractionEngine.Result r = engine.apply(e, SocialContext.empty());
            EmotionCurveCsv.appendRow(
                    csv, ++step, "java_arc", "warmup", e.name(), e.intensity(), SocialContext.empty(), engine, r.score(),
                    "positive_build_up"
            );
        }
        EmotionState warmEmotion = engine.emotion(engine.stats());
        double warmPressure = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.JEALOUS)
                + EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.ANNOYED);

        for (int i = 0; i < 70; i++) {
            InteractionEvent e = (i % 3 != 0) ? EmotionEngineFixtures.attackMaid() : EmotionEngineFixtures.teaseLight();
            SocialContext ctx = new SocialContext(3, 0.90, 0.85);
            InteractionEngine.Result r = engine.apply(e, ctx);
            EmotionCurveCsv.appendRow(
                    csv, ++step, "java_arc", "conflict", e.name(), e.intensity(), ctx, engine, r.score(),
                    "high_conflict_with_social_pressure"
            );
        }
        EmotionState conflictEmotion = engine.emotion(engine.stats());
        double conflictPressure = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.JEALOUS)
                + EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.ANNOYED);

        for (int i = 0; i < 220; i++) {
            InteractionEvent e;
            if (i % 5 == 0) {
                e = EmotionEngineFixtures.workHelp();
            } else if (i % 2 == 0) {
                e = EmotionEngineFixtures.feed();
            } else if (i % 3 == 0) {
                e = EmotionEngineFixtures.pillow();
            } else {
                e = EmotionEngineFixtures.hug();
            }
            SocialContext ctx = SocialContext.empty();
            InteractionEngine.Result r = engine.apply(e, ctx);
            EmotionCurveCsv.appendRow(
                    csv, ++step, "java_arc", "repair", e.name(), e.intensity(), ctx, engine, r.score(),
                    "repair_by_care_and_intimacy"
            );
        }
        EmotionState finalEmotion = engine.emotion(engine.stats());

        Assertions.assertTrue(conflictEmotion.pleasure() < warmEmotion.pleasure() - 0.12);
        Assertions.assertTrue(conflictPressure > warmPressure + 0.18);
        Assertions.assertTrue(finalEmotion.pleasure() > conflictEmotion.pleasure() + 0.08);

        return EmotionCurveCsv.write("java_arc_curve.csv", csv.toString());
    }

    private static ArchetypeStats exportArchetypeCurve(String name, double[] weights, long seed) {
        Random rng = new Random(seed);
        InteractionEngine engine = new InteractionEngine(
                EmotionEngineFixtures.personality(),
                new Stats(0.20, 0.18, 0.52, 0.24)
        );
        InteractionEvent[] pool = EmotionEngineFixtures.fullPool();
        StringBuilder csv = new StringBuilder(EmotionCurveCsv.header());

        for (int t = 0; t < 220; t++) {
            InteractionEvent e = EmotionEngineFixtures.pickWeighted(rng, pool, weights);
            SocialContext ctx = archetypeContext(name, rng);
            InteractionEngine.Result r = engine.apply(e, ctx);
            EmotionCurveCsv.appendRow(
                    csv, t + 1, "java_archetype_" + name, "session", e.name(), e.intensity(), ctx, engine, r.score(),
                    "player_style_" + name
            );
        }

        EmotionCurveCsv.write("java_archetype_" + name + ".csv", csv.toString());
        Stats s = engine.stats();
        EmotionState em = engine.emotion(s);
        return new ArchetypeStats(s.bond(), em.pleasure(), EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.ANNOYED));
    }

    private static SocialContext archetypeContext(String archetype, Random rng) {
        return switch (archetype) {
            case "caring" -> new SocialContext(rng.nextInt(2), 0.05 + 0.20 * rng.nextDouble(), 0.20 + 0.20 * rng.nextDouble());
            case "toxic" -> new SocialContext(1 + rng.nextInt(4), 0.45 + 0.45 * rng.nextDouble(), 0.45 + 0.45 * rng.nextDouble());
            default -> new SocialContext(rng.nextInt(3), 0.15 + 0.35 * rng.nextDouble(), 0.25 + 0.30 * rng.nextDouble());
        };
    }

    private static double[] adaptiveWeights(double[] base, Random rng, int tick) {
        double[] weights = base.clone();
        if (tick % 1200 < 200) {
            weights[7] += 0.08;
            weights[8] += 0.04;
        } else if (tick % 1500 > 1100) {
            weights[0] += 0.05;
            weights[1] += 0.04;
            weights[2] += 0.04;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.max(0.001, weights[i] + (rng.nextDouble() - 0.5) * 0.02);
        }
        return weights;
    }

    private static SocialContext randomSocialContext(Random rng, int tick) {
        int rivals = (tick % 500 < 90) ? 2 + rng.nextInt(3) : rng.nextInt(2);
        double otherAffection = (tick % 700 < 130) ? 0.55 + rng.nextDouble() * 0.40 : 0.05 + rng.nextDouble() * 0.25;
        double meanOtherFavor = (tick % 900 < 180) ? 0.45 + rng.nextDouble() * 0.40 : 0.15 + rng.nextDouble() * 0.30;
        return new SocialContext(rivals, otherAffection, meanOtherFavor);
    }

    private static String phaseFromTick(int tick) {
        if (tick % 1200 < 200) {
            return "pressure_burst";
        }
        if (tick % 1500 > 1100) {
            return "repair_window";
        }
        return "normal_play";
    }

    private record ArchetypeStats(double bond, double pleasure, double annoyed) {
    }
}
