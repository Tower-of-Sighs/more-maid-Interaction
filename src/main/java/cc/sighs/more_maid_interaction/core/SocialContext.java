package cc.sighs.more_maid_interaction.core;

/**
 * @param lastOtherAffection [0,1], observed intensity of other-affection
 * @param meanOtherFavor     [0,1], average favor of others
 */
public record SocialContext(int rivals, double lastOtherAffection, double meanOtherFavor) {
    public SocialContext(int rivals, double lastOtherAffection, double meanOtherFavor) {
        this.rivals = Math.max(0, rivals);
        this.lastOtherAffection = Stats.clamp(lastOtherAffection);
        this.meanOtherFavor = Stats.clamp(meanOtherFavor);
    }

    public static SocialContext empty() {
        return new SocialContext(0, 0, 0);
    }
}
