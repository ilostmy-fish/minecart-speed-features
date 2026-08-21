package ilostmy_fish.interpolation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_243;

/** Immutable, arc-length-parameterized polyline used only for visual interpolation. */
public final class VisualPath {
    private static final double EPSILON = 1.0E-7;

    private final class_243[] points;
    private final double[] cumulativeLength;
    private final double totalLength;

    private VisualPath(class_243[] points, double[] cumulativeLength, double totalLength) {
        this.points = points;
        this.cumulativeLength = cumulativeLength;
        this.totalLength = totalLength;
    }

    public static VisualPath create(List<class_243> input) {
        if (input == null || input.size() < 2) {
            return null;
        }

        ArrayList<class_243> deduplicated = new ArrayList<>(input.size());
        for (class_243 point : input) {
            if (point == null) {
                continue;
            }
            if (deduplicated.isEmpty() || distance(deduplicated.get(deduplicated.size() - 1), point) > EPSILON) {
                deduplicated.add(point);
            }
        }
        if (deduplicated.size() < 2) {
            return null;
        }

        // Remove points that add no geometric information. A long straight or constant slope then
        // becomes start/end only, while bends and slope transitions retain their control points.
        ArrayList<class_243> simplified = new ArrayList<>(deduplicated.size());
        simplified.add(deduplicated.get(0));
        for (int i = 1; i < deduplicated.size() - 1; i++) {
            class_243 a = simplified.get(simplified.size() - 1);
            class_243 b = deduplicated.get(i);
            class_243 c = deduplicated.get(i + 1);
            if (!collinearForward(a, b, c)) {
                simplified.add(b);
            }
        }
        simplified.add(deduplicated.get(deduplicated.size() - 1));

        class_243[] points = simplified.toArray(new class_243[0]);
        double[] cumulative = new double[points.length];
        double total = 0.0;
        for (int i = 1; i < points.length; i++) {
            total += distance(points[i - 1], points[i]);
            cumulative[i] = total;
        }
        return total <= EPSILON ? null : new VisualPath(points, cumulative, total);
    }

    public class_243 sample(double progress) {
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

    public int pointCount() {
        return points.length;
    }

    private static boolean collinearForward(class_243 a, class_243 b, class_243 c) {
        double abx = b.method_10216() - a.method_10216();
        double aby = b.method_10214() - a.method_10214();
        double abz = b.method_10215() - a.method_10215();
        double bcx = c.method_10216() - b.method_10216();
        double bcy = c.method_10214() - b.method_10214();
        double bcz = c.method_10215() - b.method_10215();
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

    private static class_243 lerp(class_243 a, class_243 b, double t) {
        return new class_243(
                a.method_10216() + (b.method_10216() - a.method_10216()) * t,
                a.method_10214() + (b.method_10214() - a.method_10214()) * t,
                a.method_10215() + (b.method_10215() - a.method_10215()) * t
        );
    }

    private static double distance(class_243 a, class_243 b) {
        double x = b.method_10216() - a.method_10216();
        double y = b.method_10214() - a.method_10214();
        double z = b.method_10215() - a.method_10215();
        return Math.sqrt(x * x + y * y + z * z);
    }
}
