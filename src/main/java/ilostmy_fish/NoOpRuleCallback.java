package ilostmy_fish;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

import java.util.function.BiConsumer;

/**
 * A dependency-free gamerule callback.
 */
public final class NoOpRuleCallback implements BiConsumer<MinecraftServer, GameRules.IntRule> {
    public static final NoOpRuleCallback INSTANCE = new NoOpRuleCallback();

    private NoOpRuleCallback() {
    }

    @Override
    public void accept(MinecraftServer server, GameRules.IntRule rule) {
        // Intentionally empty. This gamerule has no side-channel state to synchronize.
    }
}
