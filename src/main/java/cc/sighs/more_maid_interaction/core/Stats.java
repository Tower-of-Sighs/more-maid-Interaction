package cc.sighs.more_maid_interaction.core;

import java.util.Objects;

public record Stats(double favor, double bond, double sincerity, double novelty) {
    public Stats(double favor, double bond, double sincerity, double novelty) {
        this.favor = clamp(favor);
        this.bond = clamp(bond);
        this.sincerity = clamp(sincerity);
        this.novelty = clamp(novelty);
    }

    public static Stats zero() {
        return new Stats(0, 0, 0, 0);
    }

    public Stats add(Stats delta) {
        Objects.requireNonNull(delta);
        return new Stats(
                clamp(favor + delta.favor),
                clamp(bond + delta.bond),
                clamp(sincerity + delta.sincerity),
                clamp(novelty + delta.novelty)
        );
    }

    public Stats withFavor(double v) {
        return new Stats(clamp(v), bond, sincerity, novelty);
    }

    public Stats withBond(double v) {
        return new Stats(favor, clamp(v), sincerity, novelty);
    }

    public Stats withSincerity(double v) {
        return new Stats(favor, bond, clamp(v), novelty);
    }

    public Stats withNovelty(double v) {
        return new Stats(favor, bond, sincerity, clamp(v));
    }

    public static double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
