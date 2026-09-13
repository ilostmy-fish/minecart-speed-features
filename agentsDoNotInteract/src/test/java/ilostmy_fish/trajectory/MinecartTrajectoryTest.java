package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartTrajectoryTest {
    private static final double TOLERANCE = 1.0E-12;

    @Test
    void samplesPositionFromRecordedTimeFractions() {
        MinecartTrajectory trajectory = new MinecartTrajectory(
                42L,
                List.of(
                        point(0.0, 0.0, 0.0, 0.0),
                        point(0.25, 1.0, 0.0, 0.0),
                        point(1.0, 2.0, 1.0, 0.0)
                ),
                new Vec3d(1.0, 1.0, 0.0)
        );

        assertPosition(new Vec3d(0.5, 0.0, 0.0), trajectory.sample(0.125).position());
        assertPosition(new Vec3d(1.5, 0.5, 0.0), trajectory.sample(0.625).position());
    }

    @Test
    void tangentUsesTheSameTimedPathAroundATransition() {
        MinecartTrajectory trajectory = new MinecartTrajectory(
                7L,
                List.of(
                        point(0.0, 0.0, 0.0, 0.0),
                        point(0.5, 1.0, 0.0, 0.0),
                        point(1.0, 1.0, 1.0, 1.0)
                ),
                Vec3d.ZERO
        );

        Vec3d tangent = trajectory.sample(0.5).tangent();

        assertTrue(tangent.getX() > 0.0);
        assertTrue(tangent.getY() > 0.0);
        assertTrue(tangent.getZ() > 0.0);
    }

    @Test
    void finalVelocitySuppliesDirectionForAStationaryTrajectory() {
        Vec3d velocity = new Vec3d(0.4, 0.2, 0.0);
        MinecartTrajectory trajectory = new MinecartTrajectory(
                9L,
                List.of(
                        point(0.0, 3.0, 4.0, 5.0),
                        point(1.0, 3.0, 4.0, 5.0)
                ),
                velocity
        );

        assertPosition(velocity, trajectory.sample(0.5).tangent());
    }

    @Test
    void serverOrientationSuppliesDirectionForAnIdleRailCart() {
        Vec3d railTangent = new Vec3d(0.0, 0.0, 0.6);
        MinecartTrajectory trajectory = new MinecartTrajectory(
                10L,
                List.of(
                        point(0.0, 3.0, 4.0, 5.0),
                        point(1.0, 3.0, 4.0, 5.0)
                ),
                Vec3d.ZERO,
                railTangent
        );

        assertPosition(railTangent, trajectory.sample(0.5).tangent());
    }

    private static TrajectoryPoint point(double time, double x, double y, double z) {
        return new TrajectoryPoint(time, new Vec3d(x, y, z));
    }

    private static void assertPosition(Vec3d expected, Vec3d actual) {
        assertEquals(expected.getX(), actual.getX(), TOLERANCE);
        assertEquals(expected.getY(), actual.getY(), TOLERANCE);
        assertEquals(expected.getZ(), actual.getZ(), TOLERANCE);
    }
}
