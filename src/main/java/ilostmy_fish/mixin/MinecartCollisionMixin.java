package ilostmy_fish.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.collision.ImpactContactTracker;
import ilostmy_fish.collision.ImpactKinematics;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final double minecartspeedfeatures$IMPACT_CONTACT_EPSILON = 1.0E-6;
    @Unique
    private static final double minecartspeedfeatures$MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12;
    @Unique
    private static final long minecartspeedfeatures$CONTACT_SEPARATION_HYSTERESIS_TICKS = 1L;

    @Unique
    private Vec3d minecartspeedfeatures$velocityBeforeRailMove = Vec3d.ZERO;
    @Unique
    private Vec3d minecartspeedfeatures$positionBeforeRailMove = Vec3d.ZERO;
    @Unique
    private ImpactContactTracker minecartspeedfeatures$impactContacts;
    @Unique
    private Set<UUID> minecartspeedfeatures$impactedThisTick;

    protected MinecartCollisionMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    /**
     * Give minecarts the symmetric entity-collision contract used by boats.
     *
     * <p>Vanilla minecarts already use {@code BoatEntity.canCollide} when the minecart is the
     * moving entity, but unlike boats they inherit {@link Entity#isCollidable()} as {@code false}.
     * That makes the result depend on which entity happens to move: a cart moving into a mob sees
     * the mob, while a mob moving into the same cart can pass through it. Introducing this override
     * makes other entities include the minecart in their movement collision shapes as well.</p>
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
        this.minecartspeedfeatures$positionBeforeRailMove = this.getPos();
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
        Vec3d actualMovement = this.getPos().subtract(
                this.minecartspeedfeatures$positionBeforeRailMove
        );
        if (!ImpactKinematics.isDamagingApproach(incomingVelocity, actualMovement)) {
            return;
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

        LivingEntity target = this.minecartspeedfeatures$findImpactTarget(incomingVelocity);
        if (target == null) {
            return;
        }
        if (this.minecartspeedfeatures$isImpactDisarmed(target)) {
            // Collision clipping may invoke impact handling again without a real box overlap.
            // Suppress the repeated impact, but do not refresh the contact timestamp here.
            return;
        }

        // Entity.move leaves colliding boxes on the shared contact plane rather than overlapping.
        // The initial MSF impact itself starts the damage-disarm hysteresis.
        this.minecartspeedfeatures$markImpactContact(target);

        ImpactPhysics.ImpactResult impact = ImpactPhysics.impact(
                speedBlocksPerSecond,
                target.getMaxHealth()
        );

        // Match Cammie's velocity-based knockback: horizontal knockback uses 80% of the cart's
        // incoming velocity and vertical knockback uses 20% of its total incoming speed.
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

        double speedScale = impact.speedScale();
        Vec3d postCollisionVelocity = this.getVelocity();
        this.setVelocity(
                incomingVelocity.getX() * speedScale,
                postCollisionVelocity.getY(),
                incomingVelocity.getZ() * speedScale
        );
    }

    /**
     * Always run the rideable minecart's nearby-mob loop. Slow empty carts retain vanilla mounting;
     * fast or recently impacted contacts are converted to pushing by the redirect below.
     */
    @ModifyExpressionValue(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;horizontalLengthSquared()D"
            )
    )
    private double minecartspeedfeatures$alwaysRunRideableMobInteractions(double horizontalSpeedSquared) {
        // The vanilla comparison immediately after this call is `> 0.01`.
        return 1.0;
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void minecartspeedfeatures$beginImpactContactTick(CallbackInfo ci) {
        if (this.minecartspeedfeatures$impactedThisTick != null) {
            this.minecartspeedfeatures$impactedThisTick.clear();
        }
        this.minecartspeedfeatures$refreshOverlappingImpactContacts();
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void minecartspeedfeatures$finishImpactContactTick(CallbackInfo ci) {
        this.minecartspeedfeatures$refreshOverlappingImpactContacts();
    }

    /**
     * Refreshes a latch only for strict, positive-volume hitbox intersection.
     *
     * <p>A face-to-face clipped collision does not satisfy {@link net.minecraft.util.math.Box#intersects}
     * and therefore cannot keep extending the one-tick hysteresis.</p>
     */
    @Unique
    private void minecartspeedfeatures$refreshOverlappingImpactContacts() {
        if (this.getWorld().isClient) {
            return;
        }

        ImpactContactTracker contacts = this.minecartspeedfeatures$impactContacts;
        if (contacts == null || contacts.isEmpty()) {
            return;
        }

        long currentTick = this.getWorld().getTime();
        List<Entity> overlappingContacts = this.getWorld().getOtherEntities(
                this,
                this.getBoundingBox(),
                entity -> contacts.isTracked(entity.getUuid())
                        && this.getBoundingBox().intersects(entity.getBoundingBox())
        );
        for (Entity overlapping : overlappingContacts) {
            contacts.recordContact(overlapping.getUuid(), currentTick);
        }
        contacts.expireSeparatedContacts(currentTick);
    }

    /**
     * Fast contacts push instead of mounting. A recent impact does the same on later ticks, while
     * the impact tick itself preserves MSF's deliberate knockback without stacking vanilla push.
     */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;startRiding(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean minecartspeedfeatures$pushInsteadOfMountAfterImpact(Entity entity, Entity vehicle) {
        if (this.minecartspeedfeatures$wasImpactedThisTick(entity)) {
            return false;
        }
        if (this.minecartspeedfeatures$isImpactDisarmed(entity)
                || this.getVelocity().horizontalLengthSquared()
                        > minecartspeedfeatures$MOB_INTERACTION_SPEED_THRESHOLD_SQUARED) {
            entity.pushAwayFrom(vehicle);
            return false;
        }
        return entity.startRiding(vehicle);
    }

    /**
     * Preserve the impact knockback instead of immediately applying vanilla minecart pushing.
     */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;pushAwayFrom(Lnet/minecraft/entity/Entity;)V"
            )
    )
    private void minecartspeedfeatures$skipVanillaPushAfterImpact(Entity entity, Entity minecart) {
        if (this.minecartspeedfeatures$wasImpactedThisTick(entity)) {
            return;
        }
        entity.pushAwayFrom(minecart);
    }

    @Unique
    private LivingEntity minecartspeedfeatures$findImpactTarget(Vec3d incomingVelocity) {
        List<Entity> candidates = this.getWorld().getOtherEntities(
                this,
                this.getBoundingBox().expand(minecartspeedfeatures$IMPACT_CONTACT_EPSILON),
                entity -> entity instanceof LivingEntity living
                        && living.isAlive()
                        && !living.hasVehicle()
                        && this.minecartspeedfeatures$isAtHorizontalContact(living)
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
    private boolean minecartspeedfeatures$isAtHorizontalContact(Entity entity) {
        Box cartBox = this.getBoundingBox();
        Box entityBox = entity.getBoundingBox();
        return cartBox.minY < entityBox.maxY
                && cartBox.maxY > entityBox.minY
                && cartBox.minX - minecartspeedfeatures$IMPACT_CONTACT_EPSILON < entityBox.maxX
                && cartBox.maxX + minecartspeedfeatures$IMPACT_CONTACT_EPSILON > entityBox.minX
                && cartBox.minZ - minecartspeedfeatures$IMPACT_CONTACT_EPSILON < entityBox.maxZ
                && cartBox.maxZ + minecartspeedfeatures$IMPACT_CONTACT_EPSILON > entityBox.minZ;
    }

    @Unique
    private void minecartspeedfeatures$markImpactContact(Entity entity) {
        if (this.minecartspeedfeatures$impactContacts == null) {
            this.minecartspeedfeatures$impactContacts = new ImpactContactTracker(
                    minecartspeedfeatures$CONTACT_SEPARATION_HYSTERESIS_TICKS
            );
        }
        this.minecartspeedfeatures$impactContacts.recordContact(
                entity.getUuid(),
                this.getWorld().getTime()
        );
        if (this.minecartspeedfeatures$impactedThisTick == null) {
            this.minecartspeedfeatures$impactedThisTick = new HashSet<>();
        }
        this.minecartspeedfeatures$impactedThisTick.add(entity.getUuid());
    }

    @Unique
    private boolean minecartspeedfeatures$isImpactDisarmed(Entity entity) {
        ImpactContactTracker contacts = this.minecartspeedfeatures$impactContacts;
        if (contacts == null) {
            return false;
        }

        UUID entityId = entity.getUuid();
        long currentTick = this.getWorld().getTime();
        if (contacts.isTracked(entityId)
                && this.getBoundingBox().intersects(entity.getBoundingBox())) {
            contacts.recordContact(entityId, currentTick);
        }
        return contacts.isDisarmed(entityId, currentTick);
    }

    @Unique
    private boolean minecartspeedfeatures$wasImpactedThisTick(Entity entity) {
        Set<UUID> impacted = this.minecartspeedfeatures$impactedThisTick;
        return impacted != null && impacted.contains(entity.getUuid());
    }
}
