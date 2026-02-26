package cc.sighs.more_maid_interaction.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

public class EngineTest {
    private Personality persona() {
        Map<EventTag, Double> w = new EnumMap<>(EventTag.class);
        w.put(EventTag.HUG, 0.3);
        w.put(EventTag.GIFT, 0.1);
        w.put(EventTag.FEED, 0.2);
        w.put(EventTag.PILLOW, 0.25);
        w.put(EventTag.TEASE, -0.5);
        return new Personality(w);
    }

    private InteractionEvent gift() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.GIFT_VALUE, 0.9)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.3)
                .build();
        return InteractionEvent.builder("gift")
                .valence(0.6)
                .intensity(0.7)
                .tag(EventTag.GIFT)
                .stimulus(s)
                .cooldown(20)
                .build();
    }

    private InteractionEvent hug() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.AFFECTION, 0.8)
                .put(StimulusAxis.INTIMACY, 0.6)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.5)
                .build();
        return InteractionEvent.builder("hug")
                .valence(0.7)
                .intensity(0.8)
                .tag(EventTag.HUG)
                .stimulus(s)
                .build();
    }

    private InteractionEvent feed() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.CARE, 0.7)
                .put(StimulusAxis.WORK_HELPFULNESS, 0.4)
                .build();
        return InteractionEvent.builder("feed")
                .valence(0.5)
                .intensity(0.6)
                .tag(EventTag.FEED)
                .stimulus(s)
                .build();
    }

    private InteractionEvent pillow() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.INTIMACY, 0.8)
                .put(StimulusAxis.AFFECTION, 0.7)
                .build();
        return InteractionEvent.builder("pillow")
                .valence(0.65)
                .intensity(0.75)
                .tag(EventTag.PILLOW)
                .stimulus(s)
                .build();
    }

    @Test
    public void spamVsVariety() {
        InteractionEngine spam = new InteractionEngine(persona(), new Stats(0.2, 0.1, 0.5, 0.3));
        InteractionEngine variety = new InteractionEngine(persona(), new Stats(0.2, 0.1, 0.5, 0.3));
        for (int i = 0; i < 20; i++) spam.apply(gift());
        InteractionEvent[] seq = new InteractionEvent[]{gift(), hug(), feed(), pillow(), hug(), feed(), hug(), pillow(), gift(), hug(), feed(), pillow(), hug(), feed(), hug(), pillow(), gift(), hug(), feed(), pillow()};
        for (InteractionEvent e : seq) variety.apply(e);
        Assertions.assertTrue(variety.stats().favor() > spam.stats().favor());
        Assertions.assertTrue(variety.stats().sincerity() > spam.stats().sincerity());
    }

    @Test
    public void diversityBoostsNovelty() {
        InteractionEngine a = new InteractionEngine(persona(), Stats.zero());
        InteractionEngine b = new InteractionEngine(persona(), Stats.zero());
        for (int i = 0; i < 10; i++) a.apply(hug());
        for (int i = 0; i < 10; i++) {
            b.apply(i % 2 == 0 ? hug() : feed());
        }
        Assertions.assertTrue(b.stats().novelty() > a.stats().novelty());
    }

    @Test
    public void emotionStableRange() {
        InteractionEngine engine = new InteractionEngine(persona(), new Stats(0.7, 0.5, 0.6, 0.4));
        for (int i = 0; i < 10; i++) engine.apply(hug());
        EmotionState e = engine.emotion(engine.stats());
        Assertions.assertTrue(e.pleasure() >= 0 && e.pleasure() <= 1);
        Assertions.assertTrue(e.arousal() >= 0 && e.arousal() <= 1);
        Assertions.assertTrue(e.dominance() >= 0 && e.dominance() <= 1);
    }
}
