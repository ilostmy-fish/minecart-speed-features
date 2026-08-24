package ilostmy_fish.client.mixin;

import ilostmy_fish.client.trajectory.TrajectoryRenderHooks;
import ilostmy_fish.trajectory.TrajectorySample;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies the cart renderer with server-authored position and direction samples. */
@Mixin(MinecartEntityRenderer.class)
public abstract class MinecartEntityRendererMixin {
    @Unique
    private static final String minecartspeedfeatures$RENDER_METHOD =
            "render(Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;FF"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    @Unique
    private static final double minecartspeedfeatures$TANGENT_EPSILON = 1.0E-9;

    @Unique
    @Nullable
    private TrajectorySample minecartspeedfeatures$renderSample;

    @Inject(method = minecartspeedfeatures$RENDER_METHOD, at = @At("HEAD"))
    private void minecartspeedfeatures$captureTrajectorySample(
            AbstractMinecartEntity minecart,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        this.minecartspeedfeatures$renderSample = TrajectoryRenderHooks.minecartSample(
                minecart,
                tickDelta
        );
    }

    @Inject(method = minecartspeedfeatures$RENDER_METHOD, at = @At("RETURN"))
    private void minecartspeedfeatures$clearTrajectorySample(
            AbstractMinecartEntity minecart,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        this.minecartspeedfeatures$renderSample = null;
    }

    @Redirect(
            method = minecartspeedfeatures$RENDER_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;"
                            + "snapPositionToRail(DDD)Lnet/minecraft/util/math/Vec3d;"
            )
    )
    private Vec3d minecartspeedfeatures$trajectoryPosition(
            AbstractMinecartEntity minecart,
            double x,
            double y,
            double z
    ) {
        TrajectorySample sample = this.minecartspeedfeatures$renderSample;
        return sample == null ? minecart.snapPositionToRail(x, y, z) : new Vec3d(x, y, z);
    }

    @Redirect(
            method = minecartspeedfeatures$RENDER_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;"
                            + "snapPositionToRailWithOffset(DDDD)Lnet/minecraft/util/math/Vec3d;"
            )
    )
    private Vec3d minecartspeedfeatures$trajectoryDirectionSample(
            AbstractMinecartEntity minecart,
            double x,
            double y,
            double z,
            double offset
    ) {
        TrajectorySample sample = this.minecartspeedfeatures$renderSample;
        if (sample == null) {
            return minecart.snapPositionToRailWithOffset(x, y, z, offset);
        }

        Vec3d tangent = sample.tangent();
        double horizontalLength = Math.hypot(tangent.getX(), tangent.getZ());
        if (horizontalLength <= minecartspeedfeatures$TANGENT_EPSILON) {
            return new Vec3d(x, y, z);
        }
        return new Vec3d(x, y, z).add(
                tangent.getX() / horizontalLength * offset,
                tangent.getY() / horizontalLength * offset,
                tangent.getZ() / horizontalLength * offset
        );
    }
}
