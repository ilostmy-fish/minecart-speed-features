package ilostmy_fish.mixin;

import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.interpolation.VisualInterpolationAccess;
import ilostmy_fish.interpolation.VisualPath;
import ilostmy_fish.rail.MoveIteration;
import ilostmy_fish.rail.MoveResult;
import ilostmy_fish.rail.RailRef;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1313;
import net.minecraft.class_1657;
import net.minecraft.class_1688;
import net.minecraft.class_1937;
import net.minecraft.class_2241;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2442;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2768;
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
@Mixin(value = class_1688.class, remap = false)
public abstract class AbstractMinecartEntityMixin extends class_1297 implements VisualInterpolationAccess {
    @Shadow(remap = false)
    private boolean field_44917;

    @Shadow(remap = false)
    public abstract class_243 method_7508(double x, double y, double z);

    @Shadow(remap = false)
    public abstract class_243 method_7505(double x, double y, double z, double offset);

    @Unique
    private static final double minecartspeedfeatures$EPSILON = 1.0E-5;
    @Unique
    private static final int minecartspeedfeatures$MAX_RAIL_ITERATIONS = 4096;
    @Unique
    private static final int minecartspeedfeatures$MAX_VISUAL_RAILS = 2048;
    @Unique
    private static final double minecartspeedfeatures$VISUAL_INTERPOLATION_MIN_DISTANCE = 1.5;

    /** Client-only visual state. It never changes authoritative entity position or velocity. */
    @Unique
    private VisualPath minecartspeedfeatures$visualPath;
    @Unique
    private class_243 minecartspeedfeatures$visualStart;
    @Unique
    private class_243 minecartspeedfeatures$visualTarget;
    @Unique
    private int minecartspeedfeatures$visualTotalTicks;
    @Unique
    private int minecartspeedfeatures$visualTicks;
    @Unique
    private boolean minecartspeedfeatures$visualActive;

    /** Last rail shape, retained to mirror the state used by 1.1.0 launchFromRail. */
    @Unique
    private class_2768 minecartspeedfeatures$lastRail;

    /** Exact 1.1.0 update formula: sqrt(velocity.length()) / 2 * 0.95. */
    @Unique
    private double minecartspeedfeatures$lastVelocity;

    /** Y velocity after vanilla's one gravity application and before rail confinement. */
    @Unique
    private double minecartspeedfeatures$preRailYVelocity;

    protected AbstractMinecartEntityMixin(class_1299<?> type, class_1937 world) {
        super(type, world);
    }

