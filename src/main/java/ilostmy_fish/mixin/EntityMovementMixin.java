package ilostmy_fish.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Allows minecarts to commit any nonzero movement accepted by vanilla collision resolution.
@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @ModifyConstant(
            method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
            constant = @Constant(doubleValue = 1.0E-7, ordinal = 1),
            require = 1,
            allow = 1
    )
    private double minecartspeedfeatures$removeMinecartMovementCutoff(double vanillaCutoff) {
        return (Object)this instanceof AbstractMinecartEntity ? 0.0 : vanillaCutoff;
    }
}
