package ilostmy_fish.network;

import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.trajectory.MinecartTrajectory;
import ilostmy_fish.trajectory.TrajectoryPoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Server-authored minecart motion for one tick. */
public record MinecartTrajectoryPayload(
        int entityId,
        MinecartTrajectory trajectory
) implements CustomPayload {
    private static final int MAX_POINTS = 4098;

    public static final Id<MinecartTrajectoryPayload> ID = new Id<>(Identifier.of(
            MinecartSpeedFeatures.MOD_ID,
            "minecart_trajectory"
    ));
    public static final PacketCodec<RegistryByteBuf, MinecartTrajectoryPayload> CODEC =
            PacketCodec.ofStatic(MinecartTrajectoryPayload::write, MinecartTrajectoryPayload::read);

    public MinecartTrajectoryPayload {
        Objects.requireNonNull(trajectory, "trajectory");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, MinecartTrajectoryPayload payload) {
        MinecartTrajectory trajectory = payload.trajectory;
        buffer.writeVarInt(payload.entityId);
        buffer.writeLong(trajectory.serverTick());
        buffer.writeVarInt(trajectory.points().size());
        for (TrajectoryPoint point : trajectory.points()) {
            buffer.writeDouble(point.timeFraction());
            writeVec3d(buffer, point.position());
        }
        writeVec3d(buffer, trajectory.finalVelocity());
        writeVec3d(buffer, trajectory.orientationHint());
    }

    private static MinecartTrajectoryPayload read(RegistryByteBuf buffer) {
        int entityId = buffer.readVarInt();
        long serverTick = buffer.readLong();
        int pointCount = buffer.readVarInt();
        if (pointCount < 2 || pointCount > MAX_POINTS) {
            throw new IllegalArgumentException("Invalid trajectory point count: " + pointCount);
        }

        List<TrajectoryPoint> points = new ArrayList<>(pointCount);
        for (int index = 0; index < pointCount; index++) {
            points.add(new TrajectoryPoint(buffer.readDouble(), readVec3d(buffer)));
        }
        Vec3d finalVelocity = readVec3d(buffer);
        Vec3d orientationHint = readVec3d(buffer);
        return new MinecartTrajectoryPayload(
                entityId,
                new MinecartTrajectory(
                        serverTick,
                        points,
                        finalVelocity,
                        orientationHint
                )
        );
    }

    private static void writeVec3d(RegistryByteBuf buffer, Vec3d value) {
        buffer.writeDouble(value.getX());
        buffer.writeDouble(value.getY());
        buffer.writeDouble(value.getZ());
    }

    private static Vec3d readVec3d(RegistryByteBuf buffer) {
        return new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
