package cc.sighs.more_maid_interaction.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class InteractionEngine {
    private static final int STIMULUS_AXIS_COUNT = StimulusAxis.values().length;
    public record Result(Stats before, Stats after, double score, EmotionState emotion) {
    }

    private final Personality personality;
    private final Memory memory;
    private Stats stats;
    private int ticks;
    private final MoodModel mood;

    public InteractionEngine(Personality personality, Stats initial) {
        this.personality = Objects.requireNonNull(personality);
        this.stats = Objects.requireNonNull(initial);
        this.memory = new Memory(16);
        this.ticks = 0;
        this.mood = new MoodModel();
    }

    public Stats stats() {
        return stats;
    }

    public int ticks() {
        return ticks;
    }

    public Result apply(InteractionEvent event) {
        return apply(event, SocialContext.empty());
    }

    public Result apply(InteractionEvent event, SocialContext ctx) {
        Objects.requireNonNull(event);
        Objects.requireNonNull(ctx);
        memory.tick();
        Stats before = stats;

        double diversity = memory.diversity();
        double s = score(event, ctx, diversity);
        Stats delta = deltaFromScore(event, s);

        stats = stats.add(delta);
        stats = stats.withNovelty(Math.max(Stats.clamp(stats.novelty() * 0.92 + noveltyBoost(event, diversity) * 0.08), 0));
        adjustSincerity(event);
        memory.push(event);
        ticks++;

        EmotionState e = emotion(stats);
        double varianceAvg = memory.avgVariance();
        double spam = spamIndex(event);
        double jealous = jealousyIndex(ctx);
        double teaseAxis = event.stimulus().get(StimulusAxis.TEASE_INTENSITY);
        mood.update(e, varianceAvg, spam, jealous, teaseAxis, stats.novelty());
        return new Result(before, stats, s, e);
    }

    public Result applyScriptDelta(Stats delta) {
        Objects.requireNonNull(delta);
        return applyScriptDelta(delta.favor(), delta.bond(), delta.sincerity(), delta.novelty(), SocialContext.empty(), Map.of());
    }

    public Result applyScriptDelta(Stats delta, SocialContext ctx, Map<MoodModel.Mood, Double> moodBias) {
        Objects.requireNonNull(delta);
        return applyScriptDelta(delta.favor(), delta.bond(), delta.sincerity(), delta.novelty(), ctx, moodBias);
    }

    public Result applyScriptDelta(double favorDelta, double bondDelta, double sincerityDelta, double noveltyDelta) {
        return applyScriptDelta(favorDelta, bondDelta, sincerityDelta, noveltyDelta, SocialContext.empty(), Map.of());
    }

    public Result applyScriptDelta(
            double favorDelta,
            double bondDelta,
            double sincerityDelta,
            double noveltyDelta,
            SocialContext ctx,
            Map<MoodModel.Mood, Double> moodBias
    ) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(moodBias);
        Stats before = stats;
        stats = new Stats(
                Stats.clamp(stats.favor() + favorDelta),
                Stats.clamp(stats.bond() + bondDelta),
                Stats.clamp(stats.sincerity() + sincerityDelta),
                Stats.clamp(stats.novelty() + noveltyDelta)
        );
        ticks++;
        EmotionState e = emotion(stats);
        mood.update(e, 0, 0, jealousyIndex(ctx), 0, stats.novelty());
        mood.applyLogitBias(moodBias);
        return new Result(before, stats, 0, e);
    }

    public EngineSnapshot snapshot() {
        return snapshot(stats);
    }

    public EngineSnapshot snapshot(Stats state) {
        Stats safeState = state == null ? stats : state;
        EmotionState e = emotion(safeState);
        Map<MoodModel.Mood, Double> dist = new EnumMap<>(MoodModel.Mood.class);
        dist.putAll(mood.distribution());
        return new EngineSnapshot(safeState, e, mood.topMood(), dist, ticks);
    }

    private void adjustSincerity(InteractionEvent e) {
        double spam = spamIndex(e);
        boolean negative = e.tags().contains(EventTag.TEASE) || e.baseValence() < 0;
        double delta;
        if (negative) {
            double factor = 0.06 + 0.14 * memory.frequency(EventTag.TEASE);
            delta = -factor * (0.7 + 0.3 * e.intensity());
        } else if (spam > 0.85) {
            delta = -0.006 * (0.5 + 0.5 * e.intensity());
        } else {
            delta = 0.02 * (0.5 + 0.5 * e.intensity()) * (1 - stats.sincerity() * 0.5);
        }
        stats = stats.withSincerity(Stats.clamp(stats.sincerity() + delta));
    }

    private double spamIndex(InteractionEvent e) {
        Set<EventTag> tags = e.tags();
        if (tags.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (EventTag t : tags) {
            if (memory.frequency(t) > 0.4) {
                hit++;
            }
        }
        return Math.min(1.0, (double) hit / tags.size());
    }

    private Stats deltaFromScore(InteractionEvent e, double s) {
        double g = logistic(s * 2);
        double fav = (g - 0.5) * 0.15 * intensityGate(e);
        double bond = (g - 0.5) * 0.10 * bondGate();
        double sin = (g - 0.5) * 0.04 * sincerityGate();
        return new Stats(fav, bond, sin, 0);
    }

    private double intensityGate(InteractionEvent e) {
        double c = coolFactor(e);
        double n = 0.6 + 0.4 * c;
        return Math.max(0, n) * (0.7 + 0.3 * e.intensity());
    }

    private double bondGate() {
        double k = 0.5 + 0.5 * stats.favor();
        double d = 1 - Math.pow(stats.bond(), 2);
        return k * d;
    }

    private double sincerityGate() {
        return 0.5 + 0.5 * stats.sincerity();
    }

    private double noveltyBoost(InteractionEvent e, double diversity) {
        double aff = Math.max(0, personality.affinity(e.tags()));
        return 0.3 * diversity + 0.2 * aff + 0.5 * e.intensity();
    }

    private double score(InteractionEvent e, SocialContext ctx, double diversity) {
        double base = e.baseValence() * (0.7 + 0.3 * e.intensity());
        double aff = 1 + 0.6 * personality.affinity(e.tags());
        double rec = coolFactor(e);
        double div = 0.8 + 0.4 * diversity;
        double nov = 0.5 + 0.5 * stats.novelty();
        double syn = synergy(e);
        double stim = stimulusMatch(e);
        double var = 0.8 + 0.4 * memory.distanceFromMean(e.stimulus());
        double jealous = 1.0 - 0.5 * jealousyIndex(ctx);
        return base * aff * rec * div * nov * syn * stim * var * jealous;
    }

    private double coolFactor(InteractionEvent e) {
        double f = 1;
        for (EventTag t : e.tags()) {
            int cd = memory.getCooldown(t);
            if (cd > 0) {
                double p = Math.max(0, 1 - Math.min(1, cd / 20.0));
                f *= p;
            }
        }
        return f;
    }

    private double synergy(InteractionEvent e) {
        double p = stats.favor() * 0.5 + stats.bond() * 0.3 + stats.sincerity() * 0.2;
        double a = 0;
        for (EventTag t : e.tags()) {
            a += personality.weight(t);
        }
        a = a / Math.max(1, e.tags().size());
        return 0.8 + 0.4 * p + 0.2 * a;
    }

    public EmotionState emotion(Stats s) {
        double pleasure = clamp01(0.6 * s.favor() + 0.2 * s.bond() + 0.2 * s.sincerity());
        double arousal = clamp01(0.5 * s.novelty() + 0.3 * s.favor() + 0.2 * (1 - s.bond()));
        double dominance = clamp01(0.5 * s.sincerity() + 0.3 * s.bond() + 0.2 * (1 - s.novelty()));
        return new EmotionState(pleasure, arousal, dominance);
    }

    public MoodModel mood() {
        return mood;
    }

    private double stimulusMatch(InteractionEvent e) {
        Stimulus stimulus = e.stimulus();

        double f = stats.favor();
        double g = stats.bond();
        double s = stats.sincerity();
        double n = stats.novelty();

        double care = clamp01(0.4 + 0.2 * (1 - g) + 0.2 * s);
        double affection = clamp01(0.3 + 0.3 * (1 - g) + 0.2 * f);
        double gift = clamp01(0.2 + 0.2 * (1 - s) + 0.2 * n);
        double intimacy = clamp01(0.2 + 0.3 * (1 - g) + 0.3 * n);
        double playful = clamp01(0.2 + 0.3 * n);
        double workHelp = clamp01(0.2 + 0.3 * s + 0.2 * g);
        double social = clamp01(0.2 + 0.3 * n);
        double tease = clamp01(0.1 + 0.2 * n - 0.3 * s);

        double d = 0;
        d += care * stimulus.get(StimulusAxis.CARE);
        d += affection * stimulus.get(StimulusAxis.AFFECTION);
        d += gift * stimulus.get(StimulusAxis.GIFT_VALUE);
        d += intimacy * stimulus.get(StimulusAxis.INTIMACY);
        d += playful * stimulus.get(StimulusAxis.PLAYFUL);
        d += workHelp * stimulus.get(StimulusAxis.WORK_HELPFULNESS);
        d += social * stimulus.get(StimulusAxis.SOCIAL_EXPOSURE);
        d += tease * stimulus.get(StimulusAxis.TEASE_INTENSITY);

        double norm = STIMULUS_AXIS_COUNT <= 0 ? 0 : d / STIMULUS_AXIS_COUNT;
        return 0.8 + 0.4 * clamp01(norm);
    }

    private double jealousyIndex(SocialContext ctx) {
        double base = ctx.lastOtherAffection();
        double rivals = Math.min(1.0, ctx.rivals() / 3.0);
        double rivalFactor = 1.0 + 0.5 * rivals;
        double meanFavor = ctx.meanOtherFavor();
        double favorGap = Math.max(0.0, meanFavor - stats.favor());
        EmotionState es = emotion(stats);
        double vulnerability = 0.5 + 0.5 * (1 - es.dominance()) * (0.6 + 0.4 * (1 - es.pleasure()));
        double attachment = 0.5 + 0.5 * (0.6 * (1 - stats.bond()) + 0.3 * (1 - stats.favor()) + 0.1 * (1 - stats.sincerity()));
        double j = base * rivalFactor * (0.6 + 0.4 * meanFavor) * attachment * vulnerability + 0.5 * favorGap;
        return clamp01(j);
    }

    public static double logistic(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static double clamp01(double v) {
        return Stats.clamp(v);
    }
}
