package ilostmy_fish.mixin;

import ilostmy_fish.network.MinecartTrajectoryNetworking;
import ilostmy_fish.rail.RailEndpoint;
import ilostmy_fish.rail.RailRef;
import ilostmy_fish.rail.RailResolver;
import ilostmy_fish.rail.RailTraversalContext;
import ilostmy_fish.rail.RailTraversalListener;
import ilostmy_fish.trajectory.MinecartTrajectory;
import ilostmy_fish.trajectory.ServerTrajectoryBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the tick's single rail dispatch with a rail-by-rail traversal.
 *
 * <p>This mixin deliberately does not replace {@code moveOnRail}. Every visited rail receives a
 * real call with its own position and state, so vanilla branches and other mods' injections retain
 * ownership of rail behavior. The two narrow movement injections below only shorten vanilla's
 * internal entity movement and account for the fraction of tick time it consumed.</p>
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartRailTraversalMixin extends Entity {
    @Unique
    private static final int minecartspeedfeatures$MAX_RAILS_PER_TICK = 4096;
    @Unique
    private static final double minecartspeedfeatures$ORIENTATION_SAMPLE_OFFSET = 0.3;
    @Unique
    private static final double minecartspeedfeatures$ORIENTATION_EPSILON_SQUARED = 1.0E-18;

    @Shadow
    private boolean onRail;

    @Shadow
    protected abstract void moveOnRail(BlockPos pos, BlockState state);

    @Shadow
    protected abstract void moveOffRail();

    @Shadow
    public abstract void onActivatorRail(int x, int y, int z, boolean powered);

    @Shadow
    @Nullable
    public abstract Vec3d snapPositionToRailWithOffset(
            double x,
            double y,
            double z,
            double offset
    );

    @Unique
    private RailTraversalContext minecartspeedfeatures$traversal;
    @Unique
    private ServerTrajectoryBuilder minecartspeedfeatures$trajectoryBuilder;

    protected MinecartRailTraversalMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void minecartspeedfeatures$beginTrajectoryTick(CallbackInfo ci) {
        if (!this.getWorld().isClient) {
            this.minecartspeedfeatures$trajectoryBuilder = new ServerTrajectoryBuilder(
                    this.getWorld().getTime(),
                    this.getPos(),
                    this.minecartspeedfeatures$getOrientationHint()
            );
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void minecartspeedfeatures$sendTrajectory(CallbackInfo ci) {
        ServerTrajectoryBuilder builder = this.minecartspeedfeatures$trajectoryBuilder;
        this.minecartspeedfeatures$trajectoryBuilder = null;
        if (builder == null || this.isRemoved()) {
            return;
        }

        MinecartTrajectory trajectory = builder.finish(this.getPos(), this.getVelocity());
        MinecartTrajectoryNetworking.send(
                (AbstractMinecartEntity)(Object)this,
                trajectory
        );
    }

    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V"
            )
    )
    private void minecartspeedfeatures$traverseRails(
            AbstractMinecartEntity minecart,
            BlockPos initialPos,
            BlockState initialState
    ) {
        ServerTrajectoryBuilder trajectoryBuilder = this.minecartspeedfeatures$trajectoryBuilder;
        RailRef vanillaRail = RailResolver.from(initialPos, initialState);
        if (vanillaRail == null) {
            // This should be unreachable because tick checked AbstractRailBlock.isRail first. Keep
            // vanilla's call as the least surprising fallback for unusual transformed methods.
            this.moveOnRail(initialPos, initialState);
            return;
        }

        RailRef rail = RailResolver.atTraversalStart(
                this.getWorld(),
                vanillaRail,
                this.getPos(),
                this.getVelocity()
        );
        if (rail == null) {
            // The cart ended the previous tick exactly on a negative-facing exit. Vanilla floor
            // still points at the old rail, but the travel direction owns the ground beyond it.
            this.onRail = false;
            this.moveOffRail();
            return;
        }

        RailTraversalContext traversal = new RailTraversalContext();
        this.minecartspeedfeatures$traversal = traversal;
        RailTraversalListener listener = (Object)this instanceof RailTraversalListener candidate
                ? candidate
                : null;
        int visitedRails = 0;
        try {
            if (listener != null) {
                listener.minecartspeedfeatures$beginRailTraversal(this.getVelocity());
            }

            while (traversal.hasTimeRemaining()
                    && visitedRails < minecartspeedfeatures$MAX_RAILS_PER_TICK) {
                traversal.beginSegment(rail);

                // This is the normal target method, not a copied rail-force implementation. Mixin
                // hooks from Copper Rail and other rail mods run on every iteration.
                this.moveOnRail(rail.pos(), rail.state());
                visitedRails++;

                if (trajectoryBuilder != null) {
                    trajectoryBuilder.record(
                            traversal.elapsedTime(),
                            this.getPos()
                    );
                }

                this.minecartspeedfeatures$activateRail(rail);
                if (this.isRemoved() || !traversal.interceptedMovement()
                        || traversal.wasBlocked() || !traversal.reachedBoundary()) {
                    break;
                }

                RailEndpoint exitEndpoint = traversal.exitEndpoint();
                RailRef nextRail = exitEndpoint == null
                        ? null
                        : RailResolver.afterBoundary(
                                this.getWorld(), rail, this.getPos(), exitEndpoint
                        );
                if (nextRail == null) {
                    this.onRail = false;
                    Vec3d positionAfterMove = traversal.positionAfterMove();
                    if (positionAfterMove != null) {
                        // At a minimum-coordinate face, vanilla floor still sees the old rail and
                        // its post-move snap raises the cart back onto it. A maximum-coordinate
                        // face sees the ground and keeps Entity.move's height. Preserve the latter
                        // handoff in both directions without changing horizontal displacement.
                        this.setPosition(
                                this.getX(), positionAfterMove.getY(), this.getZ()
                        );
                    }
                    if (listener != null && exitEndpoint != null) {
                        listener.minecartspeedfeatures$leaveRail(rail, exitEndpoint);
                    }
                    break;
                }

                // A completed face must resolve to a different block. Refuse malformed rail
                // ownership that would cycle without consuming any time.
                if (nextRail.pos().equals(rail.pos())) {
                    break;
                }

                this.onRail = true;
                rail = nextRail;
            }

            // This is a corruption/loop guard, not a speed-derived iteration budget. Refuse to
            // carry an unbounded traversal into entity collision code if malformed rails cycle.
            if (visitedRails >= minecartspeedfeatures$MAX_RAILS_PER_TICK
                    && traversal.hasTimeRemaining()) {
                this.setVelocity(Vec3d.ZERO);
            }
        } finally {
            this.minecartspeedfeatures$traversal = null;
        }
    }

    /**
     * The traversal invokes activator behavior for every activator rail it visits. Suppress the
     * original tick's one initial-state callback so the first activator is not processed twice.
     */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;onActivatorRail(IIIZ)V"
            )
    )
    private void minecartspeedfeatures$activatorHandledByTraversal(
            AbstractMinecartEntity minecart,
            int x,
            int y,
            int z,
            boolean powered
    ) {
        // Handled immediately after that rail's moveOnRail call in traverseRails.
    }

    @ModifyArg(
            method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"
            ),
            index = 1
    )
    private Vec3d minecartspeedfeatures$limitMovementToRailSegment(Vec3d requestedMovement) {
        RailTraversalContext traversal = this.minecartspeedfeatures$traversal;
        if (traversal == null) {
            return requestedMovement;
        }
        return traversal.limitMovement(this.getPos(), requestedMovement);
    }

    @Inject(
            method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void minecartspeedfeatures$recordSegmentMovement(
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        RailTraversalContext traversal = this.minecartspeedfeatures$traversal;
        if (traversal != null) {
            traversal.completeMovement(this.getPos());
        }
    }

    @Unique
    private void minecartspeedfeatures$activateRail(RailRef rail) {
        if (!rail.state().isOf(Blocks.ACTIVATOR_RAIL)) {
            return;
        }

        BlockPos pos = rail.pos();
        this.onActivatorRail(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                rail.state().get(PoweredRailBlock.POWERED)
        );
    }

    @Unique
    private Vec3d minecartspeedfeatures$getOrientationHint() {
        Vec3d preferredDirection = this.getVelocity();
        if (preferredDirection.lengthSquared()
                <= minecartspeedfeatures$ORIENTATION_EPSILON_SQUARED) {
            double yaw = Math.toRadians(this.getYaw());
            // Minecart yaw is measured from the model's rail axis rather than Entity's usual
            // look-vector convention. This conversion reproduces the renderer's facing.
            preferredDirection = new Vec3d(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        }

        Vec3d position = this.getPos();
        Vec3d forward = this.snapPositionToRailWithOffset(
                position.getX(),
                position.getY(),
                position.getZ(),
                minecartspeedfeatures$ORIENTATION_SAMPLE_OFFSET
        );
        Vec3d backward = this.snapPositionToRailWithOffset(
                position.getX(),
                position.getY(),
                position.getZ(),
                -minecartspeedfeatures$ORIENTATION_SAMPLE_OFFSET
        );
        if (forward == null || backward == null) {
            return preferredDirection;
        }

        Vec3d tangent = forward.subtract(backward);
        if (!Double.isFinite(tangent.getX())
                || !Double.isFinite(tangent.getY())
                || !Double.isFinite(tangent.getZ())
                || tangent.lengthSquared()
                        <= minecartspeedfeatures$ORIENTATION_EPSILON_SQUARED) {
            return preferredDirection;
        }
        if (tangent.dotProduct(preferredDirection) < 0.0) {
            tangent = tangent.multiply(-1.0);
        }
        return tangent;
    }
}
