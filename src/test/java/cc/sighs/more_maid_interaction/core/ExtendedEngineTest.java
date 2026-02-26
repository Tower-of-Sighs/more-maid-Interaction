package cc.sighs.more_maid_interaction.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

public class ExtendedEngineTest {
    private Personality persona() {
        Map<EventTag, Double> w = new EnumMap<>(EventTag.class);
        w.put(EventTag.HUG, 0.3);
        w.put(EventTag.GIFT, 0.1);
        w.put(EventTag.FEED, 0.2);
        w.put(EventTag.PILLOW, 0.25);
        w.put(EventTag.TEASE, -0.5);
        return new Personality(w);
    }

    private InteractionEvent tease() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.TEASE_INTENSITY, 0.9)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.4)
                .build();
        return InteractionEvent.builder("tease")
                .valence(-0.3)
                .intensity(0.7)
                .tag(EventTag.TEASE)
                .stimulus(s)
                .cooldown(10)
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

    @Test
    public void jealousyReducesScore() {
        InteractionEngine engine = new InteractionEngine(persona(), new Stats(0.3, 0.2, 0.5, 0.3));
        SocialContext none = SocialContext.empty();
        SocialContext jealous = new SocialContext(2, 0.9, 0.6);
        double s1 = engine.apply(hug(), none).score();
        double s2 = engine.apply(hug(), jealous).score();
        Assertions.assertTrue(s2 < s1);
    }

    @Test
    public void repeatedTeaseIncreasesAnnoyed() {
        InteractionEngine engine = new InteractionEngine(persona(), new Stats(0.4, 0.3, 0.6, 0.3));
        for (int i = 0; i < 8; i++) engine.apply(tease());
        double annoyed = engine.mood().distribution().get(MoodModel.Mood.ANNOYED);
        double jealous = engine.mood().distribution().get(MoodModel.Mood.JEALOUS);
        Assertions.assertTrue(annoyed > jealous);
    }

    @Test
    public void monotonyLeadsToBored() {
        InteractionEngine a = new InteractionEngine(persona(), new Stats(0.2, 0.2, 0.5, 0.2));
        InteractionEngine b = new InteractionEngine(persona(), new Stats(0.2, 0.2, 0.5, 0.2));
        InteractionEvent fixed = tease();
        InteractionEvent varied = hug();
        for (int i = 0; i < 12; i++) a.apply(fixed);
        for (int i = 0; i < 12; i++) {
            b.apply(i % 2 == 0 ? varied : tease());
        }
        double boredA = a.mood().distribution().get(MoodModel.Mood.BORED);
        double boredB = b.mood().distribution().get(MoodModel.Mood.BORED);
        Assertions.assertTrue(boredA > boredB);
    }
}
