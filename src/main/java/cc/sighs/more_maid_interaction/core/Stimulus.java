package cc.sighs.more_maid_interaction.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class Stimulus {
    private final Map<StimulusAxis, Double> values;

    public Stimulus(Map<StimulusAxis, Double> values) {
        EnumMap<StimulusAxis, Double> map = new EnumMap<>(StimulusAxis.class);
        if (values != null) {
            for (Map.Entry<StimulusAxis, Double> e : values.entrySet()) {
                double v = e.getValue() == null ? 0 : e.getValue();
                map.put(e.getKey(), clamp(v));
            }
        }
        this.values = Collections.unmodifiableMap(map);
    }

    public static Stimulus empty() {
        return new Stimulus(Collections.emptyMap());
    }

    public double get(StimulusAxis axis) {
        return values.getOrDefault(axis, 0.0);
    }

    public Map<StimulusAxis, Double> asMap() {
        return values;
    }

    public double dot(Stimulus other) {
        double s = 0;
        for (Map.Entry<StimulusAxis, Double> e : values.entrySet()) {
            s += e.getValue() * other.get(e.getKey());
        }
        return s;
    }

    public Stimulus add(Stimulus other) {
        EnumMap<StimulusAxis, Double> map = new EnumMap<>(StimulusAxis.class);
        for (StimulusAxis a : StimulusAxis.values()) {
            map.put(a, clamp(get(a) + other.get(a)));
        }
        return new Stimulus(map);
    }

    public Stimulus scale(double k) {
        EnumMap<StimulusAxis, Double> map = new EnumMap<>(StimulusAxis.class);
        for (StimulusAxis a : StimulusAxis.values()) {
            map.put(a, clamp(get(a) * k));
        }
        return new Stimulus(map);
    }

    public static double clamp(double v) {
        return Stats.clamp(v);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EnumMap<StimulusAxis, Double> map = new EnumMap<>(StimulusAxis.class);

        public Builder put(StimulusAxis axis, double value) {
            map.put(axis, clamp(value));
            return this;
        }

        public Stimulus build() {
            return new Stimulus(map);
        }
    }
}
