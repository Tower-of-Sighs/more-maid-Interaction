package cc.sighs.more_maid_interaction.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class EmotionEngineScenarioTest {
    @Test
    public void relationshipArcShowsBuildConflictRecovery() {
        InteractionEngine engine = new InteractionEngine(
                EmotionEngineFixtures.personality(),
                new Stats(0.22, 0.18, 0.52, 0.25)
        );
        Stats start = engine.stats();

        InteractionEvent[] warmPool = EmotionEngineFixtures.positivePool();
        for (int i = 0; i < 120; i++) {
            InteractionEvent e = warmPool[i % warmPool.length];
            engine.apply(e, SocialContext.empty());
        }
        Stats afterWarm = engine.stats();
        EmotionState emotionAfterWarm = engine.emotion(afterWarm);
        double warmCalm = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.CALM);
        double warmAffection = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.AFFECTIONATE);
        double warmAnnoyed = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.ANNOYED);
        double warmJealous = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.JEALOUS);

        Assertions.assertTrue(afterWarm.favor() > start.favor() + 0.12);
        Assertions.assertTrue(afterWarm.bond() > start.bond() + 0.10);
        Assertions.assertTrue(emotionAfterWarm.pleasure() > 0.45);
        Assertions.assertTrue(warmCalm + warmAffection > 0.38);

        for (int i = 0; i < 70; i++) {
            InteractionEvent e = (i % 3 != 0)
                    ? EmotionEngineFixtures.attackMaid()
                    : EmotionEngineFixtures.teaseLight();
            engine.apply(e, new SocialContext(3, 0.90, 0.85));
        }
        Stats afterConflict = engine.stats();
        EmotionState emotionAfterConflict = engine.emotion(afterConflict);
        double conflictAnnoyed = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.ANNOYED);
        double conflictJealous = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.JEALOUS);

        System.out.printf(
                "CONFLICT warm[f=%.3f,b=%.3f,p=%.3f] conflict[f=%.3f,b=%.3f,p=%.3f,ann=%.3f,jea=%.3f]%n",
                afterWarm.favor(), afterWarm.bond(), emotionAfterWarm.pleasure(),
                afterConflict.favor(), afterConflict.bond(), emotionAfterConflict.pleasure(), conflictAnnoyed, conflictJealous
        );

        Assertions.assertTrue(emotionAfterConflict.pleasure() < emotionAfterWarm.pleasure() - 0.12);
        Assertions.assertTrue(conflictJealous + conflictAnnoyed > warmJealous + warmAnnoyed + 0.18);
        Assertions.assertTrue(conflictAnnoyed + conflictJealous > 0.30);

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
            engine.apply(e, SocialContext.empty());
        }
        Stats finalState = engine.stats();
        EmotionState finalEmotion = engine.emotion(finalState);
        double finalAnnoyed = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.ANNOYED);
        double finalJealous = EmotionEngineFixtures.moodProb(engine, MoodModel.Mood.JEALOUS);

        System.out.printf(
                "ARC start[f=%.3f,b=%.3f] warm[f=%.3f,b=%.3f,p=%.3f] conflict[f=%.3f,b=%.3f,p=%.3f,ann=%.3f,jea=%.3f] final[f=%.3f,b=%.3f,p=%.3f,ann=%.3f]%n",
                start.favor(), start.bond(),
                afterWarm.favor(), afterWarm.bond(), emotionAfterWarm.pleasure(),
                afterConflict.favor(), afterConflict.bond(), emotionAfterConflict.pleasure(), conflictAnnoyed, conflictJealous,
                finalState.favor(), finalState.bond(), finalEmotion.pleasure(), finalAnnoyed
        );

        Assertions.assertTrue(finalEmotion.pleasure() > emotionAfterConflict.pleasure() + 0.08);
        Assertions.assertTrue(finalAnnoyed < conflictAnnoyed);
        Assertions.assertTrue(finalJealous < conflictJealous);
    }

    @Test
    public void variedPositiveInteractionsOutperformGiftSpam() {
        InteractionEngine spam = new InteractionEngine(
                EmotionEngineFixtures.personality(),
                new Stats(0.24, 0.20, 0.56, 0.30)
        );
        InteractionEngine varied = new InteractionEngine(
                EmotionEngineFixtures.personality(),
                new Stats(0.24, 0.20, 0.56, 0.30)
        );

        for (int i = 0; i < 220; i++) {
            spam.apply(EmotionEngineFixtures.giftFlower(), SocialContext.empty());
        }

        InteractionEvent[] pool = EmotionEngineFixtures.positivePool();
        for (int i = 0; i < 220; i++) {
            varied.apply(pool[i % pool.length], SocialContext.empty());
        }

        Assertions.assertTrue(varied.stats().favor() > spam.stats().favor());
        Assertions.assertTrue(varied.stats().sincerity() > spam.stats().sincerity());
        Assertions.assertTrue(
                EmotionEngineFixtures.moodProb(spam, MoodModel.Mood.BORED)
                        > EmotionEngineFixtures.moodProb(varied, MoodModel.Mood.BORED)
        );
    }

    @Test
    public void differentPlayerArchetypesLeadToDistinctEmotionalOutcomes() {
        Aggregate caring = simulateArchetype(Archetype.CARING, 180, 20260331L, 24);
        Aggregate mixed = simulateArchetype(Archetype.MIXED, 180, 20260331L + 1000, 24);
        Aggregate toxic = simulateArchetype(Archetype.TOXIC, 180, 20260331L + 2000, 24);

        System.out.printf(
                "ARCH caring[f=%.3f,b=%.3f,p=%.3f,ann=%.3f] mixed[f=%.3f,b=%.3f,p=%.3f,ann=%.3f] toxic[f=%.3f,b=%.3f,p=%.3f,ann=%.3f]%n",
                caring.meanFavor, caring.meanBond, caring.meanPleasure, caring.meanAnnoyed,
                mixed.meanFavor, mixed.meanBond, mixed.meanPleasure, mixed.meanAnnoyed,
                toxic.meanFavor, toxic.meanBond, toxic.meanPleasure, toxic.meanAnnoyed
        );

        Assertions.assertTrue(mixed.meanBond > toxic.meanBond + 0.15);
        Assertions.assertTrue(caring.meanPleasure > toxic.meanPleasure + 0.12);
        Assertions.assertTrue(mixed.meanPleasure > toxic.meanPleasure + 0.10);
        Assertions.assertTrue(toxic.meanAnnoyed > caring.meanAnnoyed + 0.04);
        Assertions.assertTrue(toxic.meanAnnoyed > mixed.meanAnnoyed + 0.02);
    }

    private Aggregate simulateArchetype(Archetype archetype, int steps, long seedBase, int episodes) {
        double favor = 0;
        double bond = 0;
        double pleasure = 0;
        double annoyed = 0;

        for (int episode = 0; episode < episodes; episode++) {
            InteractionEngine engine = new InteractionEngine(
                    EmotionEngineFixtures.personality(),
                    new Stats(0.20, 0.18, 0.52, 0.24)
            );
            Random rng = new Random(seedBase + episode * 37L);
            InteractionEvent[] pool = EmotionEngineFixtures.fullPool();
            double[] weights = archetypeWeights(archetype);

            for (int t = 0; t < steps; t++) {
                InteractionEvent e = EmotionEngineFixtures.pickWeighted(rng, pool, weights);
                SocialContext ctx = switch (archetype) {
                    case CARING -> new SocialContext(rng.nextInt(2), 0.1 + rng.nextDouble() * 0.2, 0.2 + rng.nextDouble() * 0.2);
                    case MIXED -> new SocialContext(rng.nextInt(3), 0.2 + rng.nextDouble() * 0.4, 0.3 + rng.nextDouble() * 0.3);
                    case TOXIC -> new SocialContext(1 + rng.nextInt(4), 0.5 + rng.nextDouble() * 0.5, 0.5 + rng.nextDouble() * 0.4);
                };
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
        return new Aggregate(favor / n, bond / n, pleasure / n, annoyed / n);
    }

    private static double[] archetypeWeights(Archetype archetype) {
        return switch (archetype) {
            case CARING -> new double[]{0.15, 0.20, 0.20, 0.15, 0.10, 0.10, 0.08, 0.02, 0.00};
            case MIXED -> new double[]{0.12, 0.16, 0.14, 0.10, 0.14, 0.12, 0.10, 0.08, 0.04};
            case TOXIC -> new double[]{0.03, 0.05, 0.06, 0.03, 0.10, 0.05, 0.06, 0.28, 0.34};
        };
    }

    private enum Archetype {
        CARING, MIXED, TOXIC
    }

    private record Aggregate(double meanFavor, double meanBond, double meanPleasure, double meanAnnoyed) {
    }
}
