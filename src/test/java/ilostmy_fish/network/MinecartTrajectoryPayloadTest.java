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
                                new TrajectoryPoint(0.2, new Vec3d(2.123456, 2.5, 3.0)),
                                new TrajectoryPoint(1.0, new Vec3d(5.123456, 4.234567, 3.345678))
                        ),
                        new Vec3d(1.234567, 0.54321, -0.234567),
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
                double timeTolerance = index == 0
                                || index == expected.trajectory().points().size() - 1
                        ? TOLERANCE
                        : FLOAT_TOLERANCE;
                double positionTolerance = index == 0 ? TOLERANCE : FLOAT_TOLERANCE;
                assertEquals(
                        expectedPoint.timeFraction(),
                        actualPoint.timeFraction(),
                        timeTolerance
                );
                assertVec3d(
                        expectedPoint.position(),
                        actualPoint.position(),
                        positionTolerance
                );
            }
            assertVec3d(
                    expected.trajectory().finalVelocity(),
                    actual.trajectory().finalVelocity(),
                    FLOAT_TOLERANCE
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
    void relativeEndpointPreservesLargeWorldCoordinatePrecision() {
        Vec3d start = new Vec3d(29_999_999.123456, 200.987654, -29_999_999.234567);
        Vec3d end = start.add(0.123456, -0.234567, 0.345678);
        MinecartTrajectoryPayload payload = new MinecartTrajectoryPayload(
                73,
                new MinecartTrajectory(
                        123456L,
                        List.of(
                                new TrajectoryPoint(0.0, start),
                                new TrajectoryPoint(1.0, end)
                        ),
                        new Vec3d(0.123456, -0.234567, 0.345678),
                        Vec3d.ZERO
                ),
                TrajectoryStreamPhase.CONTINUE
        );
        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(),
                DynamicRegistryManager.EMPTY
        );

        try {
            MinecartTrajectoryPayload.CODEC.encode(buffer, payload);
            MinecartTrajectoryPayload actual = MinecartTrajectoryPayload.CODEC.decode(buffer);

            assertVec3d(start, actual.trajectory().points().getFirst().position(), TOLERANCE);
            assertVec3d(end, actual.trajectory().points().getLast().position(), FLOAT_TOLERANCE);
        } finally {
            buffer.release();
        }
    }

    @Test
    void twoPointTrajectoryWithoutOrientationUsesFiftyTwoBytes() {
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

            assertEquals(52, buffer.readableBytes());
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
