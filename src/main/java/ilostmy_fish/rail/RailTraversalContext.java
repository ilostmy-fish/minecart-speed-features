package ilostmy_fish.rail;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Mutable state for one outer rail-by-rail traversal. */
public final class RailTraversalContext {
    private final RailMovementBudget movementBudget = new RailMovementBudget();

    private RailRef rail;
    private Vec3d movementStart;
    private RailMovementBudget.MovementLimit movementLimit;
    private RailMovementBudget.MovementCompletion movementCompletion;
    private RailEndpoint exitEndpoint;
    private Vec3d positionAfterMove;

    public void beginSegment(RailRef rail) {
        this.rail = rail;
        this.movementStart = null;
        this.movementLimit = null;
        this.movementCompletion = null;
        this.exitEndpoint = null;
        this.positionAfterMove = null;
    }

    public Vec3d limitMovement(Vec3d start, Vec3d requestedMovement) {
        this.movementStart = start;
        this.movementLimit = this.movementBudget.limit(
                start.getX(),
                start.getZ(),
                this.rail.pos().getX(),
                this.rail.pos().getZ(),
                requestedMovement.getX(),
                requestedMovement.getZ()
        );
        if (this.movementLimit.requestedDistance() > 0.0) {
            this.exitEndpoint = RailGeometry.exitEndpoint(
                    this.rail.shape(),
                    requestedMovement.getX(),
                    requestedMovement.getZ()
            );
        }

        return new Vec3d(
                requestedMovement.getX() * this.movementLimit.scale(),
                requestedMovement.getY() * this.movementLimit.scale(),
                requestedMovement.getZ() * this.movementLimit.scale()
        );
    }

    public void completeMovement(Vec3d positionAfterMove) {
        if (this.movementStart == null || this.movementLimit == null) {
            return;
        }

        this.positionAfterMove = positionAfterMove;
        this.movementCompletion = this.movementBudget.complete(
                this.movementLimit,
                positionAfterMove.getX() - this.movementStart.getX(),
                positionAfterMove.getZ() - this.movementStart.getZ()
        );
    }

    public boolean interceptedMovement() {
        return this.movementLimit != null;
    }

    public boolean reachedBoundary() {
        return this.movementCompletion != null && this.movementCompletion.reachedBoundary();
    }

    public boolean wasBlocked() {
        return this.movementCompletion != null && this.movementCompletion.blocked();
    }

    public boolean hasTimeRemaining() {
        return this.movementBudget.hasTimeRemaining();
    }

    public double remainingTime() {
        return this.movementBudget.remainingTime();
    }

    @Nullable
    public RailEndpoint exitEndpoint() {
        return this.exitEndpoint;
    }

    /** Position produced by the bounded Entity.move call, before vanilla's final rail re-snap. */
    @Nullable
    public Vec3d positionAfterMove() {
        return this.positionAfterMove;
    }
}
