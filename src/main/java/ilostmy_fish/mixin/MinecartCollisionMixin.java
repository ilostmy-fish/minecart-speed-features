package ilostmy_fish.mixin;

import ilostmy_fish.physics.ImpactPhysics;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a rail-movement collision with a living entity into a speed-based impact.
 *
 * <p>Vanilla still owns collision placement: {@link Entity#move} places the cart at the contact
 * point and zeroes the obstructed velocity component. This mixin captures the incoming velocity,
 * deals damage and knockback, then replaces that zeroed velocity with the residual speed from
 * {@link ImpactPhysics}. MSF's traversal still sees the shortened movement and terminates the
 * current tick's path normally.</p>
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartCollisionMixin extends Entity {
    @Unique
    private static final double minecartspeedfeatures$IMPACT_SEARCH_EXPANSION = 0.1;
    @Unique
    private static final double minecartspeedfeatures$MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12;
    @Unique
    private static final int minecartspeedfeatures$PASS_THROUGH_TICKS = 4;

    @Unique
    private Vec3d minecartspeedfeatures$velocityBeforeRailMove = Vec3d.ZERO;
    @Unique
    private Map<UUID, Integer> minecartspeedfeatures$passThroughEntities;

    protected MinecartCollisionMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(
            method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"
            )
    )
    private void minecartspeedfeatures$captureImpactVelocity(
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        this.minecartspeedfeatures$velocityBeforeRailMove = this.getVelocity();
    }

    @Inject(
            method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void minecartspeedfeatures$handleLivingEntityImpact(
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        if (this.getWorld().isClient || !this.horizontalCollision) {
            return;
        }

        Vec3d incomingVelocity = this.minecartspeedfeatures$velocityBeforeRailMove;
        double speedBlocksPerSecond = incomingVelocity.horizontalLength()
                * ImpactPhysics.TICKS_PER_SECOND;
        double damagePotential = ImpactPhysics.damagePotentialForSpeed(speedBlocksPerSecond);
        if (damagePotential <= 0.0) {
            return;
        }

        LivingEntity target = this.minecartspeedfeatures$findImpactTarget(incomingVelocity);
        if (target == null) {
            return;
        }

        ImpactPhysics.ImpactResult impact = ImpactPhysics.impact(
                speedBlocksPerSecond,
                target.getMaxHealth()
        );

        // Match Cammie's velocity-based knockback: horizontal knockback uses 90% of the cart's
        // incoming velocity and vertical knockback uses 20% of its total incoming speed.
        target.addVelocity(
                incomingVelocity.getX() * 0.9,
                incomingVelocity.length() * 0.2,
                incomingVelocity.getZ() * 0.9
        );
        target.damage(this.getDamageSources().generic(), (float)impact.damagePotential());

        double speedScale = impact.speedScale();
        Vec3d postCollisionVelocity = this.getVelocity();
        this.setVelocity(
                incomingVelocity.getX() * speedScale,
                postCollisionVelocity.getY(),
                incomingVelocity.getZ() * speedScale
        );

        if (impact.residualSpeedBlocksPerSecond() > 0.0) {
            this.minecartspeedfeatures$markPassThrough(target);
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void minecartspeedfeatures$agePassThroughEntities(CallbackInfo ci) {
        Map<UUID, Integer> passThrough = this.minecartspeedfeatures$passThroughEntities;
        if (passThrough == null || passThrough.isEmpty()) {
            return;
        }

        passThrough.replaceAll((uuid, ticks) -> ticks - 1);
        passThrough.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    /**
     * Entity collision shapes are selected through the moving minecart's collidesWith predicate.
     * A successfully penetrated mob is ignored briefly so the next tick can carry the cart beyond
     * the exact contact plane instead of immediately colliding with the same box again.
     */
    @Inject(
            method = "collidesWith(Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void minecartspeedfeatures$ignoreRecentlyImpactedEntity(
            Entity other,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.minecartspeedfeatures$isPassingThrough(other)) {
            cir.setReturnValue(false);
        }
    }

    /** Prevent an entity that was just struck from being immediately auto-mounted by a rideable cart. */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;startRiding(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean minecartspeedfeatures$skipAutoMountAfterImpact(Entity entity, Entity vehicle) {
        if (this.minecartspeedfeatures$isPassingThrough(entity)) {
            return false;
        }
        return entity.startRiding(vehicle);
    }

    /** Preserve the impact knockback instead of immediately applying vanilla minecart pushing. */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;pushAwayFrom(Lnet/minecraft/entity/Entity;)V"
            )
    )
    private void minecartspeedfeatures$skipVanillaPushAfterImpact(Entity entity, Entity minecart) {
        if (!this.minecartspeedfeatures$isPassingThrough(entity)) {
            entity.pushAwayFrom(minecart);
        }
    }

    @Unique
    private LivingEntity minecartspeedfeatures$findImpactTarget(Vec3d incomingVelocity) {
        List<Entity> candidates = this.getWorld().getOtherEntities(
                this,
                this.getBoundingBox().expand(minecartspeedfeatures$IMPACT_SEARCH_EXPANSION),
                entity -> entity instanceof LivingEntity living
                        && !(living instanceof PlayerEntity)
                        && living.isAlive()
                        && !living.hasVehicle()
                        && !this.minecartspeedfeatures$isPassingThrough(living)
                        && this.collidesWith(living)
        );
        if (candidates.isEmpty()) {
            return null;
        }

        LivingEntity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        Vec3d horizontalDirection = new Vec3d(
                incomingVelocity.getX(),
                0.0,
                incomingVelocity.getZ()
        );
        boolean hasDirection = horizontalDirection.lengthSquared()
                > minecartspeedfeatures$MIN_DIRECTION_LENGTH_SQUARED;

        for (Entity candidate : candidates) {
            LivingEntity living = (LivingEntity)candidate;
            if (hasDirection) {
                Vec3d offset = living.getPos().subtract(this.getPos());
                if (offset.dotProduct(horizontalDirection) < -0.05) {
                    continue;
                }
            }

            double distance = living.squaredDistanceTo(this);
            if (distance < nearestDistance) {
                nearest = living;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    @Unique
    private void minecartspeedfeatures$markPassThrough(Entity entity) {
        if (this.minecartspeedfeatures$passThroughEntities == null) {
            this.minecartspeedfeatures$passThroughEntities = new HashMap<>();
        }
        this.minecartspeedfeatures$passThroughEntities.put(
                entity.getUuid(),
                minecartspeedfeatures$PASS_THROUGH_TICKS
        );
    }

    @Unique
    private boolean minecartspeedfeatures$isPassingThrough(Entity entity) {
        Map<UUID, Integer> passThrough = this.minecartspeedfeatures$passThroughEntities;
        return passThrough != null && passThrough.containsKey(entity.getUuid());
    }
}
