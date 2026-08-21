package ilostmy_fish.interpolation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.math.Vec3d;

/**
 * Immutable, arc-length-parameterized polyline used only for visual interpolation.
 */
public final class VisualPath {
    private static final double EPSILON = 1.0E-7;

    private final Vec3d[] points;
    private final double[] cumulativeLength;
    private final double totalLength;

    private VisualPath(Vec3d[] points, double[] cumulativeLength, double totalLength) {
        this.points = points;
        this.cumulativeLength = cumulativeLength;
        this.totalLength = totalLength;
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
            if (deduplicated.isEmpty() || distance(deduplicated.getLast(), point) > EPSILON) {
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
        double[] cumulative = new double[points.length];
        double total = 0.0;
        for (int i = 1; i < points.length; i++) {
            total += distance(points[i - 1], points[i]);
            cumulative[i] = total;
        }
        return total <= EPSILON ? null : new VisualPath(points, cumulative, total);
    }

    public Vec3d sample(double progress) {
        if (progress <= 0.0) {
            return points[0];
        }
        if (progress >= 1.0) {
            return points[points.length - 1];
        }

        double targetDistance = totalLength * progress;
        int low = 1;
        int high = cumulativeLength.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (cumulativeLength[mid] < targetDistance) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        int end = low;
        int start = end - 1;
        double segmentStart = cumulativeLength[start];
        double segmentLength = cumulativeLength[end] - segmentStart;
        double local = segmentLength <= EPSILON ? 1.0 : (targetDistance - segmentStart) / segmentLength;
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

    private static double distance(Vec3d a, Vec3d b) {
        double x = b.getX() - a.getX();
        double y = b.getY() - a.getY();
        double z = b.getZ() - a.getZ();
        return Math.sqrt(x * x + y * y + z * z);
    }
}
