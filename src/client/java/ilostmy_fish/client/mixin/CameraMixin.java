package ilostmy_fish.client.mixin;

import ilostmy_fish.interpolation.VisualInterpolationHooks;
import net.minecraft.class_1297;
import net.minecraft.class_1922;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a rider's camera on the same reconstructed visual path as the minecart. */
@Mixin(value = class_4184.class, remap = false)
public abstract class CameraMixin {
    @Shadow(remap = false)
    public abstract class_243 method_19326();

    @Shadow(remap = false)
    private void method_19322(class_243 pos) {
        throw new AssertionError();
    }

    @Inject(
            method = "method_19321(Lnet/minecraft/class_1922;Lnet/minecraft/class_1297;ZZF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_4184;method_19327(DDD)V",
                    shift = At.Shift.AFTER,
                    ordinal = 0,
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void minecartspeedfeatures$offsetRiderCamera(
            class_1922 area,
            class_1297 focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        class_243 correction = VisualInterpolationHooks.cameraOffset(focusedEntity, tickDelta);
        if (!VisualInterpolationHooks.isZero(correction)) {
            this.method_19322(this.method_19326().method_1019(correction));
        }
    }
}
