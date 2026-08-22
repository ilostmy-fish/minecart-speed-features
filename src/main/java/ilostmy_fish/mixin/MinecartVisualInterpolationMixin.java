package ilostmy_fish.mixin;

import ilostmy_fish.interpolation.VisualInterpolationAccess;
import ilostmy_fish.interpolation.VisualPath;
import ilostmy_fish.rail.RailEndpoint;
import ilostmy_fish.rail.RailGeometry;
import ilostmy_fish.rail.RailRef;
import ilostmy_fish.rail.RailResolver;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Client-only visual path reconstruction, separate from authoritative rail traversal. */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartVisualInterpolationMixin extends Entity implements VisualInterpolationAccess {
    @Unique
    private static final double minecartspeedfeatures$EPSILON = 1.0E-5;
    @Unique
    private static final int minecartspeedfeatures$MAX_VISUAL_RAILS = 2048;
    @Unique
    private static final double minecartspeedfeatures$MIN_CORRECTION_DISTANCE = 1.5;

    @Shadow
    public abstract Vec3d snapPositionToRail(double x, double y, double z);

    @Shadow
    public abstract Vec3d snapPositionToRailWithOffset(double x, double y, double z, double offset);

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

    protected MinecartVisualInterpolationMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "updateTrackedPositionAndAngles(DDDFFI)V", at = @At("HEAD"))
    private void minecartspeedfeatures$captureClientInterpolation(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int interpolationSteps,
            CallbackInfo ci
    ) {
        if (!this.getWorld().isClient) {
            return;
        }

        Vec3d start = this.getPos();
        Vec3d target = new Vec3d(x, y, z);
        if (start.squaredDistanceTo(target) < minecartspeedfeatures$MIN_CORRECTION_DISTANCE
                * minecartspeedfeatures$MIN_CORRECTION_DISTANCE) {
            this.minecartspeedfeatures$clearVisualInterpolation();
            return;
        }

        Vec3d pathStart = start;
        if (this.minecartspeedfeatures$visualActive && this.minecartspeedfeatures$visualPath != null) {
            pathStart = this.minecartspeedfeatures$visualPath.sample(
                    this.minecartspeedfeatures$visualProgress(1.0F)
            );
        }

        VisualPath path = this.minecartspeedfeatures$buildVisualRailPath(pathStart, target);
        if (path == null) {
            this.minecartspeedfeatures$clearVisualInterpolation();
            return;
        }

        this.minecartspeedfeatures$visualPath = path;
        this.minecartspeedfeatures$visualStart = start;
        this.minecartspeedfeatures$visualTarget = target;
        // The 1.21.1 minecart adds two interpolation ticks internally.
        this.minecartspeedfeatures$visualTotalTicks = Math.max(1, interpolationSteps + 2);
        this.minecartspeedfeatures$visualTicks = 0;
        this.minecartspeedfeatures$visualActive = true;
    }

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

    @Override
    public Vec3d minecartspeedfeatures$getVisualRideOffset(float tickDelta) {
        if (!this.minecartspeedfeatures$visualActive || this.minecartspeedfeatures$visualPath == null) {
            return Vec3d.ZERO;
        }

        double progress = this.minecartspeedfeatures$visualProgress(tickDelta);
        Vec3d desired = this.minecartspeedfeatures$visualPath.sample(progress);
        Vec3d baseline = minecartspeedfeatures$lerp(
                this.minecartspeedfeatures$visualStart,
                this.minecartspeedfeatures$visualTarget,
                progress
        );
        return desired.subtract(baseline);
    }

    @Override
    public Vec3d minecartspeedfeatures$getVisualBodyOffset(float tickDelta) {
        if (!this.minecartspeedfeatures$visualActive || this.minecartspeedfeatures$visualPath == null) {
            return Vec3d.ZERO;
        }

        double progress = this.minecartspeedfeatures$visualProgress(tickDelta);
        Vec3d desired = this.minecartspeedfeatures$visualPath.sample(progress);
        Vec3d linear = minecartspeedfeatures$lerp(
                this.minecartspeedfeatures$visualStart,
                this.minecartspeedfeatures$visualTarget,
                progress
        );

        // MinecartEntityRenderer performs its own snap. Compensate for that existing anchor.
        Vec3d rendererAnchor = this.minecartspeedfeatures$rendererRailAnchor(linear);
        return desired.subtract(rendererAnchor);
    }

    @Unique
    private double minecartspeedfeatures$visualProgress(float tickDelta) {
        int completed = Math.max(0, this.minecartspeedfeatures$visualTicks);
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
        // Airborne snapshots stay on vanilla interpolation even if a rail happens to be below.
        Vec3d snappedStart = this.snapPositionToRail(start.getX(), start.getY(), start.getZ());
        Vec3d snappedTarget = this.snapPositionToRail(target.getX(), target.getY(), target.getZ());
        if (snappedStart == null || snappedTarget == null) {
            return null;
        }

        RailRef startRail = this.minecartspeedfeatures$findRailNear(snappedStart);
        RailRef targetRail = this.minecartspeedfeatures$findRailNear(snappedTarget);
        if (startRail == null || targetRail == null || startRail.pos().equals(targetRail.pos())) {
            return null;
        }

        Vec3d velocity = this.getVelocity();
        double directionX = velocity.getX();
        double directionZ = velocity.getZ();
        double directionLength = Math.hypot(directionX, directionZ);
        if (directionLength < minecartspeedfeatures$EPSILON) {
            directionX = target.getX() - start.getX();
            directionZ = target.getZ() - start.getZ();
            directionLength = Math.hypot(directionX, directionZ);
        }
        if (directionLength < minecartspeedfeatures$EPSILON) {
            return null;
        }
        directionX /= directionLength;
        directionZ /= directionLength;

        List<Vec3d> points = new ArrayList<>();
        points.add(start);
        RailRef rail = startRail;
        RailRef previous = null;

        for (int count = 0; count < minecartspeedfeatures$MAX_VISUAL_RAILS; count++) {
            if (rail.pos().equals(targetRail.pos())) {
                points.add(target);
                return VisualPath.create(points);
            }

            RailEndpoint forward = RailGeometry.exitEndpoint(rail.shape(), directionX, directionZ);
            double horizontal = Math.hypot(forward.x(), forward.z());
            if (horizontal < minecartspeedfeatures$EPSILON) {
                return null;
            }
            double outX = forward.x() / horizontal;
            double outZ = forward.z() / horizontal;

            double boundaryX = rail.pos().getX() + 0.5 + forward.x() * 0.5;
            double boundaryZ = rail.pos().getZ() + 0.5 + forward.z() * 0.5;
            Vec3d boundary = this.minecartspeedfeatures$snapForVisualPath(
                    boundaryX,
                    rail.pos().getY() + 0.5,
                    boundaryZ
            );
            if (boundary == null) {
                return null;
            }
            points.add(boundary);

            RailRef next = this.minecartspeedfeatures$findRailAhead(
                    rail,
                    previous,
                    boundary,
                    outX,
                    outZ
            );
            if (next == null) {
                return null;
            }
            previous = rail;
            rail = next;
            directionX = outX;
            directionZ = outZ;
        }

        return null;
    }

    @Unique
    private RailRef minecartspeedfeatures$findRailNear(Vec3d point) {
        int x = MathHelper.floor(point.getX());
        int y = MathHelper.floor(point.getY());
        int z = MathHelper.floor(point.getZ());
        int[] offsets = {-1, 0, 1, -2, 2};
        for (int offset : offsets) {
            BlockPos pos = new BlockPos(x, y + offset, z);
            RailRef rail = RailResolver.from(pos, this.getWorld().getBlockState(pos));
            if (rail != null) {
                return rail;
            }
        }
        return null;
    }

    @Unique
    private RailRef minecartspeedfeatures$findRailAhead(
            RailRef current,
            RailRef previous,
            Vec3d boundary,
            double directionX,
            double directionZ
    ) {
        int x = MathHelper.floor(boundary.getX() + directionX * 0.02);
        int z = MathHelper.floor(boundary.getZ() + directionZ * 0.02);
        int baseY = MathHelper.floor(boundary.getY());

        RailRef best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dy = -2; dy <= 2; dy++) {
            BlockPos pos = new BlockPos(x, baseY + dy, z);
            BlockState state = this.getWorld().getBlockState(pos);
            RailRef candidate = RailResolver.from(pos, state);
            if (candidate == null || candidate.pos().equals(current.pos())) {
                continue;
            }
            if (previous != null && candidate.pos().equals(previous.pos())) {
                continue;
            }
            if (!RailGeometry.connectsHorizontallyToward(candidate, current.pos())) {
                continue;
            }

            double score = Math.abs((candidate.pos().getY() + 0.5) - boundary.getY());
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
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
        Vec3d center = this.snapPositionToRail(linear.getX(), linear.getY(), linear.getZ());
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
        return new Vec3d(center.getX(), (ahead.getY() + behind.getY()) * 0.5, center.getZ());
    }

    @Unique
    private static Vec3d minecartspeedfeatures$lerp(Vec3d start, Vec3d end, double progress) {
        return new Vec3d(
                MathHelper.lerp(progress, start.getX(), end.getX()),
                MathHelper.lerp(progress, start.getY(), end.getY()),
                MathHelper.lerp(progress, start.getZ(), end.getZ())
        );
    }
}
