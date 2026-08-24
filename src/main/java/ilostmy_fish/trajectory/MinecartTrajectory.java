package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Objects;

/** An immutable, time-parameterized trajectory produced by one authoritative server tick. */
public record MinecartTrajectory(
        long serverTick,
        List<TrajectoryPoint> points,
        Vec3d finalVelocity,
        Vec3d orientationHint
) {
    private static final double TIME_EPSILON = 1.0E-7;
    private static final double TANGENT_TIME_WINDOW = 0.05;
    private static final double MOVEMENT_EPSILON_SQUARED = 1.0E-18;

    public MinecartTrajectory {
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        Objects.requireNonNull(finalVelocity, "finalVelocity");
        Objects.requireNonNull(orientationHint, "orientationHint");
        if (points.size() < 2) {
            throw new IllegalArgumentException("A trajectory requires start and end points");
        }
        if (Math.abs(points.getFirst().timeFraction()) > TIME_EPSILON
                || Math.abs(points.getLast().timeFraction() - 1.0) > TIME_EPSILON) {
            throw new IllegalArgumentException("Trajectory endpoints must be at time 0 and 1");
        }

        double previousTime = -1.0;
        for (TrajectoryPoint point : points) {
            double time = point.timeFraction();
            if (!Double.isFinite(time) || time < 0.0 || time > 1.0
                    || time <= previousTime) {
                throw new IllegalArgumentException("Trajectory times must increase from 0 to 1");
            }
            if (isNonFinite(point.position())) {
                throw new IllegalArgumentException("Trajectory positions must be finite");
            }
            previousTime = time;
        }
        if (isNonFinite(finalVelocity)) {
            throw new IllegalArgumentException("Final velocity must be finite");
        }
        if (isNonFinite(orientationHint)) {
            throw new IllegalArgumentException("Orientation hint must be finite");
        }
    }

    public MinecartTrajectory(
            long serverTick,
            List<TrajectoryPoint> points,
            Vec3d finalVelocity
    ) {
        this(serverTick, points, finalVelocity, Vec3d.ZERO);
    }

    public TrajectorySample sample(double timeFraction) {
        double time = Double.isFinite(timeFraction)
                ? Math.clamp(timeFraction, 0.0, 1.0)
                : 0.0;
        Vec3d position = this.samplePosition(time);
        Vec3d before = this.samplePosition(Math.max(0.0, time - TANGENT_TIME_WINDOW));
        Vec3d after = this.samplePosition(Math.min(1.0, time + TANGENT_TIME_WINDOW));
        Vec3d tangent = after.subtract(before);
        if (tangent.lengthSquared() <= MOVEMENT_EPSILON_SQUARED) {
            tangent = this.finalVelocity;
        }
        if (tangent.lengthSquared() <= MOVEMENT_EPSILON_SQUARED) {
            tangent = this.orientationHint;
        }
        return new TrajectorySample(position, tangent);
    }

    private Vec3d samplePosition(double time) {
        if (time <= 0.0) {
            return this.points.getFirst().position();
        }
        if (time >= 1.0) {
            return this.points.getLast().position();
        }

        int low = 1;
        int high = this.points.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (this.points.get(middle).timeFraction() < time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        TrajectoryPoint start = this.points.get(low - 1);
        TrajectoryPoint end = this.points.get(low);
        double duration = end.timeFraction() - start.timeFraction();
        double localTime = (time - start.timeFraction()) / duration;
        return lerp(start.position(), end.position(), localTime);
    }

    private static Vec3d lerp(Vec3d start, Vec3d end, double progress) {
        return new Vec3d(
                start.getX() + (end.getX() - start.getX()) * progress,
                start.getY() + (end.getY() - start.getY()) * progress,
                start.getZ() + (end.getZ() - start.getZ()) * progress
        );
    }

    private static boolean isNonFinite(Vec3d value) {
        return !Double.isFinite(value.getX())
                || !Double.isFinite(value.getY())
                || !Double.isFinite(value.getZ());
    }
}
