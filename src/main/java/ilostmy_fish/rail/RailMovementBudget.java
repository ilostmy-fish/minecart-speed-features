package ilostmy_fish.rail;

/**
 * Owns the single unit of simulation time available to one minecart tick.
 *
 * <p>Each normal {@code moveOnRail} call proposes a full-tick displacement. This budget scales
 * that proposal by the unconsumed tick fraction and clips it at the current block face.
 * Consuming time instead of adding displacement budgets means a velocity change on one rail is
 * immediately used for the remaining fraction of the tick without granting another whole tick.</p>
 */
public final class RailMovementBudget {
    public static final double TIME_EPSILON = 1.0E-9;
    private static final double MOVEMENT_EPSILON = 1.0E-12;
    private static final double COLLISION_EPSILON = 1.0E-7;

    private double remainingTime = 1.0;

    public double remainingTime() {
        return this.remainingTime;
    }

    public boolean hasTimeRemaining() {
        return this.remainingTime > TIME_EPSILON;
    }

    public MovementLimit limit(
            double startX,
            double startZ,
            int railX,
            int railZ,
            double requestedX,
            double requestedZ
    ) {
        double requestedDistance = Math.hypot(requestedX, requestedZ);
        if (requestedDistance <= MOVEMENT_EPSILON || !this.hasTimeRemaining()) {
            return new MovementLimit(requestedX, requestedZ, 0.0, requestedDistance, false);
        }

        double boundaryScale = Double.POSITIVE_INFINITY;
        if (requestedX > MOVEMENT_EPSILON) {
            boundaryScale = positiveMinimum(
                    boundaryScale,
                    (railX + 1.0 - startX) / requestedX
            );
        } else if (requestedX < -MOVEMENT_EPSILON) {
            boundaryScale = positiveMinimum(
                    boundaryScale,
                    (railX - startX) / requestedX
            );
        }

        if (requestedZ > MOVEMENT_EPSILON) {
            boundaryScale = positiveMinimum(
                    boundaryScale,
                    (railZ + 1.0 - startZ) / requestedZ
            );
        } else if (requestedZ < -MOVEMENT_EPSILON) {
            boundaryScale = positiveMinimum(
                    boundaryScale,
                    (railZ - startZ) / requestedZ
            );
        }

        double scale = Math.min(this.remainingTime, boundaryScale);
        scale = Math.max(0.0, scale);
        boolean reachesBoundary = Double.isFinite(boundaryScale)
                && boundaryScale <= this.remainingTime + TIME_EPSILON;
        return new MovementLimit(requestedX, requestedZ, scale, requestedDistance, reachesBoundary);
    }

    public MovementCompletion complete(MovementLimit limit, double actualX, double actualZ) {
        if (limit.requestedDistance <= MOVEMENT_EPSILON || limit.scale <= TIME_EPSILON) {
            return new MovementCompletion(false, false, 0.0);
        }

        double unitX = limit.requestedX / limit.requestedDistance;
        double unitZ = limit.requestedZ / limit.requestedDistance;
        double actualForwardDistance = actualX * unitX + actualZ * unitZ;
        double limitedDistance = limit.requestedDistance * limit.scale;
        boolean blocked = actualForwardDistance + COLLISION_EPSILON < limitedDistance;

        double consumedTime = Math.clamp(
                actualForwardDistance / limit.requestedDistance,
                0.0,
                limit.scale
        );
        this.remainingTime = Math.max(0.0, this.remainingTime - consumedTime);
        return new MovementCompletion(limit.reachesBoundary && !blocked, blocked, consumedTime);
    }

    private static double positiveMinimum(double current, double candidate) {
        if (candidate < -TIME_EPSILON) {
            return current;
        }
        return Math.min(current, Math.max(0.0, candidate));
    }

    public record MovementLimit(
            double requestedX,
            double requestedZ,
            double scale,
            double requestedDistance,
            boolean reachesBoundary
    ) {
        public double scaleX() {
            return this.requestedX * this.scale;
        }

        public double scaleZ() {
            return this.requestedZ * this.scale;
        }
    }

    public record MovementCompletion(boolean reachedBoundary, boolean blocked, double consumedTime) {
    }
}
