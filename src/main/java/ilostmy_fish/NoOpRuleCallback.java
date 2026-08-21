package ilostmy_fish;

import java.util.function.BiConsumer;

/** A dependency-free gamerule callback. */
public final class NoOpRuleCallback implements BiConsumer<Object, Object> {
    public static final NoOpRuleCallback INSTANCE = new NoOpRuleCallback();

    private NoOpRuleCallback() {
    }

    @Override
    public void accept(Object server, Object rule) {
        // Intentionally empty. This gamerule has no side-channel state to synchronize.
    }
}
