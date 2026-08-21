package ilostmy_fish.client.mixin;

import ilostmy_fish.interpolation.VisualInterpolationHooks;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_897;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a render-only path correction without replacing entity networking or renderer logic. */
@Mixin(value = class_897.class, remap = false)
public abstract class EntityRendererMixin {
    @Inject(
            method = "method_23169(Lnet/minecraft/class_1297;F)Lnet/minecraft/class_243;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void minecartspeedfeatures$railPathOffset(
            class_1297 entity,
            float tickDelta,
            CallbackInfoReturnable<class_243> cir
    ) {
        class_243 correction = VisualInterpolationHooks.renderOffset(entity, tickDelta);
        if (!VisualInterpolationHooks.isZero(correction)) {
            class_243 vanilla = cir.getReturnValue();
            cir.setReturnValue(vanilla == null ? correction : vanilla.method_1019(correction));
        }
    }
}
