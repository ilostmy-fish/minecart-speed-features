package ilostmy_fish.network;

import ilostmy_fish.trajectory.MinecartTrajectory;
import ilostmy_fish.trajectory.TrajectoryPoint;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecartTrajectoryPayloadTest {
    private static final double TOLERANCE = 1.0E-12;

    @Test
    void codecRoundTripsTimedPointsVelocityAndOrientation() {
        MinecartTrajectoryPayload expected = new MinecartTrajectoryPayload(
                73,
                new MinecartTrajectory(
                        123456L,
                        List.of(
                                new TrajectoryPoint(0.0, new Vec3d(1.0, 2.0, 3.0)),
                                new TrajectoryPoint(0.2, new Vec3d(2.0, 2.5, 3.0)),
                                new TrajectoryPoint(1.0, new Vec3d(5.0, 4.0, 3.0))
                        ),
                        new Vec3d(1.25, 0.5, -0.25),
                        new Vec3d(0.0, 0.0, 0.6)
                )
        );
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(),
                DynamicRegistryManager.EMPTY
        );

        try {
            MinecartTrajectoryPayload.CODEC.encode(buffer, expected);
            MinecartTrajectoryPayload actual = MinecartTrajectoryPayload.CODEC.decode(buffer);

            assertEquals(expected.entityId(), actual.entityId());
            assertEquals(expected.trajectory().serverTick(), actual.trajectory().serverTick());
            assertEquals(expected.trajectory().points().size(), actual.trajectory().points().size());
            for (int index = 0; index < expected.trajectory().points().size(); index++) {
                TrajectoryPoint expectedPoint = expected.trajectory().points().get(index);
                TrajectoryPoint actualPoint = actual.trajectory().points().get(index);
                assertEquals(expectedPoint.timeFraction(), actualPoint.timeFraction(), TOLERANCE);
                assertVec3d(expectedPoint.position(), actualPoint.position());
            }
            assertVec3d(
                    expected.trajectory().finalVelocity(),
                    actual.trajectory().finalVelocity()
            );
            assertVec3d(
                    expected.trajectory().orientationHint(),
                    actual.trajectory().orientationHint()
            );
        } finally {
            buffer.release();
        }
    }

    private static void assertVec3d(Vec3d expected, Vec3d actual) {
        assertEquals(expected.getX(), actual.getX(), TOLERANCE);
        assertEquals(expected.getY(), actual.getY(), TOLERANCE);
        assertEquals(expected.getZ(), actual.getZ(), TOLERANCE);
    }
}
