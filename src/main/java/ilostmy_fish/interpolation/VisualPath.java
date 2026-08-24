package ilostmy_fish.interpolation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.math.Vec3d;

/**
 * Immutable 3D polyline parameterized by horizontal travel distance.
 *
 * <p>The authoritative rail traversal consumes tick time from XZ displacement, so slope height
 * must not make an ascending segment consume more interpolation time than a flat segment with the
 * same horizontal span.</p>
 */
public final class VisualPath {
    private static final double EPSILON = 1.0E-7;

    private final Vec3d[] points;
    private final double[] cumulativeTravel;
    private final double totalTravel;

    private VisualPath(Vec3d[] points, double[] cumulativeTravel, double totalTravel) {
        this.points = points;
        this.cumulativeTravel = cumulativeTravel;
        this.totalTravel = totalTravel;
    }

    public static VisualPath create(List<Vec3d> input) {
        if (input == null || input.size() < 2) {
            return null;
        }

        ArrayList<Vec3d> deduplicated = new ArrayList<>(input.size());
        for (Vec3d point : input) {
            if (point == null) {
                continue;
            }
            if (deduplicated.isEmpty()
                    || spatialDistance(deduplicated.getLast(), point) > EPSILON) {
                deduplicated.add(point);
            }
        }
        if (deduplicated.size() < 2) {
            return null;
        }

        // Remove points that add no geometric information. A long straight or constant slope then
        // becomes start/end only, while bends and slope transitions retain their control points.
        ArrayList<Vec3d> simplified = new ArrayList<>(deduplicated.size());
        simplified.add(deduplicated.getFirst());
        for (int i = 1; i < deduplicated.size() - 1; i++) {
            Vec3d a = simplified.getLast();
            Vec3d b = deduplicated.get(i);
            Vec3d c = deduplicated.get(i + 1);
            if (!collinearForward(a, b, c)) {
                simplified.add(b);
            }
        }
        simplified.add(deduplicated.getLast());

        Vec3d[] points = simplified.toArray(new Vec3d[0]);
        double[] cumulativeTravel = new double[points.length];
        double totalTravel = 0.0;
        for (int i = 1; i < points.length; i++) {
            double segmentTravel = travelDistance(points[i - 1], points[i]);
            if (segmentTravel <= EPSILON) {
                // A spatially meaningful vertical-only segment cannot be represented by the
                // horizontal clock. Let the caller fall back to vanilla interpolation instead of
                // introducing an instantaneous vertical jump.
                return null;
            }
            totalTravel += segmentTravel;
            cumulativeTravel[i] = totalTravel;
        }
        return totalTravel <= EPSILON
                ? null
                : new VisualPath(points, cumulativeTravel, totalTravel);
    }

    public Vec3d sample(double progress) {
        if (progress <= 0.0) {
            return points[0];
        }
        if (progress >= 1.0) {
            return points[points.length - 1];
        }

        double targetTravel = totalTravel * progress;
        int low = 1;
        int high = cumulativeTravel.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (cumulativeTravel[mid] < targetTravel) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        int end = low;
        int start = end - 1;
        double segmentStart = cumulativeTravel[start];
        double segmentTravel = cumulativeTravel[end] - segmentStart;
        double local = segmentTravel <= EPSILON
                ? 1.0
                : (targetTravel - segmentStart) / segmentTravel;
        return lerp(points[start], points[end], local);
    }

    private static boolean collinearForward(Vec3d a, Vec3d b, Vec3d c) {
        double abx = b.getX() - a.getX();
        double aby = b.getY() - a.getY();
        double abz = b.getZ() - a.getZ();
        double bcx = c.getX() - b.getX();
        double bcy = c.getY() - b.getY();
        double bcz = c.getZ() - b.getZ();
        double ab = Math.sqrt(abx * abx + aby * aby + abz * abz);
        double bc = Math.sqrt(bcx * bcx + bcy * bcy + bcz * bcz);
        if (ab <= EPSILON || bc <= EPSILON) {
            return true;
        }

        double ux = abx / ab;
        double uy = aby / ab;
        double uz = abz / ab;
        double vx = bcx / bc;
        double vy = bcy / bc;
        double vz = bcz / bc;
        double dot = ux * vx + uy * vy + uz * vz;
        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;
        double crossSq = cx * cx + cy * cy + cz * cz;
        return dot > 0.999999 && crossSq < 1.0E-10;
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t
        );
    }

    private static double spatialDistance(Vec3d a, Vec3d b) {
        double x = b.getX() - a.getX();
        double y = b.getY() - a.getY();
        double z = b.getZ() - a.getZ();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double travelDistance(Vec3d a, Vec3d b) {
        double x = b.getX() - a.getX();
        double z = b.getZ() - a.getZ();
        return Math.hypot(x, z);
    }
}
