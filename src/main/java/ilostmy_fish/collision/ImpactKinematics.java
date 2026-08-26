package ilostmy_fish.collision;

import ilostmy_fish.physics.ImpactPhysics;
import net.minecraft.util.math.Vec3d;

/** Distinguishes a real moving impact from retained velocity at a blocked contact plane. */
public final class ImpactKinematics {
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12;
    private static final double MIN_FORWARD_MOVEMENT = 1.0E-7;

    private ImpactKinematics() {
    }

    public static boolean isDamagingApproach(Vec3d incomingVelocity, Vec3d actualMovement) {
        double horizontalSpeedSquared = incomingVelocity.getX() * incomingVelocity.getX()
                + incomingVelocity.getZ() * incomingVelocity.getZ();
        if (horizontalSpeedSquared <= MIN_DIRECTION_LENGTH_SQUARED) {
            return false;
        }

        double speedBlocksPerSecond = Math.sqrt(horizontalSpeedSquared)
                * ImpactPhysics.TICKS_PER_SECOND;
        if (speedBlocksPerSecond <= ImpactPhysics.DAMAGE_SPEED_OFFSET_BPS) {
            return false;
        }

        double inverseSpeed = 1.0 / Math.sqrt(horizontalSpeedSquared);
        double forwardMovement = actualMovement.getX() * incomingVelocity.getX() * inverseSpeed
                + actualMovement.getZ() * incomingVelocity.getZ() * inverseSpeed;
        return forwardMovement > MIN_FORWARD_MOVEMENT;
    }
}