    /**
     * Observe vanilla's client snapshot without cancelling or changing it. 1.21.1 minecarts add
     * two interpolation ticks internally, so mirror that timing for the visual correction.
     */
    @Inject(
            method = "method_5759(DDDFFI)V",
            at = @At("HEAD"),
            remap = false
    )
    private void minecartspeedfeatures$captureClientInterpolation(
            double x, double y, double z, float yaw, float pitch, int interpolationSteps, CallbackInfo ci
    ) {
        if (!this.method_37908().field_9236) {
            return;
        }

        class_243 start = this.method_19538();
        class_243 target = new class_243(x, y, z);
        double dx = x - start.method_10216();
        double dy = y - start.method_10214();
        double dz = z - start.method_10215();
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
        class_243 pathStart = start;
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

    /** Advance only our render clock. Vanilla still owns the actual client interpolation. */
    @Inject(method = "method_5773()V", at = @At("HEAD"), remap = false)
    private void minecartspeedfeatures$advanceVisualInterpolation(CallbackInfo ci) {
        if (!this.method_37908().field_9236 || !this.minecartspeedfeatures$visualActive) {
            return;
        }
        this.minecartspeedfeatures$visualTicks++;
        if (this.minecartspeedfeatures$visualTicks > this.minecartspeedfeatures$visualTotalTicks) {
            this.minecartspeedfeatures$clearVisualInterpolation();
        }
    }

    @Inject(
            method = "method_7513(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void minecartspeedfeatures$moveOnRail(class_2338 initialPos, class_2680 initialState, CallbackInfo ci) {
        ci.cancel();
        class_243 initialVelocity = this.method_18798();
        this.minecartspeedfeatures$preRailYVelocity = initialVelocity.method_10214();
        double previousLastVelocity = this.minecartspeedfeatures$lastVelocity;
        // Tick-level launch history is sampled exactly once per real server tick. Rail traversal
        // iterations are movement resolution, not pseudo-ticks.
        this.minecartspeedfeatures$lastVelocity = Math.sqrt(initialVelocity.method_1033()) / 2.0 * 0.95;
        this.method_38785();

        MoveIteration iteration = new MoveIteration();
        RailRef rail = this.minecartspeedfeatures$railRef(initialPos, initialState);

        for (int count = 0;
             count < minecartspeedfeatures$MAX_RAIL_ITERATIONS && rail != null
                     && (iteration.initial || iteration.remainingMovement > minecartspeedfeatures$EPSILON);
             count++) {
            class_243 beforeForces = this.method_18798();
            double oldHorizontalSpeed = minecartspeedfeatures$horizontalLength(beforeForces);

            class_2768 shape = rail.shape;
            this.minecartspeedfeatures$lastRail = shape;

            class_243 velocity = this.minecartspeedfeatures$calculateRailVelocity(
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
            this.method_18799(new class_243(velocity.method_10216(), 0.0, velocity.method_10215()));

            MoveResult result = this.minecartspeedfeatures$moveAlongTrack(
                    rail.pos,
                    shape,
                    iteration.remainingMovement
            );
            iteration.remainingMovement = result.remainingMovement;
            iteration.initial = false;

            if (result.stuck) {
                this.method_18799(new class_243(0.0, 0.0, 0.0));
                return;
            }

            if (!result.reachedEndpoint) {
                return;
            }

            RailRef next = this.minecartspeedfeatures$findRailAtCart();
            if (next == null) {
                this.field_44917 = false;

                // ADJACENT_RAIL_POSITIONS_BY_SHAPE stores the upper endpoint with dy == 0.
                // Launch only when leaving that upper endpoint, not when descending off the low end.
                if (minecartspeedfeatures$isAscendingShape(shape) && result.forwardDy == 0) {
                    // 1.1.0 does not update lastVelocity on the launch-transition branch.
                    this.minecartspeedfeatures$lastVelocity = previousLastVelocity;
                    // The old transition fires after one gravity application but before off-rail
                    // movement. Restore that tick-level Y velocity; the launch routine below is
                    // then the literal 1.1.0 additive transform.
                    class_243 launchBase = this.method_18798();
                    this.method_18799(new class_243(
                            launchBase.method_10216(),
                            this.minecartspeedfeatures$preRailYVelocity,
                            launchBase.method_10215()
                    ));
                    this.minecartspeedfeatures$launchFromRail110();
                }
                return;
            }

            this.field_44917 = true;
            rail = next;
        }

        // The cap is a corruption/loop guard, never a speed-derived pseudo-tick count.
        if (iteration.remainingMovement > minecartspeedfeatures$EPSILON) {
            this.method_18799(new class_243(0.0, 0.0, 0.0));
        }
    }

    @Inject(
            method = "method_7504()D",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void minecartspeedfeatures$getMaxSpeed(CallbackInfoReturnable<Double> cir) {
        if (MinecartSpeedFeatures.MINECART_MAX_SPEED != null) {
            cir.setReturnValue(this.minecartspeedfeatures$maxSpeedBlocksPerTick());
        }
    }

    @Unique
    private class_243 minecartspeedfeatures$calculateRailVelocity(
            class_243 rawVelocity,
            MoveIteration iteration,
            class_2338 railPos,
            class_2680 railState,
            class_2768 shape
    ) {
        double x = rawVelocity.method_10216();
        double z = rawVelocity.method_10215();

        // Mojang's experimental slope force is applied at most once per real tick.
        if (!iteration.slopeVelocityApplied && minecartspeedfeatures$isAscendingShape(shape)) {
            double slope = Math.max(0.0078125, Math.hypot(x, z) * 0.02);
            if (this.method_5799()) {
                slope *= 0.2;
            }
            switch (shape.ordinal()) {
                case 2 -> x -= slope; // ASCENDING_EAST
                case 3 -> x += slope; // ASCENDING_WEST
                case 4 -> z += slope; // ASCENDING_NORTH
                case 5 -> z -= slope; // ASCENDING_SOUTH
                default -> { }
            }
            iteration.slopeVelocityApplied = true;
        }

        // Preserve 1.21.1 rider nudging, but run it only once for this server tick.
        if (iteration.initial) {
            class_1297 passenger = this.method_31483();
            if (passenger instanceof class_1657) {
                class_243 riderVelocity = passenger.method_18798();
                double riderSq = riderVelocity.method_10216() * riderVelocity.method_10216()
                        + riderVelocity.method_10215() * riderVelocity.method_10215();
                double cartSq = x * x + z * z;
                if (riderSq > 1.0E-4 && cartSq < 0.01) {
                    x += riderVelocity.method_10216() * 0.1;
                    z += riderVelocity.method_10215() * 0.1;
                    iteration.decelerated = true;
                }
            }
        }

        // Vanilla only treats Blocks.POWERED_RAIL as a propulsion/braking rail. Activator rails
        // share the PoweredRailBlock implementation, so a blanket instanceof check would be wrong.
        // The Copper Rail 0.9.4 also extends PoweredRailBlock, but its own mixin explicitly makes
        // only that custom block participate in powered-rail logic. Mirror that exact exception.
        boolean copperRail = minecartspeedfeatures$isCopperRail(railState);
        boolean poweredRail = railState.method_26204() == class_2246.field_10425 || copperRail;
        boolean powered = poweredRail && Boolean.TRUE.equals(railState.method_11654(class_2442.field_11364));

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
            double retention = this.method_5782() ? 0.997 : 0.975;
            x *= retention;
            z *= retention;
            if (this.method_5799()) {
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
            double push = Boolean.TRUE.equals(railState.method_11654(class_2741.field_12501)) ? -0.5 : 0.5;
            switch (shape.ordinal()) {
                case 1, 2, 3 -> x += push; // EAST_WEST / ascending east-west
                case 0, 4, 5 -> z += push; // NORTH_SOUTH / ascending north-south
                default -> { }
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
                int px = railPos.method_10263();
                int py = railPos.method_10264();
                int pz = railPos.method_10260();
                if (ordinal == 1) { // EAST_WEST
                    if (this.minecartspeedfeatures$willHitBlockAt(new class_2338(px - 1, py, pz))) {
                        x = 0.02;
                    } else if (this.minecartspeedfeatures$willHitBlockAt(new class_2338(px + 1, py, pz))) {
                        x = -0.02;
                    }
                } else if (ordinal == 0) { // NORTH_SOUTH
                    if (this.minecartspeedfeatures$willHitBlockAt(new class_2338(px, py, pz - 1))) {
                        z = 0.02;
                    } else if (this.minecartspeedfeatures$willHitBlockAt(new class_2338(px, py, pz + 1))) {
                        z = -0.02;
                    }
                }
                if (x != 0.0 || z != 0.0) {
                    iteration.accelerated = true;
                }
            }
        }

        return new class_243(x, 0.0, z);
    }

    @Unique
    private MoveResult minecartspeedfeatures$moveAlongTrack(
            class_2338 railPos,
            class_2768 shape,
            double remainingMovement
    ) {
        if (remainingMovement < minecartspeedfeatures$EPSILON) {
            return new MoveResult(0.0, false, false, 0);
        }

        class_243 start = this.method_19538();
        class_243 horizontalVelocity = this.method_18798();
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

        double dotA = horizontalVelocity.method_10216() * ax + horizontalVelocity.method_10215() * az;
        double dotB = horizontalVelocity.method_10216() * bx + horizontalVelocity.method_10215() * bz;
        int fx = dotA < dotB ? bx : ax;
        int fy = dotA < dotB ? by : ay;
        int fz = dotA < dotB ? bz : az;

        double hlen = Math.hypot(fx, fz);
        if (hlen < minecartspeedfeatures$EPSILON) {
            return new MoveResult(0.0, false, true, fy);
        }

        double ux = fx / hlen;
        double uz = fz / hlen;
        double targetX = railPos.method_10263() + 0.5 + fx * 0.5 + ux * minecartspeedfeatures$EPSILON;
        double targetY = railPos.method_10264() + 0.1;
        double targetZ = railPos.method_10260() + 0.5 + fz * 0.5 + uz * minecartspeedfeatures$EPSILON;

        boolean slope = ay != by;
        if (slope && !minecartspeedfeatures$ascends(horizontalVelocity, ordinal)) {
            targetY += 1.0;
        }

        double dxToTarget = targetX - start.method_10216();
        double dyToTarget = targetY - start.method_10214();
        double dzToTarget = targetZ - start.method_10215();
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
        double candidateX = start.method_10216() + dirX * requestedPath;
        double candidateY = start.method_10214() + dirY * requestedPath;
        double candidateZ = start.method_10215() + dirZ * requestedPath;

        double startToTargetSq = minecartspeedfeatures$squaredDistance(
                start.method_10216(), start.method_10214(), start.method_10215(),
                targetX, targetY, targetZ
        );
        double startToCandidateSq = minecartspeedfeatures$squaredDistance(
                start.method_10216(), start.method_10214(), start.method_10215(),
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

        double requestedDx = desiredX - start.method_10216();
        double requestedDy = desiredY - start.method_10214();
        double requestedDz = desiredZ - start.method_10215();
        this.method_5784(class_1313.field_6308, new class_243(requestedDx, requestedDy, requestedDz));

        // Keep the minecart on the rail centerline vertically even when the ascending rail's
        // support/front geometry would otherwise clip the generic entity movement.
        if (slope) {
            class_243 actual = this.method_19538();
            double distanceFromEndpoint = Math.hypot(targetX - actual.method_10216(), targetZ - actual.method_10215());
            double expectedY = targetY + (minecartspeedfeatures$ascends(horizontalVelocity, ordinal)
                    ? distanceFromEndpoint
                    : -distanceFromEndpoint);
            if (actual.method_10214() < expectedY) {
                this.method_33574(new class_243(actual.method_10216(), expectedY, actual.method_10215()));
            }
        }

        class_243 actual = this.method_19538();
        double actualMoveSq = minecartspeedfeatures$squaredDistance(
                start.method_10216(), start.method_10214(), start.method_10215(),
                actual.method_10216(), actual.method_10214(), actual.method_10215()
        );
        double requestedMoveSq = requestedDx * requestedDx + requestedDy * requestedDy + requestedDz * requestedDz;
        if (requestedMoveSq > minecartspeedfeatures$EPSILON * minecartspeedfeatures$EPSILON
                && actualMoveSq < minecartspeedfeatures$EPSILON * minecartspeedfeatures$EPSILON) {
            return new MoveResult(0.0, false, true, fy);
        }

        this.method_18799(new class_243(tangentX, 0.0, tangentZ));
        return new MoveResult(newRemaining, reachedEndpoint, false, fy);
    }

    @Override
    public class_243 minecartspeedfeatures$getVisualRideOffset(float tickDelta) {
        if (!this.minecartspeedfeatures$visualActive || this.minecartspeedfeatures$visualPath == null) {
            return new class_243(0.0, 0.0, 0.0);
        }

        double progress = this.minecartspeedfeatures$visualProgress(tickDelta);
        class_243 desired = this.minecartspeedfeatures$visualPath.sample(progress);
        class_243 baseline = minecartspeedfeatures$lerp(
                this.minecartspeedfeatures$visualStart,
                this.minecartspeedfeatures$visualTarget,
                progress
        );
        return new class_243(
                desired.method_10216() - baseline.method_10216(),
                desired.method_10214() - baseline.method_10214(),
                desired.method_10215() - baseline.method_10215()
        );
    }

    @Override
    public class_243 minecartspeedfeatures$getVisualBodyOffset(float tickDelta) {
        if (!this.minecartspeedfeatures$visualActive || this.minecartspeedfeatures$visualPath == null) {
            return new class_243(0.0, 0.0, 0.0);
        }

        double progress = this.minecartspeedfeatures$visualProgress(tickDelta);
        class_243 desired = this.minecartspeedfeatures$visualPath.sample(progress);
        class_243 linear = minecartspeedfeatures$lerp(
                this.minecartspeedfeatures$visualStart,
                this.minecartspeedfeatures$visualTarget,
                progress
        );

        // MinecartEntityRenderer performs another rail snap after EntityRenderer's position
        // offset. Compensate for that existing snap instead of fighting or replacing it.
        class_243 rendererAnchor = this.minecartspeedfeatures$rendererRailAnchor(linear);
        return new class_243(
                desired.method_10216() - rendererAnchor.method_10216(),
                desired.method_10214() - rendererAnchor.method_10214(),
                desired.method_10215() - rendererAnchor.method_10215()
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
        double progress = (completed - 1.0 + Math.max(0.0, Math.min(1.0, tickDelta)))
                / this.minecartspeedfeatures$visualTotalTicks;
        return Math.max(0.0, Math.min(1.0, progress));
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
    private VisualPath minecartspeedfeatures$buildVisualRailPath(class_243 start, class_243 target) {
        // Do not convert an airborne server snapshot into a rail path just because a rail happens
        // to be one or two blocks below it. Vanilla's own snap routine is the authority for whether
        // each endpoint is actually associated with a rail. This preserves the 1.1.0-style
        // parabolic launch and ordinary off-rail gravity unchanged.
        class_243 snappedStart = this.method_7508(
                start.method_10216(), start.method_10214(), start.method_10215()
        );
        class_243 snappedTarget = this.method_7508(
                target.method_10216(), target.method_10214(), target.method_10215()
        );
        if (snappedStart == null || snappedTarget == null) {
            return null;
        }

        RailRef startRail = this.minecartspeedfeatures$findRailNear(snappedStart);
        RailRef targetRail = this.minecartspeedfeatures$findRailNear(snappedTarget);
        if (startRail == null || targetRail == null || startRail.pos.equals(targetRail.pos)) {
            return null;
        }

        class_243 velocity = this.method_18798();
        double dirX = velocity.method_10216();
        double dirZ = velocity.method_10215();
        double dirLen = Math.hypot(dirX, dirZ);
        if (dirLen < minecartspeedfeatures$EPSILON) {
            dirX = target.method_10216() - start.method_10216();
            dirZ = target.method_10215() - start.method_10215();
            dirLen = Math.hypot(dirX, dirZ);
        }
        if (dirLen < minecartspeedfeatures$EPSILON) {
            return null;
        }
        dirX /= dirLen;
        dirZ /= dirLen;

        List<class_243> points = new ArrayList<>();
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

            double boundaryX = rail.pos.method_10263() + 0.5 + fx * 0.5;
            double boundaryZ = rail.pos.method_10260() + 0.5 + fz * 0.5;
            class_243 boundary = this.minecartspeedfeatures$snapForVisualPath(
                    boundaryX, rail.pos.method_10264() + 0.5, boundaryZ
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
    private RailRef minecartspeedfeatures$findRailNear(class_243 point) {
        int x = minecartspeedfeatures$floor(point.method_10216());
        int y = minecartspeedfeatures$floor(point.method_10214());
        int z = minecartspeedfeatures$floor(point.method_10215());
        class_1937 world = this.method_37908();
        int[] offsets = {-1, 0, 1, -2, 2};
        for (int offset : offsets) {
            class_2338 pos = new class_2338(x, y + offset, z);
            class_2680 state = world.method_8320(pos);
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
            class_243 boundary,
            double dirX,
            double dirZ
    ) {
        double probeX = boundary.method_10216() + dirX * 0.02;
        double probeZ = boundary.method_10215() + dirZ * 0.02;
        int x = minecartspeedfeatures$floor(probeX);
        int z = minecartspeedfeatures$floor(probeZ);
        int baseY = minecartspeedfeatures$floor(boundary.method_10214());
        class_1937 world = this.method_37908();

        RailRef best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dy = -2; dy <= 2; dy++) {
            class_2338 pos = new class_2338(x, baseY + dy, z);
            class_2680 state = world.method_8320(pos);
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
            double score = Math.abs((candidate.pos.method_10264() + 0.5) - boundary.method_10214());
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    @Unique
    private static boolean minecartspeedfeatures$railConnectsToward(RailRef rail, class_2338 other) {
        int ordinal = rail.shape.ordinal();
        int dx = other.method_10263() - rail.pos.method_10263();
        int dz = other.method_10260() - rail.pos.method_10260();
        return (minecartspeedfeatures$endpointAX(ordinal) == dx && minecartspeedfeatures$endpointAZ(ordinal) == dz)
                || (minecartspeedfeatures$endpointBX(ordinal) == dx && minecartspeedfeatures$endpointBZ(ordinal) == dz);
    }

    @Unique
    private class_243 minecartspeedfeatures$snapForVisualPath(double x, double y, double z) {
        class_243 snapped = this.method_7508(x, y, z);
        if (snapped != null) {
            return snapped;
        }
        snapped = this.method_7508(x, y + 1.0, z);
        if (snapped != null) {
            return snapped;
        }
        return this.method_7508(x, y - 1.0, z);
    }

    @Unique
    private class_243 minecartspeedfeatures$rendererRailAnchor(class_243 linear) {
        class_243 center = this.method_7508(
                linear.method_10216(), linear.method_10214(), linear.method_10215()
        );
        if (center == null) {
            return linear;
        }

        class_243 ahead = this.method_7505(
                linear.method_10216(), linear.method_10214(), linear.method_10215(), 0.3
        );
        class_243 behind = this.method_7505(
                linear.method_10216(), linear.method_10214(), linear.method_10215(), -0.3
        );
        if (ahead == null) {
            ahead = center;
        }
        if (behind == null) {
            behind = center;
        }
        return new class_243(
                center.method_10216(),
                (ahead.method_10214() + behind.method_10214()) * 0.5,
                center.method_10215()
        );
    }

    @Unique
    private static class_243 minecartspeedfeatures$lerp(class_243 a, class_243 b, double t) {
        return new class_243(
                a.method_10216() + (b.method_10216() - a.method_10216()) * t,
                a.method_10214() + (b.method_10214() - a.method_10214()) * t,
                a.method_10215() + (b.method_10215() - a.method_10215()) * t
        );
    }

    /** Literal 1.1.0 launchFromRail transformation, using the old constants and branch logic. */
    @Unique
    private void minecartspeedfeatures$launchFromRail110() {
        if (this.minecartspeedfeatures$lastRail == null) {
            return;
        }

        switch (this.minecartspeedfeatures$lastRail.ordinal()) {
            case 2, 3 -> this.method_5762(
                    (this.minecartspeedfeatures$lastVelocity - 0.0078125)
                            * Math.signum(this.method_18798().method_10216()),
                    this.minecartspeedfeatures$lastVelocity * (this.method_23318() < this.field_6036 ? -1.0 : 1.0),
                    0.0
            );
            case 4, 5 -> this.method_5762(
                    0.0,
                    this.minecartspeedfeatures$lastVelocity * (this.method_23318() < this.field_6036 ? -1.0 : 1.0),
                    (this.minecartspeedfeatures$lastVelocity - 0.0078125)
                            * Math.signum(this.method_18798().method_10215())
            );
            default -> { }
        }

        class_243 launchedVelocity = this.method_18798();
        class_243 launchedPosition = this.method_19538()
                .method_1019(launchedVelocity.method_18805(0.0, 0.95, 0.0))
                .method_1031(0.0, this.method_23318() < this.field_6036 ? 0.0 : 0.3, 0.0);
        this.method_33574(launchedPosition);
        this.method_22862();
        this.method_23311();
        this.field_5960 = false;
    }

    @Unique
    private RailRef minecartspeedfeatures$findRailAtCart() {
        int x = minecartspeedfeatures$floor(this.method_23317());
        int y = minecartspeedfeatures$floor(this.method_23318());
        int z = minecartspeedfeatures$floor(this.method_23321());
        class_1937 world = this.method_37908();

        class_2338 belowPos = new class_2338(x, y - 1, z);
        class_2680 belowState = world.method_8320(belowPos);
        if (class_2241.method_9476(belowState)) {
            return this.minecartspeedfeatures$railRef(belowPos, belowState);
        }

        class_2338 pos = new class_2338(x, y, z);
        class_2680 state = world.method_8320(pos);
        return this.minecartspeedfeatures$railRef(pos, state);
    }

    @Unique
    private RailRef minecartspeedfeatures$railRef(class_2338 pos, class_2680 state) {
        if (!class_2241.method_9476(state)) {
            return null;
        }
        class_2248 block = state.method_26204();
        if (!(block instanceof class_2241 rail)) {
            return null;
        }
        class_2768 shape = (class_2768) state.method_11654(rail.method_9474());
        return new RailRef(pos, state, shape);
    }

    @Unique
    private static boolean minecartspeedfeatures$isCopperRail(class_2680 state) {
        // Optional integration without a class/link dependency on The Copper Rail. User-defined
        // class names are not remapped by Loom, so this stays stable for the 0.9.4 1.21.1 build.
        return state.method_26204().getClass().getName().equals("com.thecopperrail.CopperRailBlock");
    }

    @Unique
    private boolean minecartspeedfeatures$willHitBlockAt(class_2338 pos) {
        // This helper intentionally invokes the target's private vanilla method through a shadow
        // surrogate generated below, keeping the stationary powered-rail kick vanilla-like.
        return this.method_18803(pos);
    }

    @Shadow(remap = false)
    private boolean method_18803(class_2338 pos) {
        throw new AssertionError();
    }

    @Unique
    private double minecartspeedfeatures$maxSpeedBlocksPerTick() {
        if (MinecartSpeedFeatures.MINECART_MAX_SPEED == null) {
            return (this.method_5799() ? 4.0 : 8.0) / 20.0;
        }
        double result = this.method_37908().method_8450().method_8356(MinecartSpeedFeatures.MINECART_MAX_SPEED) / 20.0;
        if (this.method_5799()) {
            result *= 0.5;
        }
        return result;
    }

    @Unique
    private static boolean minecartspeedfeatures$isAscendingShape(class_2768 shape) {
        int ordinal = shape.ordinal();
        return ordinal >= 2 && ordinal <= 5;
    }

    /** Matches the newer controller's direction test (despite the counterintuitive name). */
    @Unique
    private static boolean minecartspeedfeatures$ascends(class_243 velocity, int shapeOrdinal) {
        return switch (shapeOrdinal) {
            case 2 -> velocity.method_10216() < 0.0;
            case 3 -> velocity.method_10216() > 0.0;
            case 4 -> velocity.method_10215() > 0.0;
            case 5 -> velocity.method_10215() < 0.0;
            default -> false;
        };
    }

    @Unique
    private static double minecartspeedfeatures$horizontalLength(class_243 velocity) {
        return Math.hypot(velocity.method_10216(), velocity.method_10215());
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
    @Unique private static int minecartspeedfeatures$endpointAX(int o) { return switch (o) {
        case 1, 2, 3 -> -1;
        default -> 0;
    }; }
    @Unique private static int minecartspeedfeatures$endpointAY(int o) { return switch (o) {
        case 2 -> -1;
        case 5 -> -1;
        default -> 0;
    }; }
    @Unique private static int minecartspeedfeatures$endpointAZ(int o) { return switch (o) {
        case 0, 4, 8, 9 -> -1;
        case 5 -> -1;
        case 6, 7 -> 1;
        default -> 0;
    }; }
    @Unique private static int minecartspeedfeatures$endpointBX(int o) { return switch (o) {
        case 1, 2, 3, 6, 9 -> 1;
        case 7, 8 -> -1;
        default -> 0;
    }; }
    @Unique private static int minecartspeedfeatures$endpointBY(int o) { return switch (o) {
        case 3, 4 -> -1;
        default -> 0;
    }; }
    @Unique private static int minecartspeedfeatures$endpointBZ(int o) { return switch (o) {
        case 0, 4, 5 -> 1;
        default -> 0;
    }; }

}
