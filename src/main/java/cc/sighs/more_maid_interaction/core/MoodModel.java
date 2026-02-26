package cc.sighs.more_maid_interaction.core;

import java.util.EnumMap;
import java.util.Map;

public final class MoodModel {
    public enum Mood { CALM, AFFECTIONATE, CURIOUS, JEALOUS, ANNOYED, BORED }

    private final EnumMap<Mood, Double> dist = new EnumMap<>(Mood.class);

    public MoodModel() {
        for (Mood m : Mood.values()) dist.put(m, 1.0 / Mood.values().length);
    }

    public Map<Mood, Double> distribution() {
        return dist;
    }

    public void update(EmotionState e, double varianceAvg, double spamIndex, double jealousyIndex, double teaseAxis, double novelty) {
        double calm = (0.8 * e.pleasure() + 0.4 * e.dominance()) * (1 - 0.4 * jealousyIndex);
        double affectionate = (1.6 * e.pleasure() + 0.4 * e.dominance()) * (1 - 0.7 * jealousyIndex);
        double curious = 1.5 * e.arousal() + 0.6 * novelty + 0.8 * varianceAvg;
        double jealous = 4.2 * jealousyIndex + 0.6 * (1 - e.dominance()) + 0.5 * (1 - e.pleasure());
        double annoyed = 1.2 * teaseAxis + 0.8 * (1 - e.pleasure()) + 0.3 * spamIndex;
        double bored = 1.1 * (1 - varianceAvg) + 0.7 * (1 - e.arousal()) + 0.3 * (1 - novelty);

        double[] logits = new double[]{calm, affectionate, curious, jealous, annoyed, bored};
        double[] soft = softmax(logits);

        double gamma = 0.7;
        int i = 0;
        for (Mood m : Mood.values()) {
            double p = dist.get(m);
            dist.put(m, clamp01((1 - gamma) * p + gamma * soft[i++]));
        }
        normalize();
    }

    public Mood topMood() {
        Mood best = Mood.CALM;
        double b = -1;
        for (Map.Entry<Mood, Double> e : dist.entrySet()) {
            if (e.getValue() > b) {
                b = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private void normalize() {
        double sum = 0;
        for (double v : dist.values()) sum += v;
        if (sum <= 0) {
            double u = 1.0 / Mood.values().length;
            for (Mood m : Mood.values()) dist.put(m, u);
            return;
        }
        for (Mood m : Mood.values()) dist.put(m, dist.get(m) / sum);
    }

    private static double[] softmax(double[] x) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : x) if (v > max) max = v;
        double sum = 0;
        double[] e = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            e[i] = Math.exp(x[i] - max);
            sum += e[i];
        }
        for (int i = 0; i < x.length; i++) e[i] = e[i] / sum;
        return e;
    }

    private static double clamp01(double v) {
        return Stats.clamp(v);
    }
}
