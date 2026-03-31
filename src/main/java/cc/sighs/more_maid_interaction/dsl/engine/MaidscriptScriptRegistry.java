package cc.sighs.more_maid_interaction.dsl.engine;

import cc.sighs.more_maid_interaction.dsl.runtime.ExecutionResult;
import cc.sighs.more_maid_interaction.dsl.runtime.MaidscriptRuntime;
import cc.sighs.more_maid_interaction.dsl.runtime.RuntimeInput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MaidscriptScriptRegistry {
    private final List<CompiledScript> scripts;

    private MaidscriptScriptRegistry(List<CompiledScript> scripts) {
        this.scripts = List.copyOf(scripts);
    }

    public static MaidscriptScriptRegistry empty() {
        return new MaidscriptScriptRegistry(List.of());
    }

    public static MaidscriptScriptRegistry fromSource(String sourceName, String source) {
        Objects.requireNonNull(sourceName);
        Objects.requireNonNull(source);
        return new MaidscriptScriptRegistry(List.of(new CompiledScript(sourceName, MaidscriptRuntime.fromSource(source))));
    }

    public static MaidscriptScriptRegistry fromSources(Map<String, String> sources) {
        Objects.requireNonNull(sources);
        List<CompiledScript> loaded = new ArrayList<>();
        sources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> loaded.add(new CompiledScript(e.getKey(), MaidscriptRuntime.fromSource(e.getValue()))));
        return new MaidscriptScriptRegistry(loaded);
    }

    public static MaidscriptScriptRegistry fromDirectory(Path root) {
        Objects.requireNonNull(root);
        if (!Files.exists(root)) {
            return empty();
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".ms"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan maidscript directory: " + root, e);
        }

        List<CompiledScript> loaded = new ArrayList<>();
        for (Path path : files) {
            String src;
            try {
                src = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read script: " + path, e);
            }
            loaded.add(new CompiledScript(root.relativize(path).toString().replace('\\', '/'), MaidscriptRuntime.fromSource(src)));
        }
        return new MaidscriptScriptRegistry(loaded);
    }

    public List<String> sourceNames() {
        List<String> names = new ArrayList<>(scripts.size());
        for (CompiledScript script : scripts) {
            names.add(script.sourceName);
        }
        return names;
    }

    public ExecutionResult execute(String eventId, RuntimeInput input) {
        ExecutionResult merged = new ExecutionResult();
        for (CompiledScript script : scripts) {
            ExecutionResult partial = script.runtime.execute(eventId, input);
            mergeInto(merged, partial);
            if (partial.stopped()) {
                merged.setStopped(true);
                break;
            }
        }
        return merged;
    }

    private static void mergeInto(ExecutionResult target, ExecutionResult source) {
        for (Map.Entry<String, Double> entry : source.delta().entrySet()) {
            target.addDelta(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Double> entry : source.moodBias().entrySet()) {
            target.addMoodBias(entry.getKey(), entry.getValue());
        }
        source.memoryAdds().forEach(target::addMemoryTag);
        source.storyFlagsSet().forEach(target::addStoryFlag);
        source.actions().forEach(action -> target.addAction(action.type(), new LinkedHashMap<>(action.payload())));
        if (source.stopped()) {
            target.setStopped(true);
        }
    }

    private record CompiledScript(String sourceName, MaidscriptRuntime runtime) {
        private CompiledScript {
            Objects.requireNonNull(sourceName);
            Objects.requireNonNull(runtime);
        }
    }
}
