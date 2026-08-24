package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Collects authoritative tick endpoints and time-aware rail-boundary samples. */
public final class ServerTrajectoryBuilder {
    private static final double TIME_EPSILON = 1.0E-9;
    private static final double POSITION_TOLERANCE_SQUARED = 1.0E-10;

    private final long serverTick;
    private final Vec3d orientationHint;
    private final List<TrajectoryPoint> points = new ArrayList<>();

    public ServerTrajectoryBuilder(long serverTick, Vec3d startPosition) {
        this(serverTick, startPosition, Vec3d.ZERO);
    }

    public ServerTrajectoryBuilder(
            long serverTick,
            Vec3d startPosition,
            Vec3d orientationHint
    ) {
        this.serverTick = serverTick;
        this.orientationHint = Objects.requireNonNull(orientationHint);
        this.points.add(new TrajectoryPoint(0.0, Objects.requireNonNull(startPosition)));
    }

    public void record(double timeFraction, Vec3d position) {
        if (!Double.isFinite(timeFraction) || timeFraction <= TIME_EPSILON
                || timeFraction >= 1.0 - TIME_EPSILON) {
            return;
        }

        TrajectoryPoint previous = this.points.getLast();
        if (timeFraction <= previous.timeFraction() + TIME_EPSILON) {
            return;
        }
        this.points.add(new TrajectoryPoint(timeFraction, Objects.requireNonNull(position)));
    }

    public MinecartTrajectory finish(Vec3d endPosition, Vec3d finalVelocity) {
        this.points.add(new TrajectoryPoint(1.0, Objects.requireNonNull(endPosition)));
        return new MinecartTrajectory(
                this.serverTick,
                simplify(this.points),
                Objects.requireNonNull(finalVelocity),
                this.orientationHint
        );
    }

    private static List<TrajectoryPoint> simplify(List<TrajectoryPoint> input) {
        ArrayList<TrajectoryPoint> result = new ArrayList<>(input.size());
        for (TrajectoryPoint point : input) {
            result.add(point);
            while (result.size() >= 3) {
                int endIndex = result.size() - 1;
                TrajectoryPoint start = result.get(endIndex - 2);
                TrajectoryPoint middle = result.get(endIndex - 1);
                TrajectoryPoint end = result.get(endIndex);
                if (!isTimeLinear(start, middle, end)) {
                    break;
                }
                result.remove(endIndex - 1);
            }
        }
        return result;
    }

    private static boolean isTimeLinear(
            TrajectoryPoint start,
            TrajectoryPoint middle,
            TrajectoryPoint end
    ) {
        double duration = end.timeFraction() - start.timeFraction();
        if (duration <= TIME_EPSILON) {
            return false;
        }
        double progress = (middle.timeFraction() - start.timeFraction()) / duration;
        Vec3d expected = new Vec3d(
                start.position().getX()
                        + (end.position().getX() - start.position().getX()) * progress,
                start.position().getY()
                        + (end.position().getY() - start.position().getY()) * progress,
                start.position().getZ()
                        + (end.position().getZ() - start.position().getZ()) * progress
        );
        return expected.squaredDistanceTo(middle.position()) <= POSITION_TOLERANCE_SQUARED;
    }
}
