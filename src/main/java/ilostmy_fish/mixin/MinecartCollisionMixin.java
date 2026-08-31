package ilostmy_fish.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.collision.ImpactContactTracker;
import ilostmy_fish.damage.MinecartImpactDamageSource;
import ilostmy_fish.physics.ImpactPhysics;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

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
    private static final double minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_BPS =
            ImpactPhysics.DAMAGE_SPEED_OFFSET_BPS;
    @Unique
    private static final double minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_PER_TICK =
            minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_BPS / ImpactPhysics.TICKS_PER_SECOND;
    @Unique
    private static final double minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_SQUARED =
            minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_PER_TICK
                    * minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_PER_TICK;
    @Unique
    private static final double minecartspeedfeatures$IMPACT_SEARCH_EXPANSION = 0.1;
    @Unique
    private static final double minecartspeedfeatures$MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12;

    @Unique
    private Vec3d minecartspeedfeatures$velocityBeforeRailMove = Vec3d.ZERO;
    @Unique
    private ImpactContactTracker minecartspeedfeatures$impactContacts;

    protected MinecartCollisionMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    /**
     * Test: make minecarts solid to other entities, matching boat-style collidability.
     */
    @Override
    public boolean isCollidable() {
        return true;
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
        LivingEntity target = null;
        boolean suppressSpecialEffects = false;

        // Once a target is latched, another clipped movement is itself contact evidence even when
        // the boxes are only touching rather than strictly overlapping. Do this before the impact
        // speed threshold so a slow, continuously blocked cart does not accidentally re-arm.
        if (this.minecartspeedfeatures$hasImpactContacts()) {
            target = this.minecartspeedfeatures$findImpactTarget(incomingVelocity);
            if (target != null && this.minecartspeedfeatures$isImpactSuppressed(target)) {
                suppressSpecialEffects = true;
                this.minecartspeedfeatures$recordImpactContact(target);
            }
        }

        double speedBlocksPerSecond = incomingVelocity.horizontalLength()
                * ImpactPhysics.TICKS_PER_SECOND;
        if (speedBlocksPerSecond <= minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_BPS) {
            return;
        }

        double damagePotential = ImpactPhysics.damagePotentialForSpeed(speedBlocksPerSecond);
        if (damagePotential <= 0.0) {
            return;
        }

        if (target == null) {
            target = this.minecartspeedfeatures$findImpactTarget(incomingVelocity);
        }
        if (target == null) {
            return;
        }

        if (!suppressSpecialEffects) {
            suppressSpecialEffects = this.minecartspeedfeatures$isImpactSuppressed(target);
        }

        ImpactPhysics.ImpactResult impact = ImpactPhysics.impact(
                speedBlocksPerSecond,
                target.getMaxHealth()
        );

        if (!suppressSpecialEffects) {
            // Inspired by Cammie's velocity-based knockback: horizontal knockback uses 80% of the
            // cart's incoming velocity and vertical knockback uses 20% of its total incoming speed.
            target.addVelocity(
                    incomingVelocity.getX() * 0.8,
                    incomingVelocity.length() * 0.2,
                    incomingVelocity.getZ() * 0.8
            );

            int damagePercent = MinecartSpeedFeatures.MINECART_DAMAGE_PERCENT == null
                    ? 100
                    : this.getWorld().getGameRules().getInt(MinecartSpeedFeatures.MINECART_DAMAGE_PERCENT);
            double scaledDamage = impact.damagePotential() * damagePercent / 100.0;
            List<Entity> passengers = this.getPassengerList();
            Entity attributedPassenger = passengers.isEmpty() ? null : passengers.getFirst();
            target.damage(
                    MinecartImpactDamageSource.create(
                            this.getDamageSources(),
                            this,
                            attributedPassenger
                    ),
                    (float) scaledDamage
            );
        }

        double speedScale = impact.speedScale();
        Vec3d postCollisionVelocity = this.getVelocity();
        this.setVelocity(
                incomingVelocity.getX() * speedScale,
                postCollisionVelocity.getY(),
                incomingVelocity.getZ() * speedScale
        );
        this.minecartspeedfeatures$recordImpactContact(target);
    }

    /**
     * Vanilla only runs rideable-minecart mob pickup above 2 blocks/second. MSF inverts that gate:
     * slow carts may pick mobs up, while faster carts use impact handling instead.
     */
    @ModifyExpressionValue(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;horizontalLengthSquared()D"
            )
    )
    private double minecartspeedfeatures$limitAutomaticMobPickupToSlowCarts(double horizontalSpeedSquared) {
        // The vanilla comparison immediately after this call is `> 0.01`. Return a value on the
        // corresponding side of that comparison instead of replacing tick() or duplicating its
        // entity-interaction loop, keeping the modification narrowly scoped to the pickup gate.
        return horizontalSpeedSquared <= minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_SQUARED
                ? 1.0
                : 0.0;
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void minecartspeedfeatures$refreshImpactContactsAtTickStart(CallbackInfo ci) {
        this.minecartspeedfeatures$refreshImpactContacts(false);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void minecartspeedfeatures$refreshImpactContactsAtTickEnd(CallbackInfo ci) {
        this.minecartspeedfeatures$refreshImpactContacts(true);
    }

    @Unique
    private LivingEntity minecartspeedfeatures$findImpactTarget(Vec3d incomingVelocity) {
        List<Entity> candidates = this.getWorld().getOtherEntities(
                this,
                this.getBoundingBox().expand(minecartspeedfeatures$IMPACT_SEARCH_EXPANSION),
                entity -> entity instanceof LivingEntity living
                        && living.isAlive()
                        && !living.hasVehicle()
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
            LivingEntity living = (LivingEntity) candidate;
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
    private void minecartspeedfeatures$recordImpactContact(Entity entity) {
        if (this.minecartspeedfeatures$impactContacts == null) {
            this.minecartspeedfeatures$impactContacts = new ImpactContactTracker();
        }
        this.minecartspeedfeatures$impactContacts.recordContact(
                entity.getUuid(),
                this.getWorld().getTime()
        );
    }

    @Unique
    private boolean minecartspeedfeatures$isImpactSuppressed(Entity entity) {
        ImpactContactTracker contacts = this.minecartspeedfeatures$impactContacts;
        return contacts != null && contacts.isTracked(entity.getUuid());
    }

    @Unique
    private boolean minecartspeedfeatures$hasImpactContacts() {
        ImpactContactTracker contacts = this.minecartspeedfeatures$impactContacts;
        return contacts != null && !contacts.isEmpty();
    }

    @Unique
    private int minecartspeedfeatures$getImpactHysteresisTicks() {
        return MinecartSpeedFeatures.MINECART_IMPACT_HYSTERESIS_TICKS == null
                ? 1
                : this.getWorld().getGameRules().getInt(
                MinecartSpeedFeatures.MINECART_IMPACT_HYSTERESIS_TICKS
        );
    }

    /**
     * Strict AABB overlap is continuous-contact evidence for already latched entities. Movement
     * clips refresh the same latch in the impact handler, so face-to-face contact is also covered.
     */
    @Unique
    private void minecartspeedfeatures$refreshImpactContacts(boolean expireSeparatedContacts) {
        if (this.getWorld().isClient) {
            return;
        }

        ImpactContactTracker contacts = this.minecartspeedfeatures$impactContacts;
        if (contacts == null || contacts.isEmpty()) {
            return;
        }

        long currentTick = this.getWorld().getTime();
        Box cartBox = this.getBoundingBox();
        List<Entity> overlappingContacts = this.getWorld().getOtherEntities(
                this,
                cartBox,
                entity -> contacts.isTracked(entity.getUuid())
                        && cartBox.intersects(entity.getBoundingBox())
        );
        for (Entity overlapping : overlappingContacts) {
            contacts.recordContact(overlapping.getUuid(), currentTick);
        }

        if (expireSeparatedContacts) {
            contacts.expireSeparatedContacts(
                    currentTick,
                    this.minecartspeedfeatures$getImpactHysteresisTicks()
            );
        }

        if (contacts.isEmpty()) {
            this.minecartspeedfeatures$impactContacts = null;
        }
    }
}
