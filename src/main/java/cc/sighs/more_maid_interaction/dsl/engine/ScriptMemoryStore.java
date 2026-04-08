package cc.sighs.more_maid_interaction.dsl.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ScriptMemoryStore {
    private final int maxEntries;
    private final int frequencyWindow;
    private final Deque<MemoryEntry> entries;
    private final Set<String> storyFlags;
    private final Map<String, Integer> cooldownUntilTick;
    private int currentTick;

    public ScriptMemoryStore() {
        this(512, 64);
    }

    public ScriptMemoryStore(int maxEntries, int frequencyWindow) {
        this.maxEntries = Math.max(16, maxEntries);
        this.frequencyWindow = Math.max(1, frequencyWindow);
        this.entries = new ArrayDeque<>(this.maxEntries);
        this.storyFlags = new HashSet<>();
        this.cooldownUntilTick = new HashMap<>();
        this.currentTick = 0;
    }

    public int currentTick() {
        return currentTick;
    }

    public void advanceTick() {
        currentTick++;
    }

    public void addMemoryTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        if (entries.size() >= maxEntries) {
            entries.pollFirst();
        }
        entries.offerLast(new MemoryEntry(tag, currentTick));
    }

    public double count(String tag, int window) {
        if (tag == null || tag.isBlank() || window <= 0 || entries.isEmpty()) {
            return 0;
        }
        int cutoff = currentTick - window;
        int c = 0;
        for (MemoryEntry entry : entries) {
            if (entry.tick >= cutoff && Objects.equals(entry.tag, tag)) {
                c++;
            }
        }
        return c;
    }

    public double frequency(String tag) {
        if (tag == null || tag.isBlank() || entries.isEmpty()) {
            return 0;
        }
        int cutoff = currentTick - frequencyWindow;
        int total = 0;
        int hit = 0;
        for (MemoryEntry entry : entries) {
            if (entry.tick >= cutoff) {
                total++;
                if (Objects.equals(entry.tag, tag)) {
                    hit++;
                }
            }
        }
        if (total <= 0) {
            return 0;
        }
        return (double) hit / (double) total;
    }

    public boolean hasRecent(String tag, int window) {
        return count(tag, window) > 0;
    }

    public boolean storyHasFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            return false;
        }
        return storyFlags.contains(flag);
    }

    public void setStoryFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            return;
        }
        storyFlags.add(flag);
    }

    public boolean cooldownReady(String key, int ticks) {
        if (key == null || key.isBlank()) {
            return true;
        }
        int until = cooldownUntilTick.getOrDefault(key, Integer.MIN_VALUE);
        if (currentTick < until) {
            return false;
        }
        cooldownUntilTick.put(key, currentTick + Math.max(0, ticks));
        return true;
    }

    public java.util.List<String> recentTags(int limit) {
        if (limit <= 0 || entries.isEmpty()) {
            return java.util.List.of();
        }
        int cap = Math.min(limit, entries.size());
        java.util.ArrayList<String> out = new java.util.ArrayList<>(cap);
        java.util.Iterator<MemoryEntry> it = entries.descendingIterator();
        while (it.hasNext() && out.size() < cap) {
            MemoryEntry entry = it.next();
            if (entry != null && entry.tag != null && !entry.tag.isBlank()) {
                out.add(entry.tag);
            }
        }
        return java.util.List.copyOf(out);
    }

    private record MemoryEntry(String tag, int tick) {
    }
}
