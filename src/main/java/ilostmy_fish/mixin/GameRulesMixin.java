package ilostmy_fish.mixin;

import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.NoOpRuleCallback;
import net.minecraft.class_1928;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = class_1928.class, remap = false)
public abstract class GameRulesMixin {
    @Shadow(remap = false)
    private static class_1928.class_4313 method_8359(
            String name,
            class_1928.class_5198 category,
            class_1928.class_4314 type
    ) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void minecartspeedfeatures$registerMinecartMaxSpeed(CallbackInfo ci) {
        if (MinecartSpeedFeatures.MINECART_MAX_SPEED == null) {
            class_1928.class_4314 type = class_1928.class_4312.method_56115(
                    8,
                    1,
                    1000,
                    NoOpRuleCallback.INSTANCE
            );
            MinecartSpeedFeatures.MINECART_MAX_SPEED = method_8359(
                    "minecartMaxSpeed",
                    class_1928.class_5198.field_24100,
                    type
            );
        }
    }
}
