package ilostmy_fish.interpolation;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Client-only callers use this class, but it deliberately depends only on common entity types.
 */
public final class VisualInterpolationHooks {
    private static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    private VisualInterpolationHooks() {
    }

    /**
     * Offset for an entity model. Minecarts use their body correction; riders use ride correction.
     */
    public static Vec3d renderOffset(Entity entity, float tickDelta) {
        if (entity instanceof VisualInterpolationAccess access) {
            return access.minecartspeedfeatures$getVisualBodyOffset(tickDelta);
        }

        VisualInterpolationAccess vehicle = findMinecartVehicle(entity);
        return vehicle == null ? ZERO : vehicle.minecartspeedfeatures$getVisualRideOffset(tickDelta);
    }

    /**
     * Offset for the focused camera. Camera coordinates are based on the rider, not cart snapping.
     */
    public static Vec3d cameraOffset(Entity focusedEntity, float tickDelta) {
        VisualInterpolationAccess vehicle = findMinecartVehicle(focusedEntity);
        return vehicle == null ? ZERO : vehicle.minecartspeedfeatures$getVisualRideOffset(tickDelta);
    }

    public static boolean isZero(Vec3d value) {
        return Math.abs(value.getX()) < 1.0E-9
                && Math.abs(value.getY()) < 1.0E-9
                && Math.abs(value.getZ()) < 1.0E-9;
    }

    private static VisualInterpolationAccess findMinecartVehicle(Entity entity) {
        Entity current = entity == null ? null : entity.getVehicle();
        // Vanilla riding chains are shallow. The guard makes malformed/modded chains harmless.
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof VisualInterpolationAccess access) {
                return access;
            }
            Entity next = current.getVehicle();
            if (next == current) {
                break;
            }
            current = next;
        }
        return null;
    }
}
