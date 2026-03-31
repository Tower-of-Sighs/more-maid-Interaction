package cc.sighs.more_maid_interaction.dsl.engine;

import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeServices;

import java.util.Objects;
import java.util.Random;

public final class EngineRuntimeServices implements RuntimeServices {
    private final ScriptMemoryStore memoryStore;
    private final Random random;

    public EngineRuntimeServices(ScriptMemoryStore memoryStore) {
        this(memoryStore, new Random());
    }

    public EngineRuntimeServices(ScriptMemoryStore memoryStore, Random random) {
        this.memoryStore = Objects.requireNonNull(memoryStore);
        this.random = random == null ? new Random() : random;
    }

    @Override
    public double memoryCount(String tag, int window) {
        return memoryStore.count(tag, window);
    }

    @Override
    public double memoryFrequency(String tag) {
        return memoryStore.frequency(tag);
    }

    @Override
    public boolean memoryHasRecent(String tag, int window) {
        return memoryStore.hasRecent(tag, window);
    }

    @Override
    public boolean storyHasFlag(String flag) {
        return memoryStore.storyHasFlag(flag);
    }

    @Override
    public boolean cooldownReady(String key, int ticks) {
        return memoryStore.cooldownReady(key, ticks);
    }

    @Override
    public double random() {
        return random.nextDouble();
    }
}
