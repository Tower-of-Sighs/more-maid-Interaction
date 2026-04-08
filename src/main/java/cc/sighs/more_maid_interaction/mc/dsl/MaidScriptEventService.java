package cc.sighs.more_maid_interaction.mc.dsl;

import cc.sighs.more_maid_interaction.MoreMaidInteraction;
import cc.sighs.more_maid_interaction.core.EngineSnapshot;
import cc.sighs.more_maid_interaction.core.InteractionEngine;
import cc.sighs.more_maid_interaction.core.MoodModel;
import cc.sighs.more_maid_interaction.core.Personality;
import cc.sighs.more_maid_interaction.core.SocialContext;
import cc.sighs.more_maid_interaction.core.Stats;
import cc.sighs.more_maid_interaction.dsl.engine.BuiltinEventEnvelope;
import cc.sighs.more_maid_interaction.dsl.engine.BuiltinEventIds;
import cc.sighs.more_maid_interaction.dsl.engine.MaidscriptEngineAdapter;
import cc.sighs.more_maid_interaction.dsl.engine.MaidscriptScriptRegistry;
import cc.sighs.more_maid_interaction.dsl.engine.ScriptMemoryStore;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeCallable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidScriptEventService {
    private static final String SCRIPT_RESOURCE = "maidscript/mc_gift_demo.ms";

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static volatile MaidscriptScriptRegistry sharedRegistry;

    private MaidScriptEventService() {
    }

    public static MaidscriptEngineAdapter.ScriptedResult handleGift(UUID maidUuid, Map<String, Object> payload) {
        if (maidUuid == null) {
            return null;
        }
        Map<String, Object> safePayload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        Session session = SESSIONS.computeIfAbsent(maidUuid, ignored -> createSession());
        double intensity = deriveIntensity(safePayload);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("source", "mc.gift");
        context.put("version", "mode1-shell");
        context.put("item", buildGiftItemBinding(safePayload));

        BuiltinEventEnvelope envelope = new BuiltinEventEnvelope(
                BuiltinEventIds.PLAYER_GIFT_FLOWER,
                intensity,
                safePayload,
                context,
                SocialContext.empty()
        );
        return session.adapter.handle(envelope);
    }

    public static EngineSnapshot snapshot(UUID maidUuid) {
        if (maidUuid == null) {
            return null;
        }
        Session session = SESSIONS.get(maidUuid);
        if (session == null) {
            return null;
        }
        return session.engine.snapshot();
    }

    public static List<String> recentMemoryTags(UUID maidUuid, int limit) {
        if (maidUuid == null) {
            return List.of();
        }
        Session session = SESSIONS.get(maidUuid);
        if (session == null) {
            return List.of();
        }
        return session.adapter.scriptMemory().recentTags(limit);
    }

    public static InteractionEngine.Result applyAiDelta(UUID maidUuid, Stats delta, Map<MoodModel.Mood, Double> moodBias, String memoryTag) {
        if (maidUuid == null || delta == null) {
            return null;
        }
        Session session = SESSIONS.computeIfAbsent(maidUuid, ignored -> createSession());
        if (memoryTag != null && !memoryTag.isBlank()) {
            session.adapter.scriptMemory().addMemoryTag(memoryTag);
        }

        Map<MoodModel.Mood, Double> bias = moodBias == null ? Map.of() : new EnumMap<>(moodBias);
        return session.engine.applyScriptDelta(delta, SocialContext.empty(), bias);
    }

    private static Map<String, Object> buildGiftItemBinding(Map<String, Object> payload) {
        String itemId = normalizeResourceLike(asString(payload.get("item_id")));
        int itemCount = asInt(payload.get("item_count"));
        String itemsCsv = asString(payload.get("items_csv"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", itemId);
        item.put("count", itemCount);
        item.put("is", (RuntimeCallable) args -> {
            requireArity("item.is", args, 1);
            String rhs = normalizeResourceLike(asString(args.get(0)));
            return !itemId.isEmpty() && !rhs.isEmpty() && itemId.equals(rhs);
        });
        item.put("has", (RuntimeCallable) args -> {
            requireArity("item.has", args, 1);
            String token = normalizeResourceLike(asString(args.get(0)));
            return containsCsvToken(itemsCsv, token);
        });
        return item;
    }

    private static Session createSession() {
        InteractionEngine engine = new InteractionEngine(Personality.neutral(), new Stats(0.35, 0.22, 0.45, 0.25));
        ScriptMemoryStore scriptMemory = new ScriptMemoryStore(2400, 240);
        MaidscriptEngineAdapter adapter = new MaidscriptEngineAdapter(
                engine,
                getSharedRegistry(),
                scriptMemory,
                5000
        );
        return new Session(engine, adapter);
    }

    private static MaidscriptScriptRegistry getSharedRegistry() {
        MaidscriptScriptRegistry local = sharedRegistry;
        if (local != null) {
            return local;
        }
        synchronized (MaidScriptEventService.class) {
            if (sharedRegistry == null) {
                sharedRegistry = loadRegistry();
            }
            return sharedRegistry;
        }
    }

    private static MaidscriptScriptRegistry loadRegistry() {
        try (InputStream stream = MaidScriptEventService.class.getClassLoader().getResourceAsStream(SCRIPT_RESOURCE)) {
            if (stream == null) {
                MoreMaidInteraction.LOGGER.warn("Could not find maidscript resource: {}", SCRIPT_RESOURCE);
                return MaidscriptScriptRegistry.empty();
            }
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return MaidscriptScriptRegistry.fromSource(SCRIPT_RESOURCE, source);
        } catch (IOException exception) {
            MoreMaidInteraction.LOGGER.error("Failed to load maidscript resource: {}", SCRIPT_RESOURCE, exception);
            return MaidscriptScriptRegistry.empty();
        }
    }

    private static double deriveIntensity(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return 0.45;
        }
        double total = asDouble(payload.get("total_count"));
        double quality = asDouble(payload.get("quality_score"));
        double distinct = asDouble(payload.get("distinct_count"));

        double intensity = 0.35 + 0.04 * Math.min(total, 20.0) + 0.10 * quality + 0.03 * Math.min(distinct, 6.0);
        return Math.max(0.1, Math.min(1.0, intensity));
    }

    private static void requireArity(String name, List<Object> args, int expect) {
        if (args == null || args.size() != expect) {
            int got = args == null ? 0 : args.size();
            throw new IllegalArgumentException(name + " expects " + expect + " args, got " + got);
        }
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalizeResourceLike(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsCsvToken(String csv, String token) {
        if (csv == null || csv.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String[] parts = csv.split(",");
        for (String part : parts) {
            String normalized = normalizeResourceLike(part);
            int star = normalized.indexOf('*');
            if (star >= 0) {
                normalized = normalized.substring(0, star);
            }
            if (normalized.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private record Session(InteractionEngine engine, MaidscriptEngineAdapter adapter) {
    }
}