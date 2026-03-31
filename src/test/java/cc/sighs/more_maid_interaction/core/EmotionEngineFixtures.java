package cc.sighs.more_maid_interaction.core;

import org.junit.jupiter.api.Assertions;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

final class EmotionEngineFixtures {
    private EmotionEngineFixtures() {
    }

    static Personality personality() {
        Map<EventTag, Double> w = new EnumMap<>(EventTag.class);
        w.put(EventTag.HUG, 0.35);
        w.put(EventTag.GIFT, 0.20);
        w.put(EventTag.FEED, 0.24);
        w.put(EventTag.PILLOW, 0.30);
        w.put(EventTag.PRAISE, 0.14);
        w.put(EventTag.WORK_HELP, 0.18);
        w.put(EventTag.TALK, 0.08);
        w.put(EventTag.TEASE, -0.70);
        return new Personality(w);
    }

    static InteractionEvent giftFlower() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.GIFT_VALUE, 0.90)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.25)
                .build();
        return InteractionEvent.builder("gift_flower")
                .valence(0.62)
                .intensity(0.72)
                .tag(EventTag.GIFT)
                .stimulus(s)
                .cooldown(20)
                .build();
    }

    static InteractionEvent hug() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.AFFECTION, 0.84)
                .put(StimulusAxis.INTIMACY, 0.62)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.45)
                .build();
        return InteractionEvent.builder("hug")
                .valence(0.72)
                .intensity(0.80)
                .tag(EventTag.HUG)
                .stimulus(s)
                .build();
    }

    static InteractionEvent feed() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.CARE, 0.78)
                .put(StimulusAxis.WORK_HELPFULNESS, 0.50)
                .build();
        return InteractionEvent.builder("feed")
                .valence(0.56)
                .intensity(0.62)
                .tag(EventTag.FEED)
                .stimulus(s)
                .build();
    }

    static InteractionEvent pillow() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.INTIMACY, 0.86)
                .put(StimulusAxis.AFFECTION, 0.72)
                .build();
        return InteractionEvent.builder("knee_pillow")
                .valence(0.67)
                .intensity(0.76)
                .tag(EventTag.PILLOW)
                .stimulus(s)
                .build();
    }

    static InteractionEvent talk() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.36)
                .put(StimulusAxis.PLAYFUL, 0.16)
                .build();
        return InteractionEvent.builder("chat")
                .valence(0.30)
                .intensity(0.50)
                .tag(EventTag.TALK)
                .stimulus(s)
                .build();
    }

    static InteractionEvent praise() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.AFFECTION, 0.34)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.28)
                .build();
        return InteractionEvent.builder("praise")
                .valence(0.42)
                .intensity(0.58)
                .tag(EventTag.PRAISE)
                .stimulus(s)
                .build();
    }

    static InteractionEvent workHelp() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.WORK_HELPFULNESS, 0.92)
                .put(StimulusAxis.CARE, 0.30)
                .build();
        return InteractionEvent.builder("work_help")
                .valence(0.50)
                .intensity(0.60)
                .tag(EventTag.WORK_HELP)
                .stimulus(s)
                .build();
    }

    static InteractionEvent teaseLight() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.TEASE_INTENSITY, 0.55)
                .put(StimulusAxis.PLAYFUL, 0.40)
                .build();
        return InteractionEvent.builder("tease_light")
                .valence(-0.18)
                .intensity(0.58)
                .tag(EventTag.TEASE)
                .stimulus(s)
                .cooldown(8)
                .build();
    }

    static InteractionEvent attackMaid() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.TEASE_INTENSITY, 1.00)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.80)
                .build();
        return InteractionEvent.builder("attack_maid")
                .valence(-0.82)
                .intensity(0.98)
                .tag(EventTag.TEASE)
                .stimulus(s)
                .build();
    }

    static InteractionEvent[] positivePool() {
        return new InteractionEvent[]{
                giftFlower(), hug(), feed(), pillow(), talk(), praise(), workHelp()
        };
    }

    static InteractionEvent[] fullPool() {
        return new InteractionEvent[]{
                giftFlower(), hug(), feed(), pillow(), talk(), praise(), workHelp(), teaseLight(), attackMaid()
        };
    }

    static InteractionEvent pickWeighted(Random rng, InteractionEvent[] events, double[] weights) {
        if (events.length != weights.length) {
            throw new IllegalArgumentException("events/weights length mismatch");
        }
        double sum = 0;
        for (double w : weights) {
            sum += Math.max(0, w);
        }
        if (sum <= 0) {
            return events[0];
        }
        double r = rng.nextDouble() * sum;
        double c = 0;
        for (int i = 0; i < weights.length; i++) {
            c += Math.max(0, weights[i]);
            if (r <= c) {
                return events[i];
            }
        }
        return events[events.length - 1];
    }

    static void assertStateStable(InteractionEngine engine, String prefix) {
        Stats s = engine.stats();
        EmotionState e = engine.emotion(s);
        assertRange01(s.favor(), prefix + ".favor");
        assertRange01(s.bond(), prefix + ".bond");
        assertRange01(s.sincerity(), prefix + ".sincerity");
        assertRange01(s.novelty(), prefix + ".novelty");
        assertRange01(e.pleasure(), prefix + ".pleasure");
        assertRange01(e.arousal(), prefix + ".arousal");
        assertRange01(e.dominance(), prefix + ".dominance");

        double sum = 0;
        for (Map.Entry<MoodModel.Mood, Double> entry : engine.mood().distribution().entrySet()) {
            double v = entry.getValue();
            assertRange01(v, prefix + ".mood." + entry.getKey().name().toLowerCase(Locale.ROOT));
            sum += v;
        }
        Assertions.assertEquals(1.0, sum, 1e-6, prefix + ".mood_sum");
    }

    static double moodProb(InteractionEngine engine, MoodModel.Mood mood) {
        return engine.mood().distribution().getOrDefault(mood, 0.0);
    }

    private static void assertRange01(double v, String name) {
        Assertions.assertFalse(Double.isNaN(v), name + " is NaN");
        Assertions.assertFalse(Double.isInfinite(v), name + " is infinite");
        Assertions.assertTrue(v >= 0.0 && v <= 1.0, name + " out of range: " + v);
    }
}
