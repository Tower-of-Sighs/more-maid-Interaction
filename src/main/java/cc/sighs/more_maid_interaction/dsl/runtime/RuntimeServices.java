package cc.sighs.more_maid_interaction.dsl.runtime;

public interface RuntimeServices {
    RuntimeServices NOOP = new RuntimeServices() {
    };

    default double memoryCount(String tag, int window) {
        return 0;
    }

    default double memoryFrequency(String tag) {
        return 0;
    }

    default boolean memoryHasRecent(String tag, int window) {
        return false;
    }

    default boolean storyHasFlag(String flag) {
        return false;
    }

    default boolean cooldownReady(String key, int ticks) {
        return true;
    }

    default double random() {
        return Math.random();
    }
}
