package ilostmy_fish;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

import java.util.function.BiConsumer;

public final class NoOpRuleCallback implements BiConsumer<MinecraftServer, GameRules.IntRule> {
    public static final NoOpRuleCallback INSTANCE = new NoOpRuleCallback();

    private NoOpRuleCallback() {
    }

    @Override
    public void accept(MinecraftServer server, GameRules.IntRule rule) {
    }
}
