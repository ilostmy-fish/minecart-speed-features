package ilostmy_fish.interpolation;

import net.minecraft.class_1297;
import net.minecraft.class_243;

/** Client-only callers use this class, but it deliberately depends only on common entity types. */
public final class VisualInterpolationHooks {
    private static final class_243 ZERO = new class_243(0.0, 0.0, 0.0);

    private VisualInterpolationHooks() {
    }

    /** Offset for an entity model. Minecarts use their body correction; riders use ride correction. */
    public static class_243 renderOffset(class_1297 entity, float tickDelta) {
        if (entity instanceof VisualInterpolationAccess access) {
            return access.minecartspeedfeatures$getVisualBodyOffset(tickDelta);
        }

        VisualInterpolationAccess vehicle = findMinecartVehicle(entity);
        return vehicle == null ? ZERO : vehicle.minecartspeedfeatures$getVisualRideOffset(tickDelta);
    }

    /** Offset for the focused camera. Camera coordinates are based on the rider, not cart snapping. */
    public static class_243 cameraOffset(class_1297 focusedEntity, float tickDelta) {
        VisualInterpolationAccess vehicle = findMinecartVehicle(focusedEntity);
        return vehicle == null ? ZERO : vehicle.minecartspeedfeatures$getVisualRideOffset(tickDelta);
    }

    public static boolean isZero(class_243 value) {
        return Math.abs(value.method_10216()) < 1.0E-9
                && Math.abs(value.method_10214()) < 1.0E-9
                && Math.abs(value.method_10215()) < 1.0E-9;
    }

    private static VisualInterpolationAccess findMinecartVehicle(class_1297 entity) {
        class_1297 current = entity == null ? null : entity.method_5854();
        // Vanilla riding chains are shallow. The guard makes malformed/modded chains harmless.
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof VisualInterpolationAccess access) {
                return access;
            }
            class_1297 next = current.method_5854();
            if (next == current) {
                break;
            }
            current = next;
        }
        return null;
    }
}
