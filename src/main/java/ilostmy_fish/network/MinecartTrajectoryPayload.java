package ilostmy_fish.network;

import ilostmy_fish.MinecartSpeedFeatures;
import ilostmy_fish.trajectory.MinecartTrajectory;
import ilostmy_fish.trajectory.TrajectoryPoint;
import ilostmy_fish.trajectory.TrajectoryStreamPhase;
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
        MinecartTrajectory trajectory,
        TrajectoryStreamPhase phase
) implements CustomPayload {
    private static final int MAX_POINTS = 4098;
    private static final int PHASE_MASK = 0b0000_0011;
    private static final int HAS_ORIENTATION = 0b0000_0100;
    private static final int KNOWN_FLAGS = PHASE_MASK | HAS_ORIENTATION;

    public static final Id<MinecartTrajectoryPayload> ID = new Id<>(Identifier.of(
            MinecartSpeedFeatures.MOD_ID,
            "minecart_trajectory"
    ));
    public static final PacketCodec<RegistryByteBuf, MinecartTrajectoryPayload> CODEC =
            PacketCodec.ofStatic(MinecartTrajectoryPayload::write, MinecartTrajectoryPayload::read);

    public MinecartTrajectoryPayload {
        Objects.requireNonNull(trajectory, "trajectory");
        Objects.requireNonNull(phase, "phase");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, MinecartTrajectoryPayload payload) {
        MinecartTrajectory trajectory = payload.trajectory;
        buffer.writeVarInt(payload.entityId);
        buffer.writeVarLong(trajectory.serverTick());
        boolean hasOrientation = hasOrientation(trajectory.orientationHint());
        int flags = payload.phase.wireValue() | (hasOrientation ? HAS_ORIENTATION : 0);
        buffer.writeByte(flags);

        List<TrajectoryPoint> points = trajectory.points();
        buffer.writeVarInt(points.size());
        Vec3d startPosition = points.getFirst().position();
        writeVec3d(buffer, startPosition);
        for (int index = 1; index < points.size() - 1; index++) {
            TrajectoryPoint point = points.get(index);
            buffer.writeFloat((float)point.timeFraction());
            writeRelativeVec3f(buffer, startPosition, point.position());
        }
        writeVec3d(buffer, points.getLast().position());
        writeVec3d(buffer, trajectory.finalVelocity());
        if (hasOrientation) {
            writeVec3f(buffer, trajectory.orientationHint());
        }
    }

    private static MinecartTrajectoryPayload read(RegistryByteBuf buffer) {
        int entityId = buffer.readVarInt();
        long serverTick = buffer.readVarLong();
        int flags = buffer.readUnsignedByte();
        if ((flags & ~KNOWN_FLAGS) != 0) {
            throw new IllegalArgumentException("Unknown trajectory flags: " + flags);
        }
        TrajectoryStreamPhase phase = TrajectoryStreamPhase.fromWireValue(flags & PHASE_MASK);
        int pointCount = buffer.readVarInt();
        if (pointCount < 2 || pointCount > MAX_POINTS) {
            throw new IllegalArgumentException("Invalid trajectory point count: " + pointCount);
        }

        List<TrajectoryPoint> points = new ArrayList<>(pointCount);
        Vec3d startPosition = readVec3d(buffer);
        points.add(new TrajectoryPoint(0.0, startPosition));
        for (int index = 1; index < pointCount - 1; index++) {
            points.add(new TrajectoryPoint(
                    buffer.readFloat(),
                    readRelativeVec3f(buffer, startPosition)
            ));
        }
        points.add(new TrajectoryPoint(1.0, readVec3d(buffer)));
        Vec3d finalVelocity = readVec3d(buffer);
        Vec3d orientationHint = (flags & HAS_ORIENTATION) != 0
                ? readVec3f(buffer)
                : Vec3d.ZERO;
        return new MinecartTrajectoryPayload(
                entityId,
                new MinecartTrajectory(
                        serverTick,
                        points,
                        finalVelocity,
                        orientationHint
                ),
                phase
        );
    }

    private static boolean hasOrientation(Vec3d value) {
        return value.getX() != 0.0 || value.getY() != 0.0 || value.getZ() != 0.0;
    }

    private static void writeVec3d(RegistryByteBuf buffer, Vec3d value) {
        buffer.writeDouble(value.getX());
        buffer.writeDouble(value.getY());
        buffer.writeDouble(value.getZ());
    }

    private static Vec3d readVec3d(RegistryByteBuf buffer) {
        return new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static void writeRelativeVec3f(
            RegistryByteBuf buffer,
            Vec3d origin,
            Vec3d value
    ) {
        writeVec3f(buffer, value.subtract(origin));
    }

    private static Vec3d readRelativeVec3f(RegistryByteBuf buffer, Vec3d origin) {
        return origin.add(readVec3f(buffer));
    }

    private static void writeVec3f(RegistryByteBuf buffer, Vec3d value) {
        buffer.writeFloat((float)value.getX());
        buffer.writeFloat((float)value.getY());
        buffer.writeFloat((float)value.getZ());
    }

    private static Vec3d readVec3f(RegistryByteBuf buffer) {
        return new Vec3d(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
}
