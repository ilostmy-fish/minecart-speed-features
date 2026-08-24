package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerTrajectoryBuilderTest {
    private static final double TOLERANCE = 1.0E-12;

    @Test
    void offRailMotionPublishesAuthoritativeEndpoints() {
        ServerTrajectoryBuilder builder = new ServerTrajectoryBuilder(10L, Vec3d.ZERO);

        MinecartTrajectory trajectory = builder.finish(
                new Vec3d(1.0, 0.0, 0.0),
                new Vec3d(1.0, 0.0, 0.0)
        );

        assertEquals(2, trajectory.points().size());
        assertEquals(0.5, trajectory.sample(0.5).position().getX(), TOLERANCE);
    }

    @Test
    void constantVelocityStraightRunCollapsesToEndpoints() {
        ServerTrajectoryBuilder builder = builder(11L);
        builder.record(0.25, new Vec3d(0.5, 0.0, 0.0));
        builder.record(0.5, new Vec3d(1.0, 0.0, 0.0));

        MinecartTrajectory trajectory = builder.finish(
                new Vec3d(2.0, 0.0, 0.0),
                new Vec3d(2.0, 0.0, 0.0)
        );

        assertNotNull(trajectory);
        assertEquals(2, trajectory.points().size());
    }

    @Test
    void velocityChangeOnStraightTrackRetainsItsBoundaryTime() {
        ServerTrajectoryBuilder builder = builder(12L);
        builder.record(0.25, new Vec3d(1.0, 0.0, 0.0));

        MinecartTrajectory trajectory = builder.finish(
                new Vec3d(2.0, 0.0, 0.0),
                new Vec3d(1.0, 0.0, 0.0)
        );

        assertNotNull(trajectory);
        assertEquals(3, trajectory.points().size());
        assertEquals(0.25, trajectory.points().get(1).timeFraction(), TOLERANCE);
    }

    @Test
    void slopeTransitionRetainsItsControlPoint() {
        ServerTrajectoryBuilder builder = builder(13L);
        builder.record(0.5, new Vec3d(1.0, 0.0, 0.0));

        MinecartTrajectory trajectory = builder.finish(
                new Vec3d(2.0, 1.0, 0.0),
                new Vec3d(1.0, 1.0, 0.0)
        );

        assertNotNull(trajectory);
        assertEquals(3, trajectory.points().size());
    }

    @Test
    void blockedRemainderStaysAtTheCollisionPosition() {
        ServerTrajectoryBuilder builder = builder(14L);
        Vec3d collision = new Vec3d(1.0, 0.0, 0.0);
        builder.record(0.25, collision);

        MinecartTrajectory trajectory = builder.finish(collision, Vec3d.ZERO);

        assertNotNull(trajectory);
        assertEquals(3, trajectory.points().size());
        assertEquals(collision.getX(), trajectory.sample(0.75).position().getX(), TOLERANCE);
    }

    @Test
    void preservesTheServerRailDirectionForAnIdleTick() {
        Vec3d northSouth = new Vec3d(0.0, 0.0, 0.6);
        ServerTrajectoryBuilder builder = new ServerTrajectoryBuilder(
                15L,
                Vec3d.ZERO,
                northSouth
        );

        MinecartTrajectory trajectory = builder.finish(Vec3d.ZERO, Vec3d.ZERO);

        assertEquals(northSouth, trajectory.orientationHint());
        assertEquals(northSouth, trajectory.sample(0.5).tangent());
    }

    private static ServerTrajectoryBuilder builder(long serverTick) {
        return new ServerTrajectoryBuilder(serverTick, Vec3d.ZERO);
    }
}
