package cc.sighs.more_maid_interaction.dsl.engine;

import cc.sighs.more_maid_interaction.core.SocialContext;
import cc.sighs.more_maid_interaction.core.Stats;

import java.util.LinkedHashMap;
import java.util.Map;

public record BuiltinEventEnvelope(
        String eventId,
        double intensity,
        Map<String, Object> payload,
        Map<String, Object> context,
        SocialContext socialContext
) {
    public BuiltinEventEnvelope {
        eventId = eventId == null ? "" : eventId;
        intensity = Stats.clamp(intensity);
        payload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        context = context == null ? Map.of() : new LinkedHashMap<>(context);
        socialContext = socialContext == null ? SocialContext.empty() : socialContext;
    }

    public static BuiltinEventEnvelope of(String eventId) {
        return new BuiltinEventEnvelope(eventId, 0, Map.of(), Map.of(), SocialContext.empty());
    }
}
