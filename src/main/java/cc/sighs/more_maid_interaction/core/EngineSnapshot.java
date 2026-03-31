package cc.sighs.more_maid_interaction.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record EngineSnapshot(
        Stats stats,
        EmotionState emotion,
        MoodModel.Mood moodTop,
        Map<MoodModel.Mood, Double> moodDistribution,
        int ticks
) {
    public EngineSnapshot {
        stats = stats == null ? Stats.zero() : stats;
        emotion = emotion == null ? new EmotionState(0, 0, 0) : emotion;
        moodTop = moodTop == null ? MoodModel.Mood.CALM : moodTop;
        EnumMap<MoodModel.Mood, Double> copy = new EnumMap<>(MoodModel.Mood.class);
        if (moodDistribution != null) {
            copy.putAll(moodDistribution);
        }
        moodDistribution = Collections.unmodifiableMap(copy);
        ticks = Math.max(0, ticks);
    }
}
