package cc.sighs.more_maid_interaction.dsl.engine;

import cc.sighs.more_maid_interaction.core.EmotionState;
import cc.sighs.more_maid_interaction.core.EngineSnapshot;
import cc.sighs.more_maid_interaction.core.InteractionEngine;
import cc.sighs.more_maid_interaction.core.MoodModel;
import cc.sighs.more_maid_interaction.core.SocialContext;
import cc.sighs.more_maid_interaction.core.Stats;
import cc.sighs.more_maid_interaction.dsl.runtime.ExecutionResult;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeInput;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MaidscriptEngineAdapter {
    private final InteractionEngine engine;
    private final MaidscriptScriptRegistry scripts;
    private final ScriptMemoryStore scriptMemory;
    private final EngineRuntimeServices runtimeServices;
    private final int maxSteps;

    public MaidscriptEngineAdapter(InteractionEngine engine, MaidscriptScriptRegistry scripts) {
        this(engine, scripts, new ScriptMemoryStore(), 2000);
    }

    public MaidscriptEngineAdapter(InteractionEngine engine, MaidscriptScriptRegistry scripts, ScriptMemoryStore scriptMemory, int maxSteps) {
        this.engine = Objects.requireNonNull(engine);
        this.scripts = Objects.requireNonNull(scripts);
        this.scriptMemory = scriptMemory == null ? new ScriptMemoryStore() : scriptMemory;
        this.runtimeServices = new EngineRuntimeServices(this.scriptMemory);
        this.maxSteps = maxSteps <= 0 ? 2000 : maxSteps;
    }

    public ScriptMemoryStore scriptMemory() {
        return scriptMemory;
    }

    public ScriptedResult handle(BuiltinEventEnvelope envelope) {
        Objects.requireNonNull(envelope);
        scriptMemory.advanceTick();

        EngineSnapshot before = engine.snapshot();
        RuntimeInput input = toRuntimeInput(before, envelope);
        ExecutionResult scriptResult = scripts.execute(envelope.eventId(), input);

        scriptResult.memoryAdds().forEach(scriptMemory::addMemoryTag);
        scriptResult.storyFlagsSet().forEach(scriptMemory::setStoryFlag);

        Delta delta = toDelta(scriptResult.delta());
        Map<MoodModel.Mood, Double> bias = toMoodBias(scriptResult.moodBias());

        InteractionEngine.Result engineResult = engine.applyScriptDelta(
                delta.favor(),
                delta.bond(),
                delta.sincerity(),
                delta.novelty(),
                envelope.socialContext(),
                bias
        );
        EngineSnapshot after = engine.snapshot();

        return new ScriptedResult(envelope, before, after, engineResult, scriptResult);
    }

    private RuntimeInput toRuntimeInput(EngineSnapshot snapshot, BuiltinEventEnvelope envelope) {
        Stats stats = snapshot.stats();
        EmotionState emotion = snapshot.emotion();
        SocialContext socialContext = envelope.socialContext();

        Map<String, Object> state = Map.of(
                "favor", stats.favor(),
                "bond", stats.bond(),
                "sincerity", stats.sincerity(),
                "novelty", stats.novelty()
        );

        Map<String, Object> emotionMap = Map.of(
                "pleasure", emotion.pleasure(),
                "arousal", emotion.arousal(),
                "dominance", emotion.dominance()
        );

        Map<String, Double> moodDistribution = new LinkedHashMap<>();
        for (Map.Entry<MoodModel.Mood, Double> entry : snapshot.moodDistribution().entrySet()) {
            String upper = entry.getKey().name();
            moodDistribution.put(upper, entry.getValue());
            moodDistribution.put(upper.toLowerCase(Locale.ROOT), entry.getValue());
        }

        Map<String, Object> social = Map.of(
                "rivals", socialContext.rivals(),
                "last_other_affection", socialContext.lastOtherAffection(),
                "mean_other_favor", socialContext.meanOtherFavor()
        );

        return new RuntimeInput(
                state,
                emotionMap,
                snapshot.moodTop().name(),
                moodDistribution,
                social,
                new RuntimeInput.EventData(envelope.eventId(), envelope.intensity(), envelope.payload()),
                envelope.context(),
                runtimeServices,
                maxSteps
        );
    }

    private static Delta toDelta(Map<String, Double> deltaMap) {
        double favor = value(deltaMap, "favor");
        double bond = value(deltaMap, "bond");
        double sincerity = value(deltaMap, "sincerity");
        double novelty = value(deltaMap, "novelty");
        return new Delta(favor, bond, sincerity, novelty);
    }

    private static double value(Map<String, Double> map, String key) {
        if (map == null || key == null) {
            return 0;
        }
        return map.getOrDefault(key, 0.0);
    }

    private static Map<MoodModel.Mood, Double> toMoodBias(Map<String, Double> moodBias) {
        Map<MoodModel.Mood, Double> mapped = new EnumMap<>(MoodModel.Mood.class);
        if (moodBias == null || moodBias.isEmpty()) {
            return mapped;
        }
        for (Map.Entry<String, Double> entry : moodBias.entrySet()) {
            MoodModel.Mood mood = parseMood(entry.getKey());
            if (mood != null) {
                mapped.put(mood, mapped.getOrDefault(mood, 0.0) + entry.getValue());
            }
        }
        return mapped;
    }

    private static MoodModel.Mood parseMood(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return MoodModel.Mood.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record ScriptedResult(
            BuiltinEventEnvelope envelope,
            EngineSnapshot before,
            EngineSnapshot after,
            InteractionEngine.Result engineResult,
            ExecutionResult scriptResult
    ) {
    }

    private record Delta(double favor, double bond, double sincerity, double novelty) {
    }
}
