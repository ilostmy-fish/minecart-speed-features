package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrajectoryTransformsTest {
    private static final double TOLERANCE = 1.0E-12;

    @Test
    void riderOffsetUsesTheRidersOwnInterpolationBaseline() {
        Vec3d sampledCart = new Vec3d(2.0, 0.0, 0.0);
        Vec3d logicalCart = new Vec3d(4.0, 0.0, 0.0);
        Vec3d logicalRider = new Vec3d(4.0, 1.0, 0.0);

        Vec3d correction = TrajectoryTransforms.mountedRenderOffset(
                sampledCart,
                logicalCart,
                logicalRider,
                logicalRider
        );

        assertVec3d(new Vec3d(-2.0, 0.0, 0.0), correction);
        assertVec3d(
                new Vec3d(2.0, 1.0, 0.0),
                logicalRider.add(correction)
        );
    }

    @Test
    void cullingOffsetStartsAtTheLogicalBoundingBox() {
        Vec3d sampledCart = new Vec3d(2.0, 0.5, 0.0);
        Vec3d logicalCart = new Vec3d(5.0, 1.0, 0.0);

        Vec3d correction = TrajectoryTransforms.visibilityOffset(
                sampledCart,
                logicalCart
        );

        assertVec3d(new Vec3d(-3.0, -0.5, 0.0), correction);
        assertVec3d(sampledCart, logicalCart.add(correction));
    }

    private static void assertVec3d(Vec3d expected, Vec3d actual) {
        assertEquals(expected.getX(), actual.getX(), TOLERANCE);
        assertEquals(expected.getY(), actual.getY(), TOLERANCE);
        assertEquals(expected.getZ(), actual.getZ(), TOLERANCE);
    }
}
