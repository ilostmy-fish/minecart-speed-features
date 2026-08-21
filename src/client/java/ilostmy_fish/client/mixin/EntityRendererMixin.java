package ilostmy_fish.client.mixin;

import ilostmy_fish.interpolation.VisualInterpolationHooks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a render-only path correction without replacing entity networking or renderer logic.
 */
@Mixin(value = EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(
            method = "getPositionOffset(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void minecartspeedfeatures$railPathOffset(
            Entity entity,
            float tickDelta,
            CallbackInfoReturnable<Vec3d> cir
    ) {
        Vec3d correction = VisualInterpolationHooks.renderOffset(entity, tickDelta);
        if (!VisualInterpolationHooks.isZero(correction)) {
            Vec3d vanilla = cir.getReturnValue();
            cir.setReturnValue(vanilla == null ? correction : vanilla.add(correction));
        }
    }
}
