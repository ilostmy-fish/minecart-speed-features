package ilostmy_fish.interpolation;

import net.minecraft.class_243;

/**
 * Client-render interpolation supplied by the minecart mixin.
 *
 * The ride offset is relative to vanilla's straight snapshot interpolation and is used for
 * passengers/camera. The body offset also compensates for the 1.21.1 minecart renderer's own
 * snapPositionToRail pass so the cart model itself lands on the same reconstructed path.
 */
public interface VisualInterpolationAccess {
    class_243 minecartspeedfeatures$getVisualRideOffset(float tickDelta);

    class_243 minecartspeedfeatures$getVisualBodyOffset(float tickDelta);
}
