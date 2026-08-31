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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
    private static final long minecartspeedfeatures$CONTACT_SEPARATION_HYSTERESIS_TICKS = 1L;

    @Unique
    private Vec3d minecartspeedfeatures$velocityBeforeRailMove = Vec3d.ZERO;
    @Unique
    private ImpactContactTracker minecartspeedfeatures$impactContacts;
    @Unique
    private Vec3d minecartspeedfeatures$velocityBeforeLivingPush;

    protected MinecartCollisionMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    /** Test: make minecarts solid to other entities, matching boat-style collidability. */
    @Override
    public boolean isCollidable() {
        return true;
    }

    /**
     * Living-entity cramming invokes the minecart's specialized push method outside the
     * minecart's own tick. Let that method apply vanilla separation to the living entity, then
     * discard only its secondary change to an impact-resolving cart. Ordinary and cart-to-cart
     * pushes never take a snapshot.
     */
    @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void minecartspeedfeatures$captureVelocityBeforeLivingPush(
            Entity other,
            CallbackInfo ci
    ) {
        this.minecartspeedfeatures$velocityBeforeLivingPush = other instanceof LivingEntity
                && this.minecartspeedfeatures$hasImpactContacts()
                ? this.getVelocity()
                : null;
    }

    @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("RETURN"))
    private void minecartspeedfeatures$restoreVelocityAfterLivingPush(
            Entity other,
            CallbackInfo ci
    ) {
        Vec3d velocityBeforePush = this.minecartspeedfeatures$velocityBeforeLivingPush;
        this.minecartspeedfeatures$velocityBeforeLivingPush = null;
        if (velocityBeforePush != null) {
            this.setVelocity(velocityBeforePush);
        }
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

        ImpactPhysics.ImpactResult impact = ImpactPhysics.impact(
                speedBlocksPerSecond,
                target.getMaxHealth()
        );

        // Inspired by Cammie's velocity-based knockback: horizontal knockback uses 80% of the cart's
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

        if (impact.residualSpeedBlocksPerSecond() > 0.0) {
            this.minecartspeedfeatures$markPassThrough(target);
        }
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
        this.minecartspeedfeatures$refreshImpactContacts();
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void minecartspeedfeatures$refreshImpactContactsAtTickEnd(CallbackInfo ci) {
        this.minecartspeedfeatures$refreshImpactContacts();
    }

    /**
     * Entity collision shapes are selected through the moving minecart's collidesWith predicate.
     * A successfully penetrated living entity remains ignored until the boxes have actually
     * separated. This matters when the residual speed is too low to clear a trapped target in one
     * tick.
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

    /**
     * Prevent an entity that was just struck from being immediately auto-mounted by a rideable cart.
     */
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

    /**
     * Preserve the impact knockback and rail velocity during the minecart's own nearby-entity
     * loop. {@link #pushAwayFrom(Entity)} covers the opposite receiver direction used by living
     * entity cramming.
     */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;pushAwayFrom(Lnet/minecraft/entity/Entity;)V"
            )
    )
    private void minecartspeedfeatures$skipVanillaPushAfterImpact(Entity entity, Entity minecart) {
        if (this.minecartspeedfeatures$isPassingThrough(entity)) {
            return;
        }
        if (!(entity instanceof LivingEntity)
                || !this.minecartspeedfeatures$hasImpactContacts()) {
            entity.pushAwayFrom(minecart);
            return;
        }

        Vec3d velocityBeforePush = this.getVelocity();
        entity.pushAwayFrom(minecart);
        this.setVelocity(velocityBeforePush);
    }

    @Unique
    private LivingEntity minecartspeedfeatures$findImpactTarget(Vec3d incomingVelocity) {
        List<Entity> candidates = this.getWorld().getOtherEntities(
                this,
                this.getBoundingBox().expand(minecartspeedfeatures$IMPACT_SEARCH_EXPANSION),
                entity -> entity instanceof LivingEntity living
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
    private void minecartspeedfeatures$markPassThrough(Entity entity) {
        if (this.minecartspeedfeatures$impactContacts == null) {
            this.minecartspeedfeatures$impactContacts = new ImpactContactTracker(
                    minecartspeedfeatures$CONTACT_SEPARATION_HYSTERESIS_TICKS
            );
        }
        this.minecartspeedfeatures$impactContacts.recordContact(
                entity.getUuid(),
                this.getWorld().getTime()
        );
    }

    @Unique
    private boolean minecartspeedfeatures$isPassingThrough(Entity entity) {
        ImpactContactTracker contacts = this.minecartspeedfeatures$impactContacts;
        return contacts != null && contacts.isActive(
                entity.getUuid(),
                this.getWorld().getTime()
        );
    }

    @Unique
    private boolean minecartspeedfeatures$hasImpactContacts() {
        ImpactContactTracker contacts = this.minecartspeedfeatures$impactContacts;
        return contacts != null && !contacts.isEmpty();
    }

    /**
     * Refresh only on a strict positive-volume overlap. The one-tick hysteresis bridges the
     * face-to-face state left by collision clipping, then keeps refreshing for however many ticks
     * the residual-speed cart needs to pass through the target.
     */
    @Unique
    private void minecartspeedfeatures$refreshImpactContacts() {
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
        contacts.expireSeparatedContacts(currentTick);

        if (contacts.isEmpty()) {
            this.minecartspeedfeatures$impactContacts = null;
        }
    }
}
