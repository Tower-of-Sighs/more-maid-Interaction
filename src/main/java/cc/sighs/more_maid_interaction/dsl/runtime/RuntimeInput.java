package cc.sighs.more_maid_interaction.dsl.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeInput(
        Map<String, Object> state,
        Map<String, Object> emotion,
        String moodTop,
        Map<String, Double> moodDistribution,
        Map<String, Object> social,
        EventData event,
        Map<String, Object> context,
        RuntimeServices services,
        int maxSteps
) {
    public RuntimeInput {
        state = state == null ? Map.of() : new LinkedHashMap<>(state);
        emotion = emotion == null ? Map.of() : new LinkedHashMap<>(emotion);
        moodTop = moodTop == null ? "CALM" : moodTop;
        moodDistribution = moodDistribution == null ? Map.of() : new LinkedHashMap<>(moodDistribution);
        social = social == null ? Map.of() : new LinkedHashMap<>(social);
        event = event == null ? new EventData("", 0, Map.of()) : event;
        context = context == null ? Map.of() : new LinkedHashMap<>(context);
        services = services == null ? RuntimeServices.NOOP : services;
        maxSteps = maxSteps <= 0 ? 2000 : maxSteps;
    }

    public static RuntimeInput empty(String eventId) {
        return new RuntimeInput(
                Map.of(),
                Map.of(),
                "CALM",
                Map.of(),
                Map.of(),
                new EventData(eventId, 0, Map.of()),
                Map.of(),
                RuntimeServices.NOOP,
                2000
        );
    }

    public record EventData(String id, double intensity, Map<String, Object> payload) {
        public EventData {
            id = id == null ? "" : id;
            payload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        }
    }
}
