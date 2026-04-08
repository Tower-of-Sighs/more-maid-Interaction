package cc.sighs.more_maid_interaction.mc.interaction;

import cc.sighs.more_maid_interaction.dsl.engine.BuiltinEventIds;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum BuiltinMaidInteractionEvent {
    KNEE_PILLOW(
            BuiltinEventIds.PLAYER_KNEE_PILLOW,
            "event-knee-pillow",
            "event.more_maid_interaction.knee_pillow",
            "event_detail.more_maid_interaction.knee_pillow"
    ),
    GIFT(
            BuiltinEventIds.PLAYER_GIFT_FLOWER,
            "event-gift",
            "event.more_maid_interaction.gift",
            "event_detail.more_maid_interaction.gift"
    ),
    HEADPAT(
            BuiltinEventIds.PLAYER_HEADPAT,
            "event-headpat",
            "event.more_maid_interaction.headpat",
            "event_detail.more_maid_interaction.headpat"
    ),
    FEED(
            BuiltinEventIds.PLAYER_FEED,
            "event-feed",
            "event.more_maid_interaction.feed",
            "event_detail.more_maid_interaction.feed"
    );

    private static final Map<String, BuiltinMaidInteractionEvent> BY_EVENT_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(event -> normalize(event.eventId), event -> event));

    private final String eventId;
    private final String buttonElementId;
    private final String displayNameKey;
    private final String detailKey;

    BuiltinMaidInteractionEvent(String eventId, String buttonElementId, String displayNameKey, String detailKey) {
        this.eventId = eventId;
        this.buttonElementId = buttonElementId;
        this.displayNameKey = displayNameKey;
        this.detailKey = detailKey;
    }

    public String eventId() {
        return eventId;
    }

    public String buttonElementId() {
        return buttonElementId;
    }

    public String displayNameKey() {
        return displayNameKey;
    }

    public String detailKey() {
        return detailKey;
    }

    public static Optional<BuiltinMaidInteractionEvent> byEventId(String eventId) {
        if (eventId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_EVENT_ID.get(normalize(eventId)));
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
