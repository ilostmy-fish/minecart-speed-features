package ilostmy_fish.interpolation;

import net.minecraft.util.math.Vec3d;

/**
 * Client-render interpolation supplied by the minecart mixin.
 * <p>
 * The ride offset is relative to vanilla's straight snapshot interpolation and is used for
 * passengers/camera. The body offset also compensates for the 1.21.1 minecart renderer's own
 * snapPositionToRail pass so the cart model itself lands on the same reconstructed path.
 */
public interface VisualInterpolationAccess {
    Vec3d minecartspeedfeatures$getVisualRideOffset(float tickDelta);

    Vec3d minecartspeedfeatures$getVisualBodyOffset(float tickDelta);
}
