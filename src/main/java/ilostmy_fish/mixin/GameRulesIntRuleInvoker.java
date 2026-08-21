package ilostmy_fish.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BiConsumer;

@Mixin(GameRules.IntRule.class)
public interface GameRulesIntRuleInvoker {
    @Invoker("create")
    static GameRules.Type<GameRules.IntRule> minecartspeedfeatures$create(
            int initialValue,
            int min,
            int max,
            BiConsumer<MinecraftServer, GameRules.IntRule> changeCallback
    ) {
        throw new AssertionError();
    }
}
