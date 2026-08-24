package ilostmy_fish.client.mixin;

import ilostmy_fish.client.trajectory.TrajectoryRenderHooks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a rider's camera on the same authoritative trajectory as the minecart. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    public abstract Vec3d getPos();

    @Shadow
    protected void setPos(Vec3d pos) {
        throw new AssertionError();
    }

    @Inject(
            method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            ),
            require = 0
    )
    private void minecartspeedfeatures$trajectoryCameraOffset(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        Vec3d correction = TrajectoryRenderHooks.cameraOffset(focusedEntity, tickDelta);
        if (TrajectoryRenderHooks.isNonZero(correction)) {
            this.setPos(this.getPos().add(correction));
        }
    }
}
