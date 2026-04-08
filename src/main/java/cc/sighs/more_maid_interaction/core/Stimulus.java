package cc.sighs.more_maid_interaction.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class Stimulus {
    private static final StimulusAxis[] AXES = StimulusAxis.values();
    private static final Stimulus EMPTY = new Stimulus(new double[AXES.length], false);

    private final double[] values;
    private final Map<StimulusAxis, Double> view;

    public Stimulus(Map<StimulusAxis, Double> values) {
        this.values = new double[AXES.length];
        if (values != null) {
            for (Map.Entry<StimulusAxis, Double> e : values.entrySet()) {
                StimulusAxis axis = e.getKey();
                if (axis == null) {
                    continue;
                }
                double v = e.getValue() == null ? 0 : e.getValue();
                this.values[axis.ordinal()] = clamp(v);
            }
        }
        this.view = buildView(this.values);
    }

    private Stimulus(double[] values, boolean copyArray) {
        this.values = copyArray ? values.clone() : values;
        this.view = buildView(this.values);
    }

    public static Stimulus empty() {
        return EMPTY;
    }

    public double get(StimulusAxis axis) {
        if (axis == null) {
            return 0;
        }
        return values[axis.ordinal()];
    }

    public Map<StimulusAxis, Double> asMap() {
        return view;
    }

    public double dot(Stimulus other) {
        if (other == null) {
            return 0;
        }
        double s = 0;
        for (int i = 0; i < values.length; i++) {
            s += values[i] * other.values[i];
        }
        return s;
    }

    public Stimulus add(Stimulus other) {
        if (other == null) {
            return this;
        }
        double[] next = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            next[i] = clamp(values[i] + other.values[i]);
        }
        return new Stimulus(next, false);
    }

    public Stimulus scale(double k) {
        double[] next = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            next[i] = clamp(values[i] * k);
        }
        return new Stimulus(next, false);
    }

    public static double clamp(double v) {
        return Stats.clamp(v);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Map<StimulusAxis, Double> buildView(double[] values) {
        EnumMap<StimulusAxis, Double> map = new EnumMap<>(StimulusAxis.class);
        for (int i = 0; i < AXES.length; i++) {
            double v = values[i];
            if (v != 0.0) {
                map.put(AXES[i], v);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private final double[] values = new double[AXES.length];

        public Builder put(StimulusAxis axis, double value) {
            if (axis != null) {
                values[axis.ordinal()] = clamp(value);
            }
            return this;
        }

        public Stimulus build() {
            return new Stimulus(values.clone(), false);
        }
    }
}
