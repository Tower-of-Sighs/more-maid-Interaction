package cc.sighs.more_maid_interaction.core;

import java.util.ArrayDeque;

final class Memory {
    private static final double ALPHA = 0.2;
    private static final StimulusAxis[] AXES = StimulusAxis.values();
    private static final double DISTANCE_NORM = Math.sqrt(AXES.length);

    private final int window;
    private final ArrayDeque<InteractionEvent> recent;
    private final int[] cooldownByTag;
    private final int[] tagCount;
    private final double[] mean;
    private final double[] mean2;
    private int totalTagHits;

    Memory(int window) {
        this.window = Math.max(1, window);
        this.recent = new ArrayDeque<>(this.window);
        this.cooldownByTag = new int[EventTag.values().length];
        this.tagCount = new int[EventTag.values().length];
        this.mean = new double[AXES.length];
        this.mean2 = new double[AXES.length];
        this.totalTagHits = 0;
    }

    void tick() {
        for (int i = 0; i < cooldownByTag.length; i++) {
            if (cooldownByTag[i] > 0) {
                cooldownByTag[i]--;
            }
        }
    }

    void push(InteractionEvent e) {
        if (recent.size() >= window) {
            InteractionEvent old = recent.poll();
            if (old != null) {
                removeTagCounts(old);
            }
        }

        recent.offer(e);
        addTagCounts(e);

        Stimulus s = e.stimulus();
        for (int i = 0; i < AXES.length; i++) {
            double x = s.get(AXES[i]);
            double m = mean[i];
            double m2 = mean2[i];
            mean[i] = (1 - ALPHA) * m + ALPHA * x;
            mean2[i] = (1 - ALPHA) * m2 + ALPHA * x * x;
        }
    }

    private void addTagCounts(InteractionEvent e) {
        int cooldown = e.cooldown();
        for (EventTag t : e.tags()) {
            int idx = t.ordinal();
            if (cooldown > 0 && cooldown > cooldownByTag[idx]) {
                cooldownByTag[idx] = cooldown;
            }
            tagCount[idx]++;
            totalTagHits++;
        }
    }

    private void removeTagCounts(InteractionEvent e) {
        for (EventTag t : e.tags()) {
            int idx = t.ordinal();
            if (tagCount[idx] > 0) {
                tagCount[idx]--;
                totalTagHits--;
            }
        }
        if (totalTagHits < 0) {
            totalTagHits = 0;
        }
    }

    double frequency(EventTag tag) {
        if (recent.isEmpty()) {
            return 0;
        }
        return (double) tagCount[tag.ordinal()] / (double) recent.size();
    }

    double diversity() {
        if (totalTagHits <= 0) {
            return 0;
        }
        double h = 0;
        int kinds = 0;
        for (int count : tagCount) {
            if (count <= 0) {
                continue;
            }
            kinds++;
            double p = (double) count / (double) totalTagHits;
            h += -p * Math.log(p);
        }
        if (kinds <= 1) {
            return 0;
        }
        double max = Math.log(kinds);
        if (max <= 0) {
            return 0;
        }
        return h / max;
    }

    int getCooldown(EventTag tag) {
        return cooldownByTag[tag.ordinal()];
    }

    double variance(StimulusAxis a) {
        int idx = a.ordinal();
        double m = mean[idx];
        double m2 = mean2[idx];
        double v = m2 - m * m;
        return v < 0 ? 0 : v;
    }

    double avgVariance() {
        if (AXES.length == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < AXES.length; i++) {
            double m = mean[i];
            double m2 = mean2[i];
            double v = m2 - m * m;
            if (v > 0) {
                sum += v;
            }
        }
        return sum / AXES.length;
    }

    double distanceFromMean(Stimulus s) {
        if (DISTANCE_NORM <= 0) {
            return 0;
        }
        double d2 = 0;
        for (int i = 0; i < AXES.length; i++) {
            double diff = s.get(AXES[i]) - mean[i];
            d2 += diff * diff;
        }
        double d = Math.sqrt(d2);
        return Math.min(1.0, d / DISTANCE_NORM);
    }
}
