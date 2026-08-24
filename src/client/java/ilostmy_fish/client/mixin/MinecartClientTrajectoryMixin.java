package ilostmy_fish.client.mixin;

import ilostmy_fish.client.trajectory.ClientTrajectoryManager;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes the server-authored trajectory own the client minecart's logical position. */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartClientTrajectoryMixin {
    @Shadow
    private int clientInterpolationSteps;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void minecartspeedfeatures$disableVanillaPositionLerp(CallbackInfo ci) {
        AbstractMinecartEntity minecart = (AbstractMinecartEntity)(Object)this;
        if (ClientTrajectoryManager.INSTANCE.isActive(minecart)) {
            this.clientInterpolationSteps = 0;
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void minecartspeedfeatures$applyTrajectoryEndpoint(CallbackInfo ci) {
        ClientTrajectoryManager.INSTANCE.completeTick(
                (AbstractMinecartEntity)(Object)this
        );
    }
}
