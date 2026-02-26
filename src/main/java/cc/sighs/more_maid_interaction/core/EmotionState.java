package cc.sighs.more_maid_interaction.core;

public record EmotionState(double pleasure, double arousal, double dominance) {
    public EmotionState(double pleasure, double arousal, double dominance) {
        this.pleasure = clamp(pleasure);
        this.arousal = clamp(arousal);
        this.dominance = clamp(dominance);
    }

    private static double clamp(double v) {
        return Stats.clamp(v);
    }
}
