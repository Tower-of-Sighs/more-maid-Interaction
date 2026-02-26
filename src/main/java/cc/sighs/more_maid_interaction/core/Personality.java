package cc.sighs.more_maid_interaction.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record Personality(Map<EventTag, Double> weights) {
    public Personality(Map<EventTag, Double> weights) {
        EnumMap<EventTag, Double> copy = new EnumMap<>(EventTag.class);
        if (weights != null) {
            for (Map.Entry<EventTag, Double> e : weights.entrySet()) {
                double v = e.getValue() == null ? 0 : e.getValue();
                copy.put(e.getKey(), clamp(v));
            }
        }
        this.weights = Collections.unmodifiableMap(copy);
    }

    public static Personality neutral() {
        return new Personality(Collections.emptyMap());
    }

    public double affinity(Iterable<EventTag> tags) {
        double s = 0;
        int c = 0;
        for (EventTag t : tags) {
            s += weights.getOrDefault(t, 0.0);
            c++;
        }
        if (c == 0) return 0;
        return clamp(s / c);
    }

    public double weight(EventTag tag) {
        return weights.getOrDefault(tag, 0.0);
    }

    private static double clamp(double v) {
        if (v < -1) return -1;
        if (v > 1) return 1;
        return v;
    }
}
