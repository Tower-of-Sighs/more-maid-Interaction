package cc.sighs.more_maid_interaction.testing;

import cc.sighs.more_maid_interaction.core.EmotionState;
import cc.sighs.more_maid_interaction.core.InteractionEngine;
import cc.sighs.more_maid_interaction.core.MoodModel;
import cc.sighs.more_maid_interaction.core.SocialContext;
import cc.sighs.more_maid_interaction.core.Stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class EmotionCurveCsv {
    private EmotionCurveCsv() {
    }

    public static String header() {
        return "step,scenario,phase,event_id,intensity,rivals,last_other_affection,mean_other_favor,"
                + "favor,bond,sincerity,novelty,pleasure,arousal,dominance,mood_top,"
                + "calm,affectionate,curious,jealous,annoyed,bored,score,note\n";
    }

    public static void appendRow(
            StringBuilder out,
            int step,
            String scenario,
            String phase,
            String eventId,
            double intensity,
            SocialContext social,
            InteractionEngine engine,
            double score,
            String note
    ) {
        Stats s = engine.stats();
        EmotionState e = engine.emotion(s);
        MoodModel mood = engine.mood();
        out.append(step).append(',')
                .append(csv(scenario)).append(',')
                .append(csv(phase)).append(',')
                .append(csv(eventId)).append(',')
                .append(fmt(intensity)).append(',')
                .append(social == null ? 0 : social.rivals()).append(',')
                .append(fmt(social == null ? 0 : social.lastOtherAffection())).append(',')
                .append(fmt(social == null ? 0 : social.meanOtherFavor())).append(',')
                .append(fmt(s.favor())).append(',')
                .append(fmt(s.bond())).append(',')
                .append(fmt(s.sincerity())).append(',')
                .append(fmt(s.novelty())).append(',')
                .append(fmt(e.pleasure())).append(',')
                .append(fmt(e.arousal())).append(',')
                .append(fmt(e.dominance())).append(',')
                .append(csv(mood.topMood().name())).append(',')
                .append(fmt(mood.distribution().getOrDefault(MoodModel.Mood.CALM, 0.0))).append(',')
                .append(fmt(mood.distribution().getOrDefault(MoodModel.Mood.AFFECTIONATE, 0.0))).append(',')
                .append(fmt(mood.distribution().getOrDefault(MoodModel.Mood.CURIOUS, 0.0))).append(',')
                .append(fmt(mood.distribution().getOrDefault(MoodModel.Mood.JEALOUS, 0.0))).append(',')
                .append(fmt(mood.distribution().getOrDefault(MoodModel.Mood.ANNOYED, 0.0))).append(',')
                .append(fmt(mood.distribution().getOrDefault(MoodModel.Mood.BORED, 0.0))).append(',')
                .append(fmt(score)).append(',')
                .append(csv(note))
                .append('\n');
    }

    public static Path write(String fileName, String content) {
        try {
            Path dir = Paths.get("build", "reports", "emotion-curves");
            Files.createDirectories(dir);
            Path path = dir.resolve(fileName);
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write curve csv: " + fileName, e);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    private static String csv(String s) {
        if (s == null) {
            return "\"\"";
        }
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
