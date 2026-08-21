package ilostmy_fish.mixin;

import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.interpolation.VisualInterpolationAccess;
import ilostmy_fish.interpolation.VisualPath;
import ilostmy_fish.rail.MoveIteration;
import ilostmy_fish.rail.MoveResult;
import ilostmy_fish.rail.RailRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.world.World;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the 1.21.1 entity tick intact and replaces only the server-side rail movement body.
 * One logical tick may consume many rail sections, but gravity/bookkeeping/input/friction are
 * never replayed as whole entity ticks.
 */
@Mixin(value = AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin extends Entity implements VisualInterpolationAccess {
    @Shadow
    private boolean onRail;

    @Shadow
    public abstract Vec3d snapPositionToRail(double x, double y, double z);

    @Shadow
    public abstract Vec3d snapPositionToRailWithOffset(double x, double y, double z, double offset);

    @Unique
    private static final double minecartspeedfeatures$EPSILON = 1.0E-5;
    @Unique
    private static final int minecartspeedfeatures$MAX_RAIL_ITERATIONS = 4096;
    @Unique
    private static final int minecartspeedfeatures$MAX_VISUAL_RAILS = 2048;
    @Unique
    private static final double minecartspeedfeatures$VISUAL_INTERPOLATION_MIN_DISTANCE = 1.5;

    /**
     * Client-only visual state. It never changes authoritative entity position or velocity.
     */
    @Unique
    private VisualPath minecartspeedfeatures$visualPath;
    @Unique
    private Vec3d minecartspeedfeatures$visualStart;
    @Unique
    private Vec3d minecartspeedfeatures$visualTarget;
    @Unique
    private int minecartspeedfeatures$visualTotalTicks;
    @Unique
    private int minecartspeedfeatures$visualTicks;
    @Unique
    private boolean minecartspeedfeatures$visualActive;

    /**
     * Last rail shape, retained to mirror the state used by 1.1.0 launchFromRail.
     */
    @Unique
    private RailShape minecartspeedfeatures$lastRail;

    /**
     * Exact 1.1.0 update formula: sqrt(velocity.length()) / 2 * 0.95.
     */
    @Unique
    private double minecartspeedfeatures$lastVelocity;

    protected AbstractMinecartEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    /**
     * Observe vanilla's client snapshot without cancelling or changing it. 1.21.1 minecarts add
     * two interpolation ticks internally, so mirror that timing for the visual correction.
     */
    @Inject(
            method = "updateTrackedPositionAndAngles(DDDFFI)V",
            at = @At("HEAD")
    )
    private void minecartspeedfeatures$captureClientInterpolation(
            double x, double y, double z, float yaw, float pitch, int interpolationSteps, CallbackInfo ci
    ) {
        if (!this.getWorld().isClient) {
            return;
        }

        Vec3d start = this.getPos();
        Vec3d target = new Vec3d(x, y, z);
        double dx = x - start.getX();
        double dy = y - start.getY();
        double dz = z - start.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < minecartspeedfeatures$VISUAL_INTERPOLATION_MIN_DISTANCE
                * minecartspeedfeatures$VISUAL_INTERPOLATION_MIN_DISTANCE) {
            this.minecartspeedfeatures$clearVisualInterpolation();
            return;
        }

        // Keep visual continuity across successive snapshots. The logical client position can be
        // cutting straight through a bend while the previous render correction is already on the
        // rail, so use that corrected point as the next geometric path start. The baseline start
        // remains the untouched logical position below.
        Vec3d pathStart = start;
        if (this.minecartspeedfeatures$visualActive && this.minecartspeedfeatures$visualPath != null) {
            pathStart = this.minecartspeedfeatures$visualPath.sample(this.minecartspeedfeatures$visualProgress(1.0F));
        }

        VisualPath path = this.minecartspeedfeatures$buildVisualRailPath(pathStart, target);
        if (path == null) {
            // Missing/unloaded rails, airborne motion, unusual modded rail geometry, or an
            // ambiguous path all fall back to untouched vanilla interpolation.
            this.minecartspeedfeatures$clearVisualInterpolation();
            return;
        }

        this.minecartspeedfeatures$visualPath = path;
        this.minecartspeedfeatures$visualStart = start;
        this.minecartspeedfeatures$visualTarget = target;
        this.minecartspeedfeatures$visualTotalTicks = Math.max(1, interpolationSteps + 2);
        this.minecartspeedfeatures$visualTicks = 0;
        this.minecartspeedfeatures$visualActive = true;
    }

    /**
     * Advance only our render clock. Vanilla still owns the actual client interpolation.
     */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void minecartspeedfeatures$advanceVisualInterpolation(CallbackInfo ci) {
        if (!this.getWorld().isClient || !this.minecartspeedfeatures$visualActive) {
            return;
        }
        this.minecartspeedfeatures$visualTicks++;
        if (this.minecartspeedfeatures$visualTicks > this.minecartspeedfeatures$visualTotalTicks) {
            this.minecartspeedfeatures$clearVisualInterpolation();
        }
    }

    @Inject(
            method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void minecartspeedfeatures$moveOnRail(BlockPos initialPos, BlockState initialState, CallbackInfo ci) {
        ci.cancel();
        Vec3d initialVelocity = this.getVelocity();
        double preRailYVelocity = initialVelocity.getY();
        double previousLastVelocity = this.minecartspeedfeatures$lastVelocity;
        // Tick-level launch history is sampled exactly once per real server tick. Rail traversal
        // iterations are movement resolution, not pseudo-ticks.
        this.minecartspeedfeatures$lastVelocity = Math.sqrt(initialVelocity.length()) / 2.0 * 0.95;
        this.onLanding();

        MoveIteration iteration = new MoveIteration();
        RailRef rail = this.minecartspeedfeatures$railRef(initialPos, initialState);

        for (int count = 0;
             count < minecartspeedfeatures$MAX_RAIL_ITERATIONS && rail != null
                     && (iteration.initial || iteration.remainingMovement > minecartspeedfeatures$EPSILON);
             count++) {
            Vec3d beforeForces = this.getVelocity();
            double oldHorizontalSpeed = minecartspeedfeatures$horizontalLength(beforeForces);

            RailShape shape = rail.shape;
            this.minecartspeedfeatures$lastRail = shape;

            Vec3d velocity = this.minecartspeedfeatures$calculateRailVelocity(
                    beforeForces,
                    iteration,
                    rail.pos,
                    rail.state,
                    shape
            );
            double newHorizontalSpeed = minecartspeedfeatures$horizontalLength(velocity);

            if (iteration.initial) {
                iteration.remainingMovement = newHorizontalSpeed;
            } else {
                iteration.remainingMovement = Math.max(
                        0.0,
                        iteration.remainingMovement + newHorizontalSpeed - oldHorizontalSpeed
                );
            }

            // Rail-constrained velocity itself remains horizontal, as in 1.21.1. Vertical
            // displacement is handled by the centerline path below rather than by free flight.
            this.setVelocity(new Vec3d(velocity.getX(), 0.0, velocity.getZ()));

            MoveResult result = this.minecartspeedfeatures$moveAlongTrack(
                    rail.pos,
                    shape,
                    iteration.remainingMovement
            );
            iteration.remainingMovement = result.remainingMovement;
            iteration.initial = false;

            if (result.stuck) {
                this.setVelocity(new Vec3d(0.0, 0.0, 0.0));
                return;
            }

            if (!result.reachedEndpoint) {
                return;
            }

            RailRef next = this.minecartspeedfeatures$findRailAtCart();
            if (next == null) {
                this.onRail = false;

                // ADJACENT_RAIL_POSITIONS_BY_SHAPE stores the upper endpoint with dy == 0.
                // Launch only when leaving that upper endpoint, not when descending off the low end.
                if (minecartspeedfeatures$isAscendingShape(shape) && result.forwardDy == 0) {
                    // 1.1.0 does not update lastVelocity on the launch-transition branch.
                    this.minecartspeedfeatures$lastVelocity = previousLastVelocity;
                    // The old transition fires after one gravity application but before off-rail
                    // movement. Restore that tick-level Y velocity; the launch routine below is
                    // then the literal 1.1.0 additive transform.
                    Vec3d launchBase = this.getVelocity();
                    this.setVelocity(new Vec3d(
                            launchBase.getX(),
                            preRailYVelocity,
                            launchBase.getZ()
                    ));
                    this.minecartspeedfeatures$launchFromRail110();
                }
                return;
            }

            this.onRail = true;
            rail = next;
        }

        // The cap is a corruption/loop guard, never a speed-derived pseudo-tick count.
        if (iteration.remainingMovement > minecartspeedfeatures$EPSILON) {
            this.setVelocity(new Vec3d(0.0, 0.0, 0.0));
        }
    }

    @Inject(
            method = "getMaxSpeed()D",
            at = @At("RETURN"),
            cancellable = true
    )
    private void minecartspeedfeatures$getMaxSpeed(CallbackInfoReturnable<Double> cir) {
        if (MinecartSpeedFeatures.MINECART_MAX_SPEED != null) {
            cir.setReturnValue(this.minecartspeedfeatures$maxSpeedBlocksPerTick());
        }
    }

    @Unique
    private Vec3d minecartspeedfeatures$calculateRailVelocity(
            Vec3d rawVelocity,
            MoveIteration iteration,
            BlockPos railPos,
            BlockState railState,
            RailShape shape
    ) {
        double x = rawVelocity.getX();
        double z = rawVelocity.getZ();

        // Mojang's experimental slope force is applied at most once per real tick.
        if (!iteration.slopeVelocityApplied && minecartspeedfeatures$isAscendingShape(shape)) {
            double slope = Math.max(0.0078125, Math.hypot(x, z) * 0.02);
            if (this.isTouchingWater()) {
                slope *= 0.2;
            }
            switch (shape.ordinal()) {
                case 2 -> x -= slope; // ASCENDING_EAST
                case 3 -> x += slope; // ASCENDING_WEST
                case 4 -> z += slope; // ASCENDING_NORTH
                case 5 -> z -= slope; // ASCENDING_SOUTH
                default -> {
                }
            }
            iteration.slopeVelocityApplied = true;
        }

        // Preserve 1.21.1 rider nudging, but run it only once for this server tick.
        if (iteration.initial) {
            Entity passenger = this.getFirstPassenger();
            if (passenger instanceof PlayerEntity) {
                Vec3d riderVelocity = passenger.getVelocity();
                double riderSq = riderVelocity.getX() * riderVelocity.getX()
                        + riderVelocity.getZ() * riderVelocity.getZ();
                double cartSq = x * x + z * z;
                if (riderSq > 1.0E-4 && cartSq < 0.01) {
                    x += riderVelocity.getX() * 0.1;
                    z += riderVelocity.getZ() * 0.1;
                    iteration.decelerated = true;
                }
            }
        }

        // Vanilla only treats Blocks.POWERED_RAIL as a propulsion/braking rail. Activator rails
        // share the PoweredRailBlock implementation, so a blanket instanceof check would be wrong.
        // The Copper Rail 0.9.4 also extends PoweredRailBlock, but its own mixin explicitly makes
        // only that custom block participate in powered-rail logic. Mirror that exact exception.
        boolean copperRail = minecartspeedfeatures$isCopperRail(railState);
        boolean poweredRail = railState.getBlock() == Blocks.POWERED_RAIL || copperRail;
        boolean powered = poweredRail && Boolean.TRUE.equals(railState.get(PoweredRailBlock.POWERED));

        // An unpowered powered-rail is a brake, once. Ordinary ascending rails are never gated here.
        if (!iteration.decelerated && poweredRail && !powered) {
            double speed = Math.hypot(x, z);
            if (speed < 0.03) {
                x = 0.0;
                z = 0.0;
            } else {
                x *= 0.5;
                z *= 0.5;
            }
            iteration.decelerated = true;
        }

        if (iteration.initial) {
            double retention = this.hasPassengers() ? 0.997 : 0.975;
            x *= retention;
            z *= retention;
            if (this.isTouchingWater()) {
                x *= 0.95;
                z *= 0.95;
            }
            double speed = Math.hypot(x, z);
            double max = this.minecartspeedfeatures$maxSpeedBlocksPerTick();
            if (speed > max && speed > 0.0) {
                double scale = max / speed;
                x *= scale;
                z *= scale;
            }
        }

        // The Copper Rail 0.9.4 injects at the end of vanilla moveOnRail and replaces
        // powered-rail acceleration with a directional +/-0.5 push. Our HEAD cancellation means
        // its late callback cannot execute, so reproduce that public block behavior here. Apply
        // it once per real tick, never once per traversed rail, to avoid high-speed multiplication.
        if (powered && copperRail && !iteration.copperRailApplied) {
            double push = Boolean.TRUE.equals(railState.get(Properties.INVERTED)) ? -0.5 : 0.5;
            switch (shape.ordinal()) {
                case 1, 2, 3 -> x += push; // EAST_WEST / ascending east-west
                case 0, 4, 5 -> z += push; // NORTH_SOUTH / ascending north-south
                default -> {
                }
            }
            double speed = Math.hypot(x, z);
            double max = this.minecartspeedfeatures$maxSpeedBlocksPerTick();
            if (speed > max && speed > 0.0) {
                double scale = max / speed;
                x *= scale;
                z *= scale;
            }
            iteration.copperRailApplied = true;
            iteration.accelerated = true; // Copper Rail replaces vanilla +0.06 on that tick.
        }

        // Vanilla powered-rail acceleration rate: +0.06 blocks/tick, once per server tick.
        if (!iteration.accelerated && powered) {
            double speed = Math.hypot(x, z);
            if (speed > 0.01) {
                double target = Math.min(this.minecartspeedfeatures$maxSpeedBlocksPerTick(), speed + 0.06);
                double scale = target / speed;
                x *= scale;
                z *= scale;
                iteration.accelerated = true;
            } else {
                // Keep the target-version stationary kick behavior. It only chooses a direction
                // when a solid block exists at one end of a straight powered rail.
                int ordinal = shape.ordinal();
                int px = railPos.getX();
                int py = railPos.getY();
                int pz = railPos.getZ();
                if (ordinal == 1) { // EAST_WEST
                    if (this.minecartspeedfeatures$willHitBlockAt(new BlockPos(px - 1, py, pz))) {
                        x = 0.02;
                    } else if (this.minecartspeedfeatures$willHitBlockAt(new BlockPos(px + 1, py, pz))) {
                        x = -0.02;
                    }
                } else if (ordinal == 0) { // NORTH_SOUTH
                    if (this.minecartspeedfeatures$willHitBlockAt(new BlockPos(px, py, pz - 1))) {
                        z = 0.02;
                    } else if (this.minecartspeedfeatures$willHitBlockAt(new BlockPos(px, py, pz + 1))) {
                        z = -0.02;
                    }
                }
                if (x != 0.0 || z != 0.0) {
                    iteration.accelerated = true;
                }
            }
        }

        return new Vec3d(x, 0.0, z);
    }

    @Unique
    private MoveResult minecartspeedfeatures$moveAlongTrack(
            BlockPos railPos,
            RailShape shape,
            double remainingMovement
    ) {
        if (remainingMovement < minecartspeedfeatures$EPSILON) {
            return new MoveResult(0.0, false, false, 0);
        }

        Vec3d start = this.getPos();
        Vec3d horizontalVelocity = this.getVelocity();
        double horizontalSpeed = minecartspeedfeatures$horizontalLength(horizontalVelocity);
        if (horizontalSpeed < minecartspeedfeatures$EPSILON) {
            return new MoveResult(0.0, false, true, 0);
        }

        int ordinal = shape.ordinal();
        int ax = minecartspeedfeatures$endpointAX(ordinal);
        int ay = minecartspeedfeatures$endpointAY(ordinal);
        int az = minecartspeedfeatures$endpointAZ(ordinal);
        int bx = minecartspeedfeatures$endpointBX(ordinal);
        int by = minecartspeedfeatures$endpointBY(ordinal);
        int bz = minecartspeedfeatures$endpointBZ(ordinal);

        double dotA = horizontalVelocity.getX() * ax + horizontalVelocity.getZ() * az;
        double dotB = horizontalVelocity.getX() * bx + horizontalVelocity.getZ() * bz;
        int fx = dotA < dotB ? bx : ax;
        int fy = dotA < dotB ? by : ay;
        int fz = dotA < dotB ? bz : az;

        double hlen = Math.hypot(fx, fz);
        if (hlen < minecartspeedfeatures$EPSILON) {
            return new MoveResult(0.0, false, true, fy);
        }

        double ux = fx / hlen;
        double uz = fz / hlen;
        double targetX = railPos.getX() + 0.5 + fx * 0.5 + ux * minecartspeedfeatures$EPSILON;
        double targetY = railPos.getY() + 0.1;
        double targetZ = railPos.getZ() + 0.5 + fz * 0.5 + uz * minecartspeedfeatures$EPSILON;

        boolean slope = ay != by;
        if (slope && !minecartspeedfeatures$ascends(horizontalVelocity, ordinal)) {
            targetY += 1.0;
        }

        double dxToTarget = targetX - start.getX();
        double dyToTarget = targetY - start.getY();
        double dzToTarget = targetZ - start.getZ();
        double fullLen = Math.sqrt(dxToTarget * dxToTarget + dyToTarget * dyToTarget + dzToTarget * dzToTarget);
        double horizontalLen = Math.hypot(dxToTarget, dzToTarget);
        if (fullLen < minecartspeedfeatures$EPSILON || horizontalLen < minecartspeedfeatures$EPSILON) {
            return new MoveResult(remainingMovement, true, false, fy);
        }

        double dirX = dxToTarget / fullLen;
        double dirY = dyToTarget / fullLen;
        double dirZ = dzToTarget / fullLen;
        double dirHorizontal = Math.hypot(dirX, dirZ);
        double tangentScale = horizontalSpeed / dirHorizontal;
        double tangentX = dirX * tangentScale;
        double tangentZ = dirZ * tangentScale;

        double requestedPath = remainingMovement * (slope ? Math.sqrt(2.0) : 1.0);
        double candidateX = start.getX() + dirX * requestedPath;
        double candidateY = start.getY() + dirY * requestedPath;
        double candidateZ = start.getZ() + dirZ * requestedPath;

        double startToTargetSq = minecartspeedfeatures$squaredDistance(
                start.getX(), start.getY(), start.getZ(),
                targetX, targetY, targetZ
        );
        double startToCandidateSq = minecartspeedfeatures$squaredDistance(
                start.getX(), start.getY(), start.getZ(),
                candidateX, candidateY, candidateZ
        );
        boolean reachedEndpoint = startToTargetSq <= startToCandidateSq;

        double desiredX;
        double desiredY;
        double desiredZ;
        double newRemaining;
        if (reachedEndpoint) {
            desiredX = targetX;
            desiredY = targetY;
            desiredZ = targetZ;
            newRemaining = Math.hypot(candidateX - targetX, candidateZ - targetZ);
        } else {
            desiredX = candidateX;
            desiredY = candidateY;
            desiredZ = candidateZ;
            newRemaining = 0.0;
        }

        double requestedDx = desiredX - start.getX();
        double requestedDy = desiredY - start.getY();
        double requestedDz = desiredZ - start.getZ();
        this.move(MovementType.SELF, new Vec3d(requestedDx, requestedDy, requestedDz));

        // Keep the minecart on the rail centerline vertically even when the ascending rail's
        // support/front geometry would otherwise clip the generic entity movement.
        if (slope) {
            Vec3d actual = this.getPos();
            double distanceFromEndpoint = Math.hypot(targetX - actual.getX(), targetZ - actual.getZ());
            double expectedY = targetY + (minecartspeedfeatures$ascends(horizontalVelocity, ordinal)
                    ? distanceFromEndpoint
                    : -distanceFromEndpoint);
            if (actual.getY() < expectedY) {
                this.setPosition(new Vec3d(actual.getX(), expectedY, actual.getZ()));
            }
        }

        Vec3d actual = this.getPos();
        double actualMoveSq = minecartspeedfeatures$squaredDistance(
                start.getX(), start.getY(), start.getZ(),
                actual.getX(), actual.getY(), actual.getZ()
        );
        double requestedMoveSq = requestedDx * requestedDx + requestedDy * requestedDy + requestedDz * requestedDz;
        if (requestedMoveSq > minecartspeedfeatures$EPSILON * minecartspeedfeatures$EPSILON
                && actualMoveSq < minecartspeedfeatures$EPSILON * minecartspeedfeatures$EPSILON) {
            return new MoveResult(0.0, false, true, fy);
        }

        this.setVelocity(new Vec3d(tangentX, 0.0, tangentZ));
        return new MoveResult(newRemaining, reachedEndpoint, false, fy);
    }

    @Override
    public Vec3d minecartspeedfeatures$getVisualRideOffset(float tickDelta) {
        if (!this.minecartspeedfeatures$visualActive || this.minecartspeedfeatures$visualPath == null) {
            return new Vec3d(0.0, 0.0, 0.0);
        }

        double progress = this.minecartspeedfeatures$visualProgress(tickDelta);
        Vec3d desired = this.minecartspeedfeatures$visualPath.sample(progress);
        Vec3d baseline = minecartspeedfeatures$lerp(
                this.minecartspeedfeatures$visualStart,
                this.minecartspeedfeatures$visualTarget,
                progress
        );
        return new Vec3d(
                desired.getX() - baseline.getX(),
                desired.getY() - baseline.getY(),
                desired.getZ() - baseline.getZ()
        );
    }

    @Override
    public Vec3d minecartspeedfeatures$getVisualBodyOffset(float tickDelta) {
        if (!this.minecartspeedfeatures$visualActive || this.minecartspeedfeatures$visualPath == null) {
            return new Vec3d(0.0, 0.0, 0.0);
        }

        double progress = this.minecartspeedfeatures$visualProgress(tickDelta);
        Vec3d desired = this.minecartspeedfeatures$visualPath.sample(progress);
        Vec3d linear = minecartspeedfeatures$lerp(
                this.minecartspeedfeatures$visualStart,
                this.minecartspeedfeatures$visualTarget,
                progress
        );

        // MinecartEntityRenderer performs another rail snap after EntityRenderer's position
        // offset. Compensate for that existing snap instead of fighting or replacing it.
        Vec3d rendererAnchor = this.minecartspeedfeatures$rendererRailAnchor(linear);
        return new Vec3d(
                desired.getX() - rendererAnchor.getX(),
                desired.getY() - rendererAnchor.getY(),
                desired.getZ() - rendererAnchor.getZ()
        );
    }

    @Unique
    private double minecartspeedfeatures$visualProgress(float tickDelta) {
        int completed = Math.max(0, this.minecartspeedfeatures$visualTicks);
        // After the kth interpolation tick, vanilla renders between k-1 and k. Before the first
        // tick, remain exactly at the snapshot start.
        if (completed == 0) {
            return 0.0;
        }
        double progress = (completed - 1.0 + Math.clamp(tickDelta, 0.0F, 1.0F))
                / this.minecartspeedfeatures$visualTotalTicks;
        return Math.clamp(progress, 0.0, 1.0);
    }

    @Unique
    private void minecartspeedfeatures$clearVisualInterpolation() {
        this.minecartspeedfeatures$visualPath = null;
        this.minecartspeedfeatures$visualStart = null;
        this.minecartspeedfeatures$visualTarget = null;
        this.minecartspeedfeatures$visualTotalTicks = 0;
        this.minecartspeedfeatures$visualTicks = 0;
        this.minecartspeedfeatures$visualActive = false;
    }

    @Unique
    private VisualPath minecartspeedfeatures$buildVisualRailPath(Vec3d start, Vec3d target) {
        // Do not convert an airborne server snapshot into a rail path just because a rail happens
        // to be one or two blocks below it. Vanilla's own snap routine is the authority for whether
        // each endpoint is actually associated with a rail. This preserves the 1.1.0-style
        // parabolic launch and ordinary off-rail gravity unchanged.
        Vec3d snappedStart = this.snapPositionToRail(
                start.getX(), start.getY(), start.getZ()
        );
        Vec3d snappedTarget = this.snapPositionToRail(
                target.getX(), target.getY(), target.getZ()
        );
        if (snappedStart == null || snappedTarget == null) {
            return null;
        }

        RailRef startRail = this.minecartspeedfeatures$findRailNear(snappedStart);
        RailRef targetRail = this.minecartspeedfeatures$findRailNear(snappedTarget);
        if (startRail == null || targetRail == null || startRail.pos.equals(targetRail.pos)) {
            return null;
        }

        Vec3d velocity = this.getVelocity();
        double dirX = velocity.getX();
        double dirZ = velocity.getZ();
        double dirLen = Math.hypot(dirX, dirZ);
        if (dirLen < minecartspeedfeatures$EPSILON) {
            dirX = target.getX() - start.getX();
            dirZ = target.getZ() - start.getZ();
            dirLen = Math.hypot(dirX, dirZ);
        }
        if (dirLen < minecartspeedfeatures$EPSILON) {
            return null;
        }
        dirX /= dirLen;
        dirZ /= dirLen;

        List<Vec3d> points = new ArrayList<>();
        points.add(start);
        RailRef rail = startRail;
        RailRef previous = null;

        for (int count = 0; count < minecartspeedfeatures$MAX_VISUAL_RAILS; count++) {
            if (rail.pos.equals(targetRail.pos)) {
                points.add(target);
                return VisualPath.create(points);
            }

            int ordinal = rail.shape.ordinal();
            int ax = minecartspeedfeatures$endpointAX(ordinal);
            int az = minecartspeedfeatures$endpointAZ(ordinal);
            int bx = minecartspeedfeatures$endpointBX(ordinal);
            int bz = minecartspeedfeatures$endpointBZ(ordinal);
            double dotA = dirX * ax + dirZ * az;
            double dotB = dirX * bx + dirZ * bz;
            int fx = dotA < dotB ? bx : ax;
            int fz = dotA < dotB ? bz : az;
            double horizontal = Math.hypot(fx, fz);
            if (horizontal < minecartspeedfeatures$EPSILON) {
                return null;
            }
            double outX = fx / horizontal;
            double outZ = fz / horizontal;

            double boundaryX = rail.pos.getX() + 0.5 + fx * 0.5;
            double boundaryZ = rail.pos.getZ() + 0.5 + fz * 0.5;
            Vec3d boundary = this.minecartspeedfeatures$snapForVisualPath(
                    boundaryX, rail.pos.getY() + 0.5, boundaryZ
            );
            if (boundary == null) {
                return null;
            }
            points.add(boundary);

            RailRef next = this.minecartspeedfeatures$findRailAhead(rail, previous, boundary, outX, outZ);
            if (next == null) {
                return null;
            }
            previous = rail;
            rail = next;
            dirX = outX;
            dirZ = outZ;
        }

        return null;
    }

    @Unique
    private RailRef minecartspeedfeatures$findRailNear(Vec3d point) {
        int x = minecartspeedfeatures$floor(point.getX());
        int y = minecartspeedfeatures$floor(point.getY());
        int z = minecartspeedfeatures$floor(point.getZ());
        World world = this.getWorld();
        int[] offsets = {-1, 0, 1, -2, 2};
        for (int offset : offsets) {
            BlockPos pos = new BlockPos(x, y + offset, z);
            BlockState state = world.getBlockState(pos);
            RailRef ref = this.minecartspeedfeatures$railRef(pos, state);
            if (ref != null) {
                return ref;
            }
        }
        return null;
    }

    @Unique
    private RailRef minecartspeedfeatures$findRailAhead(
            RailRef current,
            RailRef previous,
            Vec3d boundary,
            double dirX,
            double dirZ
    ) {
        double probeX = boundary.getX() + dirX * 0.02;
        double probeZ = boundary.getZ() + dirZ * 0.02;
        int x = minecartspeedfeatures$floor(probeX);
        int z = minecartspeedfeatures$floor(probeZ);
        int baseY = minecartspeedfeatures$floor(boundary.getY());
        World world = this.getWorld();

        RailRef best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dy = -2; dy <= 2; dy++) {
            BlockPos pos = new BlockPos(x, baseY + dy, z);
            BlockState state = world.getBlockState(pos);
            RailRef candidate = this.minecartspeedfeatures$railRef(pos, state);
            if (candidate == null || candidate.pos.equals(current.pos)) {
                continue;
            }
            if (previous != null && candidate.pos.equals(previous.pos)) {
                continue;
            }
            if (!minecartspeedfeatures$railConnectsToward(candidate, current.pos)) {
                continue;
            }
            double score = Math.abs((candidate.pos.getY() + 0.5) - boundary.getY());
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    @Unique
    private static boolean minecartspeedfeatures$railConnectsToward(RailRef rail, BlockPos other) {
        int ordinal = rail.shape.ordinal();
        int dx = other.getX() - rail.pos.getX();
        int dz = other.getZ() - rail.pos.getZ();
        return (minecartspeedfeatures$endpointAX(ordinal) == dx && minecartspeedfeatures$endpointAZ(ordinal) == dz)
                || (minecartspeedfeatures$endpointBX(ordinal) == dx && minecartspeedfeatures$endpointBZ(ordinal) == dz);
    }

    @Unique
    private Vec3d minecartspeedfeatures$snapForVisualPath(double x, double y, double z) {
        Vec3d snapped = this.snapPositionToRail(x, y, z);
        if (snapped != null) {
            return snapped;
        }
        snapped = this.snapPositionToRail(x, y + 1.0, z);
        if (snapped != null) {
            return snapped;
        }
        return this.snapPositionToRail(x, y - 1.0, z);
    }

    @Unique
    private Vec3d minecartspeedfeatures$rendererRailAnchor(Vec3d linear) {
        Vec3d center = this.snapPositionToRail(
                linear.getX(), linear.getY(), linear.getZ()
        );
        if (center == null) {
            return linear;
        }

        Vec3d ahead = this.snapPositionToRailWithOffset(
                linear.getX(), linear.getY(), linear.getZ(), 0.3
        );
        Vec3d behind = this.snapPositionToRailWithOffset(
                linear.getX(), linear.getY(), linear.getZ(), -0.3
        );
        if (ahead == null) {
            ahead = center;
        }
        if (behind == null) {
            behind = center;
        }
        return new Vec3d(
                center.getX(),
                (ahead.getY() + behind.getY()) * 0.5,
                center.getZ()
        );
    }

    @Unique
    private static Vec3d minecartspeedfeatures$lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t
        );
    }

    /**
     * Literal 1.1.0 launchFromRail transformation, using the old constants and branch logic.
     */
    @Unique
    private void minecartspeedfeatures$launchFromRail110() {
        if (this.minecartspeedfeatures$lastRail == null) {
            return;
        }

        switch (this.minecartspeedfeatures$lastRail.ordinal()) {
            case 2, 3 -> this.addVelocity(
                    (this.minecartspeedfeatures$lastVelocity - 0.0078125)
                            * Math.signum(this.getVelocity().getX()),
                    this.minecartspeedfeatures$lastVelocity * (this.getY() < this.prevY ? -1.0 : 1.0),
                    0.0
            );
            case 4, 5 -> this.addVelocity(
                    0.0,
                    this.minecartspeedfeatures$lastVelocity * (this.getY() < this.prevY ? -1.0 : 1.0),
                    (this.minecartspeedfeatures$lastVelocity - 0.0078125)
                            * Math.signum(this.getVelocity().getZ())
            );
            default -> {
            }
        }

        Vec3d launchedVelocity = this.getVelocity();
        Vec3d launchedPosition = this.getPos()
                .add(launchedVelocity.multiply(0.0, 0.95, 0.0))
                .add(0.0, this.getY() < this.prevY ? 0.0 : 0.3, 0.0);
        this.setPosition(launchedPosition);
        this.resetPosition();
        this.refreshPosition();
        this.noClip = false;
    }

    @Unique
    private RailRef minecartspeedfeatures$findRailAtCart() {
        int x = minecartspeedfeatures$floor(this.getX());
        int y = minecartspeedfeatures$floor(this.getY());
        int z = minecartspeedfeatures$floor(this.getZ());
        World world = this.getWorld();

        BlockPos belowPos = new BlockPos(x, y - 1, z);
        BlockState belowState = world.getBlockState(belowPos);
        if (AbstractRailBlock.isRail(belowState)) {
            return this.minecartspeedfeatures$railRef(belowPos, belowState);
        }

        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        return this.minecartspeedfeatures$railRef(pos, state);
    }

    @Unique
    private RailRef minecartspeedfeatures$railRef(BlockPos pos, BlockState state) {
        if (!AbstractRailBlock.isRail(state)) {
            return null;
        }
        Block block = state.getBlock();
        if (!(block instanceof AbstractRailBlock rail)) {
            return null;
        }
        RailShape shape = state.get(rail.getShapeProperty());
        return new RailRef(pos, state, shape);
    }

    @Unique
    private static boolean minecartspeedfeatures$isCopperRail(BlockState state) {
        // Optional integration without a class/link dependency on The Copper Rail. User-defined
        // class names are not remapped by Loom, so this stays stable for the 0.9.4 1.21.1 build.
        return state.getBlock().getClass().getName().equals("com.thecopperrail.CopperRailBlock");
    }

    @Unique
    private boolean minecartspeedfeatures$willHitBlockAt(BlockPos pos) {
        // This helper intentionally invokes the target's private vanilla method through a shadow
        // surrogate generated below, keeping the stationary powered-rail kick vanilla-like.
        return this.willHitBlockAt(pos);
    }

    @Shadow
    private boolean willHitBlockAt(BlockPos pos) {
        throw new AssertionError();
    }

    @Unique
    private double minecartspeedfeatures$maxSpeedBlocksPerTick() {
        if (MinecartSpeedFeatures.MINECART_MAX_SPEED == null) {
            return (this.isTouchingWater() ? 4.0 : 8.0) / 20.0;
        }
        double result = this.getWorld().getGameRules().getInt(MinecartSpeedFeatures.MINECART_MAX_SPEED) / 20.0;
        if (this.isTouchingWater()) {
            result *= 0.5;
        }
        return result;
    }

    @Unique
    private static boolean minecartspeedfeatures$isAscendingShape(RailShape shape) {
        int ordinal = shape.ordinal();
        return ordinal >= 2 && ordinal <= 5;
    }

    /**
     * Matches the newer controller's direction test (despite the counterintuitive name).
     */
    @Unique
    private static boolean minecartspeedfeatures$ascends(Vec3d velocity, int shapeOrdinal) {
        return switch (shapeOrdinal) {
            case 2 -> velocity.getX() < 0.0;
            case 3 -> velocity.getX() > 0.0;
            case 4 -> velocity.getZ() > 0.0;
            case 5 -> velocity.getZ() < 0.0;
            default -> false;
        };
    }

    @Unique
    private static double minecartspeedfeatures$horizontalLength(Vec3d velocity) {
        return Math.hypot(velocity.getX(), velocity.getZ());
    }

    @Unique
    private static int minecartspeedfeatures$floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    @Unique
    private static double minecartspeedfeatures$squaredDistance(
            double ax, double ay, double az,
            double bx, double by, double bz
    ) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    // Endpoint geometry copied from the 1.21.2 ADJACENT_RAIL_POSITIONS_BY_SHAPE layout.
    @Unique
    private static int minecartspeedfeatures$endpointAX(int o) {
        return switch (o) {
            case 1, 2, 3 -> -1;
            default -> 0;
        };
    }

    @Unique
    private static int minecartspeedfeatures$endpointAY(int o) {
        return switch (o) {
            case 2, 5 -> -1;
            default -> 0;
        };
    }

    @Unique
    private static int minecartspeedfeatures$endpointAZ(int o) {
        return switch (o) {
            case 0, 4, 5, 8, 9 -> -1;
            case 6, 7 -> 1;
            default -> 0;
        };
    }

    @Unique
    private static int minecartspeedfeatures$endpointBX(int o) {
        return switch (o) {
            case 1, 2, 3, 6, 9 -> 1;
            case 7, 8 -> -1;
            default -> 0;
        };
    }

    @Unique
    private static int minecartspeedfeatures$endpointBY(int o) {
        return switch (o) {
            case 3, 4 -> -1;
            default -> 0;
        };
    }

    @Unique
    private static int minecartspeedfeatures$endpointBZ(int o) {
        return switch (o) {
            case 0, 4, 5 -> 1;
            default -> 0;
        };
    }

}
