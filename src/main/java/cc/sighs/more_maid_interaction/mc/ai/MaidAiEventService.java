package cc.sighs.more_maid_interaction.mc.ai;

import cc.sighs.more_maid_interaction.MoreMaidInteraction;
import cc.sighs.more_maid_interaction.core.EngineSnapshot;
import cc.sighs.more_maid_interaction.core.MoodModel;
import cc.sighs.more_maid_interaction.core.Stats;
import cc.sighs.more_maid_interaction.mc.dsl.MaidScriptEventService;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.CharacterSetting;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidAiEventService {
    // 目标：尽量复用 TLM 的 LLM Site/Key/Provider，并把我们自己的状态压缩后发给 AI。

    private static final int RATE_LIMIT_TICKS = 40;
    private static final int MAX_PERSONA_CHARS = 420;
    private static final int MAX_TLM_SUMMARY_CHARS = 240;

    private static final double DELTA_CAP = 0.08;

    private static final ConcurrentHashMap<String, Long> LAST_REQUEST_TICK = new ConcurrentHashMap<>();

    private MaidAiEventService() {
    }

    public static void requestGiftReaction(EntityMaid maid, ServerPlayer player, Map<String, Object> giftPayload) {
        if (maid == null || player == null) {
            return;
        }
        if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel)) {
            return;
        }

        UUID maidUuid = maid.getUUID();
        long tick = maid.level().getGameTime();
        if (!rateLimit(maidUuid, "gift", tick)) {
            return;
        }

        MaidAIChatManager chatManager = maid.getAiChatManager();
        if (chatManager == null || chatManager.getLLMSite() == null) {
            return;
        }
        LLMClient client;
        try {
            client = chatManager.getLLMSite().client();
        } catch (Throwable t) {
            MoreMaidInteraction.LOGGER.debug("LLM site not ready for maid {}", maidUuid, t);
            return;
        }
        if (client == null) {
            return;
        }

        EngineSnapshot snapshot = MaidScriptEventService.snapshot(maidUuid);
        if (snapshot == null) {
            return;
        }

        List<String> recentTags = MaidScriptEventService.recentMemoryTags(maidUuid, 6);

        String persona = buildPersona(chatManager, maid);
        String tlmSummary = buildTlmSummary(chatManager);
        String developer = developerInstruction();
        String userJson = buildGiftUserJson(snapshot, giftPayload, recentTags, tlmSummary);

        long gameTime = maid.level().getGameTime();
        List<LLMMessage> messages = new ArrayList<>(4);
        if (!persona.isBlank()) {
            messages.add(LLMMessage.systemChat(maid, persona));
        }
        messages.add(new LLMMessage(Role.DEVELOPER, developer, gameTime));
        messages.add(LLMMessage.userChat(maid, userJson));

        client.chat(new EventCallback(chatManager, messages, maid, player, maidUuid, "gift", tick));
    }

    private static boolean rateLimit(UUID maidUuid, String eventKey, long tick) {
        String key = maidUuid + "|" + eventKey;
        Long prev = LAST_REQUEST_TICK.put(key, tick);
        return prev == null || tick - prev >= RATE_LIMIT_TICKS;
    }

    private static String buildPersona(MaidAIChatManager chatManager, EntityMaid maid) {
        try {
            var opt = chatManager.getSetting();
            if (opt.isEmpty()) {
                return "";
            }
            CharacterSetting setting = opt.get();
            String lang = safeLang(chatManager.getChatLanguage());
            String persona = safe(setting.getSetting(maid, lang));
            if (persona.isBlank()) {
                return "";
            }
            // 控 token：截断人物设定。
            if (persona.length() > MAX_PERSONA_CHARS) {
                persona = persona.substring(0, MAX_PERSONA_CHARS);
            }
            return persona;
        } catch (Throwable t) {
            return "";
        }
    }

    private static String buildTlmSummary(MaidAIChatManager chatManager) {
        try {
            String sum = safe(chatManager.getCompressedSummary());
            if (sum.isBlank()) {
                return "";
            }
            if (sum.length() > MAX_TLM_SUMMARY_CHARS) {
                sum = sum.substring(0, MAX_TLM_SUMMARY_CHARS);
            }
            return sum;
        } catch (Throwable t) {
            return "";
        }
    }

    private static String safeLang(String raw) {
        String lang = safe(raw).trim().toLowerCase(Locale.ROOT);
        if (lang.isBlank()) {
            return "zh_cn";
        }
        return lang;
    }

    private static String developerInstruction() {
        // 这个 developer 消息会成为“硬约束”。越短越省 token，但要保证结构稳定。
        return "输出必须是单行JSON，不要代码块/markdown。结构固定：" +
                "{\"v\":1,\"say\":\"...\",\"d\":{\"favor\":0,\"bond\":0,\"sincerity\":0,\"novelty\":0},\"mem\":\"...\"}. " +
                "say为中文<=60字且不换行；mem<=24字；d每项范围[-0.08,0.08]；不要输出任何其他键。";
    }

    private static String buildGiftUserJson(EngineSnapshot snapshot, Map<String, Object> giftPayload, List<String> recentTags, String tlmSummary) {
        JsonObject obj = new JsonObject();
        obj.addProperty("ev", "gift");

        String itemId = asString(giftPayload.get("item_id"));
        int count = asInt(giftPayload.get("item_count"));
        int total = asInt(giftPayload.get("total_count"));
        double quality = asDouble(giftPayload.get("quality_score"));
        boolean rare = asBool(giftPayload.get("rare"));

        obj.addProperty("it", normalize(itemId));
        obj.addProperty("n", count);
        obj.addProperty("t", total);
        obj.addProperty("q", (int) Math.round(quality * 100));
        obj.addProperty("r", rare ? 1 : 0);

        JsonObject st = new JsonObject();
        st.addProperty("f", (int) Math.round(snapshot.stats().favor() * 100));
        st.addProperty("b", (int) Math.round(snapshot.stats().bond() * 100));
        st.addProperty("s", (int) Math.round(snapshot.stats().sincerity() * 100));
        st.addProperty("n", (int) Math.round(snapshot.stats().novelty() * 100));
        obj.add("st", st);

        JsonObject em = new JsonObject();
        em.addProperty("p", (int) Math.round(snapshot.emotion().pleasure() * 100));
        em.addProperty("a", (int) Math.round(snapshot.emotion().arousal() * 100));
        em.addProperty("d", (int) Math.round(snapshot.emotion().dominance() * 100));
        obj.add("em", em);

        obj.addProperty("mood", safe(snapshot.moodTop() == null ? "" : snapshot.moodTop().name()));

        if (recentTags != null && !recentTags.isEmpty()) {
            var arr = new com.google.gson.JsonArray();
            for (String tag : recentTags) {
                if (tag == null || tag.isBlank()) continue;
                String t0 = tag.trim();
                if (t0.length() > 18) t0 = t0.substring(0, 18);
                arr.add(t0);
            }
            obj.add("mem", arr);
        }

        if (tlmSummary != null && !tlmSummary.isBlank()) {
            obj.addProperty("tlm", tlmSummary);
        }

        return obj.toString();
    }

    private static String normalize(String raw) {
        String v = safe(raw).trim().toLowerCase(Locale.ROOT);
        return v;
    }

    private static String safe(String raw) {
        return raw == null ? "" : raw;
    }

    private static String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null ? 0.0 : Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static boolean asBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = asString(v).trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private static double clampDelta(double v) {
        if (v > DELTA_CAP) return DELTA_CAP;
        if (v < -DELTA_CAP) return -DELTA_CAP;
        return v;
    }

    private static String sanitizeSay(String raw) {
        String s = safe(raw);
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        while (s.contains("  ")) {
            s = s.replace("  ", " ");
        }
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        return s;
    }

    private static AiResponse parseResponse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }

        try {
            JsonElement el = JsonParser.parseString(s);
            if (!el.isJsonObject()) {
                return null;
            }
            JsonObject obj = el.getAsJsonObject();
            String say = getString(obj, "say");
            if (say.isBlank()) {
                say = getString(obj, "s");
            }

            JsonObject d = obj.has("d") && obj.get("d").isJsonObject() ? obj.getAsJsonObject("d") : null;
            double favor = d == null ? 0 : getDouble(d, "favor", getDouble(d, "f", 0));
            double bond = d == null ? 0 : getDouble(d, "bond", getDouble(d, "b", 0));
            double sincerity = d == null ? 0 : getDouble(d, "sincerity", getDouble(d, "s", 0));
            double novelty = d == null ? 0 : getDouble(d, "novelty", getDouble(d, "n", 0));

            String mem = getString(obj, "mem");
            if (mem.isBlank()) {
                mem = getString(obj, "m");
            }

            return new AiResponse(
                    sanitizeSay(say),
                    new Stats(clampDelta(favor), clampDelta(bond), clampDelta(sincerity), clampDelta(novelty)),
                    sanitizeMem(mem)
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String sanitizeMem(String raw) {
        String s = safe(raw).replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }
        return s;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return "";
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return "";
        }
        try {
            return el.getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        if (obj == null || key == null || !obj.has(key)) {
            return def;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return def;
        }
        try {
            return el.getAsDouble();
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static final class EventCallback extends LLMCallback {
        private final EntityMaid maid;
        private final ServerPlayer player;
        private final UUID maidUuid;
        private final String eventKey;
        private final long requestTick;

        private EventCallback(
                MaidAIChatManager chatManager,
                List<LLMMessage> messages,
                EntityMaid maid,
                ServerPlayer player,
                UUID maidUuid,
                String eventKey,
                long requestTick
        ) {
            super(chatManager, messages, false);
            this.maid = maid;
            this.player = player;
            this.maidUuid = maidUuid;
            this.eventKey = eventKey;
            this.requestTick = requestTick;
        }

        @Override
        public void onSuccess(ResponseChat response) {
            String raw = response == null ? "" : String.valueOf(response.getChatText());
            AiResponse parsed = parseResponse(raw);
            if (parsed == null) {
                parsed = new AiResponse("…", new Stats(0, 0, 0, 0), "");
            }

            AiResponse finalParsed = parsed;
            runOnServerThread(() -> {
                try {
                    if (maid != null && maid.isAlive()) {
                        String say = finalParsed.say;
                        if (!say.isBlank()) {
                            maid.getChatBubbleManager().addTextChatBubbleIfTimeout(say, 30);
                        }
                    }
                    // 把 AI 的“剧情微调”映射到我们的情绪引擎（强限制）。
                    MaidScriptEventService.applyAiDelta(maidUuid, finalParsed.delta, Map.of(), finalParsed.mem);
                } catch (Throwable t) {
                    MoreMaidInteraction.LOGGER.warn("AI apply failed, maid={}, event={}, tick={}", maidUuid, eventKey, requestTick, t);
                }
            });
        }

        @Override
        public void onFailure(HttpRequest request, Throwable throwable, int httpCode) {
            runOnServerThread(() -> {
                MoreMaidInteraction.LOGGER.warn("AI chat failed, maid={}, event={}, code={}", maidUuid, eventKey, httpCode, throwable);
            });
        }

        @Override
        public void onFunctionCall(com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message message, LLMClient client) {
            // 强制禁用工具调用：避免多轮与不可控的 token 消耗。
            onFailure(null, new IllegalStateException("tool call blocked"), 0);
        }
    }

    private record AiResponse(String say, Stats delta, String mem) {
    }
}