package ilostmy_fish.mixin;

import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.NoOpRuleCallback;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRules.class)
public abstract class GameRulesMixin {
    @Shadow
    private static <T extends GameRules.Rule<T>> GameRules.Key<T> register(
            String name,
            GameRules.Category category,
            GameRules.Type<T> type
    ) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void minecartspeedfeatures$registerMinecartMaxSpeed(CallbackInfo ci) {
        if (MinecartSpeedFeatures.MINECART_MAX_SPEED == null) {
            GameRules.Type<GameRules.IntRule> type = GameRulesIntRuleInvoker.minecartspeedfeatures$create(
                    8,
                    1,
                    1000,
                    NoOpRuleCallback.INSTANCE
            );
            MinecartSpeedFeatures.MINECART_MAX_SPEED = register(
                    "minecartMaxSpeed",
                    GameRules.Category.MISC,
                    type
            );
        }
    }
}
