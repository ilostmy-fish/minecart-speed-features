package ilostmy_fish.mixin;

import ilostmy_fish.MinecartSpeedFeatures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the configurable speed limit without owning rail behavior or traversal. */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartSpeedMixin extends Entity {
    protected MinecartSpeedMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "getMaxSpeed()D", at = @At("RETURN"), cancellable = true)
    private void minecartspeedfeatures$getMaxSpeed(CallbackInfoReturnable<Double> cir) {
        if (MinecartSpeedFeatures.MINECART_MAX_SPEED != null) {
            cir.setReturnValue(this.minecartspeedfeatures$maxSpeedBlocksPerTick());
        }
    }

    /**
     * Vanilla separately caps rail-constrained velocity at 2 blocks/tick before applying
     * getMaxSpeed. Use the configured magnitude there so settings above 40 blocks/second can
     * actually reach their requested speed while retaining the rest of vanilla moveOnRail.
     */
    @ModifyArg(
            method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"),
            index = 0
    )
    private double minecartspeedfeatures$replaceInternalVelocityCap(double vanillaCap) {
        return MinecartSpeedFeatures.MINECART_MAX_SPEED == null
                ? vanillaCap
                : this.minecartspeedfeatures$maxSpeedBlocksPerTick();
    }

    @Unique
    private double minecartspeedfeatures$maxSpeedBlocksPerTick() {
        double result = this.getWorld().getGameRules()
                .getInt(MinecartSpeedFeatures.MINECART_MAX_SPEED) / 20.0;
        if (this.isTouchingWater()) {
            result *= 0.5;
        }
        return result;
    }
}
