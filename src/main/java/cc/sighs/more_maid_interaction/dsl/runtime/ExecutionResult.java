package cc.sighs.more_maid_interaction.dsl.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExecutionResult {
    private final Map<String, Double> delta = new LinkedHashMap<>();
    private final Map<String, Double> moodBias = new LinkedHashMap<>();
    private final List<String> memoryAdds = new ArrayList<>();
    private final Set<String> storyFlagsSet = new LinkedHashSet<>();
    private final List<ReactionAction> actions = new ArrayList<>();
    private boolean stopped;

    public Map<String, Double> delta() {
        return delta;
    }

    public Map<String, Double> moodBias() {
        return moodBias;
    }

    public List<String> memoryAdds() {
        return memoryAdds;
    }

    public Set<String> storyFlagsSet() {
        return storyFlagsSet;
    }

    public List<ReactionAction> actions() {
        return actions;
    }

    public boolean stopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public void addDelta(String key, double value) {
        delta.put(key, delta.getOrDefault(key, 0.0) + value);
    }

    public void addMoodBias(String key, double value) {
        moodBias.put(key, moodBias.getOrDefault(key, 0.0) + value);
    }

    public void addMemoryTag(String tag) {
        memoryAdds.add(tag);
    }

    public void addStoryFlag(String flag) {
        storyFlagsSet.add(flag);
    }

    public void addAction(String type, Map<String, Object> payload) {
        actions.add(new ReactionAction(type, payload));
    }
}
