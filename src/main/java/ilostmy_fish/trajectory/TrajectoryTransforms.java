package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;

/** Coordinate-space conversions shared by trajectory rendering and visibility checks. */
public final class TrajectoryTransforms {
    private TrajectoryTransforms() {
    }

    public static Vec3d mountedRenderOffset(
            Vec3d sampledMinecartPosition,
            Vec3d logicalMinecartPosition,
            Vec3d logicalEntityPosition,
            Vec3d interpolatedEntityPosition
    ) {
        Vec3d attachmentOffset = logicalEntityPosition.subtract(logicalMinecartPosition);
        Vec3d sampledEntityPosition = sampledMinecartPosition.add(attachmentOffset);
        return sampledEntityPosition.subtract(interpolatedEntityPosition);
    }

    public static Vec3d visibilityOffset(
            Vec3d sampledMinecartPosition,
            Vec3d logicalMinecartPosition
    ) {
        return sampledMinecartPosition.subtract(logicalMinecartPosition);
    }
}
