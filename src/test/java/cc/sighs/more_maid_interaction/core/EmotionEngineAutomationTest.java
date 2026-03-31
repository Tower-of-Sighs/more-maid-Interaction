package cc.sighs.more_maid_interaction.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class EmotionEngineAutomationTest {
    @Test
    public void longRunRandomReplayStaysStableAndDynamic() {
        final int steps = 6000;
        final int seeds = 6;
        int totalMoodSwitch = 0;

        for (int s = 0; s < seeds; s++) {
            long seed = 20260331L + s * 91L;
            Random rng = new Random(seed);
            InteractionEngine engine = new InteractionEngine(
                    EmotionEngineFixtures.personality(),
                    new Stats(0.22, 0.20, 0.54, 0.26)
            );
            InteractionEvent[] pool = EmotionEngineFixtures.fullPool();
            double[] baseWeights = new double[]{0.12, 0.17, 0.14, 0.11, 0.14, 0.10, 0.09, 0.07, 0.06};

            MoodModel.Mood lastMood = engine.mood().topMood();
            int moodSwitch = 0;

            for (int t = 0; t < steps; t++) {
                double[] weights = adaptiveWeights(baseWeights, rng, t);
                InteractionEvent e = EmotionEngineFixtures.pickWeighted(rng, pool, weights);
                SocialContext ctx = randomSocialContext(rng, t);
                engine.apply(e, ctx);
                EmotionEngineFixtures.assertStateStable(engine, "seed" + s + "_step" + t);

                MoodModel.Mood now = engine.mood().topMood();
                if (now != lastMood) {
                    moodSwitch++;
                    lastMood = now;
                }
            }

            totalMoodSwitch += moodSwitch;
            Assertions.assertTrue(moodSwitch > 80, "mood transitions too static, seed=" + seed);
        }

        Assertions.assertTrue(totalMoodSwitch > 700, "global mood transitions unexpectedly low");
    }

    @Test
    public void monteCarloCaringOutperformsToxicInExpectation() {
        MonteCarloStats caring = monteCarlo(Behavior.CARING, 140, 96, 7777L);
        MonteCarloStats neutral = monteCarlo(Behavior.NEUTRAL, 140, 96, 8888L);
        MonteCarloStats toxic = monteCarlo(Behavior.TOXIC, 140, 96, 9999L);

        System.out.printf(
                "MONTE caring[f=%.3f,b=%.3f,p=%.3f,ann=%.3f] neutral[f=%.3f,b=%.3f,p=%.3f,ann=%.3f] toxic[f=%.3f,b=%.3f,p=%.3f,ann=%.3f]%n",
                caring.meanFavor, caring.meanBond, caring.meanPleasure, caring.meanAnnoyed,
                neutral.meanFavor, neutral.meanBond, neutral.meanPleasure, neutral.meanAnnoyed,
                toxic.meanFavor, toxic.meanBond, toxic.meanPleasure, toxic.meanAnnoyed
        );

        Assertions.assertTrue(caring.meanFavor > toxic.meanFavor + 0.03);
        Assertions.assertTrue(neutral.meanFavor > toxic.meanFavor + 0.01);
        Assertions.assertTrue(caring.meanBond > toxic.meanBond + 0.03);
        Assertions.assertTrue(neutral.meanBond > toxic.meanBond + 0.01);
        Assertions.assertTrue(neutral.meanAnnoyed < toxic.meanAnnoyed);
        Assertions.assertTrue(caring.meanAnnoyed < toxic.meanAnnoyed - 0.05);
        Assertions.assertTrue(caring.meanPleasure > toxic.meanPleasure + 0.05);
    }

    private static MonteCarloStats monteCarlo(Behavior behavior, int steps, int episodes, long seedBase) {
        double favor = 0;
        double bond = 0;
        double pleasure = 0;
        double annoyed = 0;

        for (int i = 0; i < episodes; i++) {
            Random rng = new Random(seedBase + i * 17L);
            InteractionEngine engine = new InteractionEngine(
                    EmotionEngineFixtures.personality(),
                    new Stats(0.20, 0.18, 0.52, 0.24)
            );
            InteractionEvent[] pool = EmotionEngineFixtures.fullPool();
            double[] weights = behaviorWeights(behavior);
            for (int t = 0; t < steps; t++) {
                InteractionEvent e = EmotionEngineFixtures.pickWeighted(rng, pool, weights);
                SocialContext ctx = behaviorContext(behavior, rng);
                engine.apply(e, ctx);
            }

            Stats s = engine.stats();
            EmotionState em = engine.emotion(s);
            favor += s.favor();
            bond += s.bond();
            pleasure += em.pleasure();
            annoyed += EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.ANNOYED);
        }

        double n = Math.max(1, episodes);
        return new MonteCarloStats(favor / n, bond / n, pleasure / n, annoyed / n);
    }

    private static double[] adaptiveWeights(double[] baseWeights, Random rng, int tick) {
        double[] weights = baseWeights.clone();
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

    private static double[] behaviorWeights(Behavior behavior) {
        return switch (behavior) {
            case CARING -> new double[]{0.14, 0.20, 0.21, 0.14, 0.10, 0.09, 0.09, 0.02, 0.01};
            case NEUTRAL -> new double[]{0.11, 0.16, 0.15, 0.10, 0.13, 0.11, 0.10, 0.08, 0.06};
            case TOXIC -> new double[]{0.03, 0.05, 0.05, 0.03, 0.11, 0.06, 0.07, 0.27, 0.33};
        };
    }

    private static SocialContext behaviorContext(Behavior behavior, Random rng) {
        return switch (behavior) {
            case CARING -> new SocialContext(rng.nextInt(2), 0.05 + 0.20 * rng.nextDouble(), 0.20 + 0.20 * rng.nextDouble());
            case NEUTRAL -> new SocialContext(rng.nextInt(3), 0.15 + 0.35 * rng.nextDouble(), 0.25 + 0.30 * rng.nextDouble());
            case TOXIC -> new SocialContext(1 + rng.nextInt(4), 0.45 + 0.45 * rng.nextDouble(), 0.45 + 0.45 * rng.nextDouble());
        };
    }

    private enum Behavior {
        CARING, NEUTRAL, TOXIC
    }

    private record MonteCarloStats(double meanFavor, double meanBond, double meanPleasure, double meanAnnoyed) {
    }
}
