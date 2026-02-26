package cc.sighs.more_maid_interaction.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class InteractionEvent {
    private final String name;
    private final double baseValence;
    private final double intensity;
    private final Set<EventTag> tags;
    private final int cooldown;
    private final Stimulus stimulus;

    private InteractionEvent(String name, double baseValence, double intensity, Set<EventTag> tags, int cooldown, Stimulus stimulus) {
        this.name = Objects.requireNonNull(name);
        this.baseValence = clampValence(baseValence);
        this.intensity = clampIntensity(intensity);
        this.tags = Collections.unmodifiableSet(EnumSet.copyOf(tags));
        this.cooldown = Math.max(0, cooldown);
        this.stimulus = stimulus == null ? Stimulus.empty() : stimulus;
    }

    public String name() {
        return name;
    }

    public double baseValence() {
        return baseValence;
    }

    public double intensity() {
        return intensity;
    }

    public Set<EventTag> tags() {
        return tags;
    }

    public int cooldown() {
        return cooldown;
    }

    public Stimulus stimulus() {
        return stimulus;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private double baseValence = 0;
        private double intensity = 1;
        private final EnumSet<EventTag> tags = EnumSet.noneOf(EventTag.class);
        private int cooldown = 0;
        private Stimulus stimulus = Stimulus.empty();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public Builder valence(double v) {
            this.baseValence = clampValence(v);
            return this;
        }

        public Builder intensity(double v) {
            this.intensity = clampIntensity(v);
            return this;
        }

        public Builder tag(EventTag t) {
            this.tags.add(Objects.requireNonNull(t));
            return this;
        }

        public Builder cooldown(int ticks) {
            this.cooldown = Math.max(0, ticks);
            return this;
        }

        public Builder stimulus(Stimulus s) {
            this.stimulus = s == null ? Stimulus.empty() : s;
            return this;
        }

        public InteractionEvent build() {
            return new InteractionEvent(name, baseValence, intensity, tags, cooldown, stimulus);
        }
    }

    private static double clampValence(double v) {
        if (v < -1) return -1;
        if (v > 1) return 1;
        return v;
    }

    private static double clampIntensity(double v) {
        return Stats.clamp(v);
    }
}
