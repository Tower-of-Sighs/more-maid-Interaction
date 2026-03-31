package cc.sighs.more_maid_interaction.dsl.runtime;

import java.util.Map;

public record ReactionAction(String type, Map<String, Object> payload) {
}
