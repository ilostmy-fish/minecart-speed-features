package ilostmy_fish.client.mixin;

import ilostmy_fish.client.trajectory.TrajectoryRenderHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies trajectory-derived render and visibility offsets to minecarts and their passengers. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(
            method = "shouldRender(Lnet/minecraft/entity/Entity;"
                    + "Lnet/minecraft/client/render/Frustum;DDD)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void minecartspeedfeatures$trajectoryVisibility(
            Entity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValue()) {
            return;
        }

        float tickDelta = MinecraftClient.getInstance()
                .getRenderTickCounter()
                .getTickDelta(false);
        Vec3d visibilityOffset = TrajectoryRenderHooks.visibilityOffset(entity, tickDelta);
        if (TrajectoryRenderHooks.isNonZero(visibilityOffset)
                && entity.shouldRender(
                        cameraX - visibilityOffset.getX(),
                        cameraY - visibilityOffset.getY(),
                        cameraZ - visibilityOffset.getZ()
                )
                && frustum.isVisible(
                        entity.getVisibilityBoundingBox()
                                .expand(0.5)
                                .offset(visibilityOffset)
                )) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "getPositionOffset(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void minecartspeedfeatures$trajectoryRenderOffset(
            Entity entity,
            float tickDelta,
            CallbackInfoReturnable<Vec3d> cir
    ) {
        Vec3d correction = TrajectoryRenderHooks.renderOffset(entity, tickDelta);
        if (TrajectoryRenderHooks.isNonZero(correction)) {
            Vec3d vanilla = cir.getReturnValue();
            cir.setReturnValue(vanilla == null ? correction : vanilla.add(correction));
        }
    }
}
