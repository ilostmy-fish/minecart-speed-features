package ilostmy_fish.client.mixin;

import ilostmy_fish.client.trajectory.TrajectoryRenderHooks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps shadows and debug hitboxes on the same sampled path as the rendered entity. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(
            method = "render(Lnet/minecraft/entity/Entity;DDDFF"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V",
                    ordinal = 1
            )
    )
    private void minecartspeedfeatures$retainTrajectoryOffset(
            Entity entity,
            double x,
            double y,
            double z,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        Vec3d correction = TrajectoryRenderHooks.renderOffset(entity, tickDelta);
        if (TrajectoryRenderHooks.isNonZero(correction)) {
            matrices.translate(correction.getX(), correction.getY(), correction.getZ());
        }
    }
}
