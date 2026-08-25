package ilostmy_fish.network;

import ilostmy_fish.trajectory.MinecartTrajectory;
import ilostmy_fish.trajectory.TrajectoryPoint;
import ilostmy_fish.trajectory.TrajectoryStreamPhase;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecartTrajectoryPayloadTest {
    private static final double TOLERANCE = 1.0E-12;
    private static final double FLOAT_TOLERANCE = 1.0E-6;

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
                ),
                TrajectoryStreamPhase.START
        );
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(),
                DynamicRegistryManager.EMPTY
        );

        try {
            MinecartTrajectoryPayload.CODEC.encode(buffer, expected);
            MinecartTrajectoryPayload actual = MinecartTrajectoryPayload.CODEC.decode(buffer);

            assertEquals(expected.entityId(), actual.entityId());
            assertEquals(expected.phase(), actual.phase());
            assertEquals(expected.trajectory().serverTick(), actual.trajectory().serverTick());
            assertEquals(expected.trajectory().points().size(), actual.trajectory().points().size());
            for (int index = 0; index < expected.trajectory().points().size(); index++) {
                TrajectoryPoint expectedPoint = expected.trajectory().points().get(index);
                TrajectoryPoint actualPoint = actual.trajectory().points().get(index);
                double tolerance = index == 0
                                || index == expected.trajectory().points().size() - 1
                        ? TOLERANCE
                        : FLOAT_TOLERANCE;
                assertEquals(
                        expectedPoint.timeFraction(),
                        actualPoint.timeFraction(),
                        tolerance
                );
                assertVec3d(expectedPoint.position(), actualPoint.position(), tolerance);
            }
            assertVec3d(
                    expected.trajectory().finalVelocity(),
                    actual.trajectory().finalVelocity(),
                    TOLERANCE
            );
            assertVec3d(
                    expected.trajectory().orientationHint(),
                    actual.trajectory().orientationHint(),
                    FLOAT_TOLERANCE
            );
        } finally {
            buffer.release();
        }
    }

    @Test
    void twoPointTrajectoryWithoutOrientationUsesSeventySixBytes() {
        MinecartTrajectoryPayload payload = new MinecartTrajectoryPayload(
                1,
                new MinecartTrajectory(
                        1L,
                        List.of(
                                new TrajectoryPoint(0.0, Vec3d.ZERO),
                                new TrajectoryPoint(1.0, new Vec3d(1.0, 0.0, 0.0))
                        ),
                        Vec3d.ZERO,
                        Vec3d.ZERO
                ),
                TrajectoryStreamPhase.END
        );
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(),
                DynamicRegistryManager.EMPTY
        );

        try {
            MinecartTrajectoryPayload.CODEC.encode(buffer, payload);

            assertEquals(76, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static void assertVec3d(Vec3d expected, Vec3d actual, double tolerance) {
        assertEquals(expected.getX(), actual.getX(), tolerance);
        assertEquals(expected.getY(), actual.getY(), tolerance);
        assertEquals(expected.getZ(), actual.getZ(), tolerance);
    }
}
