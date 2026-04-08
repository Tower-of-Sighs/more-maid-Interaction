package cc.sighs.more_maid_interaction.dsl.runtime;

import java.util.List;

@FunctionalInterface
public interface RuntimeCallable {
    Object call(List<Object> args);
}