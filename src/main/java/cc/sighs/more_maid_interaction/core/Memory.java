package cc.sighs.more_maid_interaction.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;

final class Memory {
    private final int window;
    private final Queue<InteractionEvent> recent;
    private final Map<EventTag, Integer> cooldowns;
    private final EnumMap<StimulusAxis, Double> mean;
    private final EnumMap<StimulusAxis, Double> mean2;
    private static final double ALPHA = 0.2;

    Memory(int window) {
        this.window = Math.max(1, window);
        this.recent = new ArrayDeque<>(this.window);
        this.cooldowns = new EnumMap<>(EventTag.class);
        this.mean = new EnumMap<>(StimulusAxis.class);
        this.mean2 = new EnumMap<>(StimulusAxis.class);
        for (StimulusAxis a : StimulusAxis.values()) {
            mean.put(a, 0.0);
            mean2.put(a, 0.0);
        }
    }

    void tick() {
        for (Map.Entry<EventTag, Integer> e : new ArrayList<>(cooldowns.entrySet())) {
            int v = e.getValue() == null ? 0 : e.getValue();
            if (v <= 0) {
                cooldowns.remove(e.getKey());
            } else {
                cooldowns.put(e.getKey(), v - 1);
            }
        }
    }

    void push(InteractionEvent e) {
        if (recent.size() >= window) recent.poll();
        recent.offer(e);
        for (EventTag t : e.tags()) {
            if (e.cooldown() > 0) {
                cooldowns.put(t, Math.max(cooldowns.getOrDefault(t, 0), e.cooldown()));
            }
        }
        Stimulus s = e.stimulus();
        for (StimulusAxis a : StimulusAxis.values()) {
            double x = s.get(a);
            double m = mean.getOrDefault(a, 0.0);
            double m2 = mean2.getOrDefault(a, 0.0);
            double nm = (1 - ALPHA) * m + ALPHA * x;
            double nm2 = (1 - ALPHA) * m2 + ALPHA * x * x;
            mean.put(a, nm);
            mean2.put(a, nm2);
        }
    }

    double frequency(EventTag tag) {
        if (recent.isEmpty()) return 0;
        int c = 0;
        for (InteractionEvent e : recent) if (e.tags().contains(tag)) c++;
        return (double) c / (double) recent.size();
    }

    double diversity() {
        if (recent.isEmpty()) return 0;
        Map<EventTag, Integer> count = new EnumMap<>(EventTag.class);
        for (InteractionEvent e : recent) {
            for (EventTag t : e.tags()) count.put(t, count.getOrDefault(t, 0) + 1);
        }
        int total = 0;
        for (int v : count.values()) total += v;
        if (total == 0) return 0;
        double h = 0;
        for (int v : count.values()) {
            double p = (double) v / (double) total;
            h += -p * Math.log(p);
        }
        double max = Math.log(count.size());
        if (max <= 0) return 0;
        return h / max;
    }

    int getCooldown(EventTag tag) {
        return cooldowns.getOrDefault(tag, 0);
    }

    double variance(StimulusAxis a) {
        double m = mean.getOrDefault(a, 0.0);
        double m2 = mean2.getOrDefault(a, 0.0);
        double v = m2 - m * m;
        if (v < 0) v = 0;
        return v;
    }

    double avgVariance() {
        double s = 0;
        int c = 0;
        for (StimulusAxis a : StimulusAxis.values()) {
            s += variance(a);
            c++;
        }
        if (c == 0) return 0;
        return s / c;
    }

    double distanceFromMean(Stimulus s) {
        double d2 = 0;
        for (StimulusAxis a : StimulusAxis.values()) {
            double diff = s.get(a) - mean.getOrDefault(a, 0.0);
            d2 += diff * diff;
        }
        double d = Math.sqrt(d2);
        double norm = Math.sqrt(StimulusAxis.values().length);
        if (norm <= 0) return 0;
        return Math.min(1.0, d / norm);
    }
}
