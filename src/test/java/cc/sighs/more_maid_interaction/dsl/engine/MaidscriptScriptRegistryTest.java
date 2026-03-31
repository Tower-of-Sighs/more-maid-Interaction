package cc.sighs.more_maid_interaction.dsl.engine;

import cc.sighs.more_maid_interaction.dsl.runtime.ExecutionResult;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeInput;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MaidscriptScriptRegistryTest {
    @Test
    public void loadDirectoryAndMergeScripts(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("01_a.ms");
        Path b = dir.resolve("02_b.ms");
        Path note = dir.resolve("note.txt");

        Files.writeString(a, "on_event \"builtin.player.chat_to_maid\" { say(\"a\") }", StandardCharsets.UTF_8);
        Files.writeString(b, "on_event \"builtin.player.chat_to_maid\" { say(\"b\") }", StandardCharsets.UTF_8);
        Files.writeString(note, "ignore", StandardCharsets.UTF_8);

        MaidscriptScriptRegistry registry = MaidscriptScriptRegistry.fromDirectory(dir);
        ExecutionResult result = registry.execute("builtin.player.chat_to_maid", RuntimeInput.empty("builtin.player.chat_to_maid"));

        Assertions.assertEquals(2, registry.sourceNames().size());
        Assertions.assertEquals(2, result.actions().size());
        Assertions.assertEquals("a", result.actions().get(0).payload().get("text"));
        Assertions.assertEquals("b", result.actions().get(1).payload().get("text"));
    }

    @Test
    public void stopPreventsFollowingScripts(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("01_stop.ms");
        Path b = dir.resolve("02_next.ms");

        Files.writeString(a, "on_event \"builtin.player.chat_to_maid\" { say(\"first\") stop }", StandardCharsets.UTF_8);
        Files.writeString(b, "on_event \"builtin.player.chat_to_maid\" { say(\"second\") }", StandardCharsets.UTF_8);

        MaidscriptScriptRegistry registry = MaidscriptScriptRegistry.fromDirectory(dir);
        ExecutionResult result = registry.execute("builtin.player.chat_to_maid", RuntimeInput.empty("builtin.player.chat_to_maid"));

        Assertions.assertTrue(result.stopped());
        Assertions.assertEquals(1, result.actions().size());
        Assertions.assertEquals("first", result.actions().get(0).payload().get("text"));
    }
}
