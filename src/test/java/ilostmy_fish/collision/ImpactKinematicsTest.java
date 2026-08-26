package ilostmy_fish.collision;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactKinematicsTest {
    @Test
    void retainedVelocityAtAContactPlaneCannotDamage() {
        assertFalse(ImpactKinematics.isDamagingApproach(
                new Vec3d(1.0, 0.0, 0.0),
                Vec3d.ZERO
        ));
    }

    @Test
    void slowShovingCannotDamage() {
        assertFalse(ImpactKinematics.isDamagingApproach(
                new Vec3d(0.1, 0.0, 0.0),
                new Vec3d(0.1, 0.0, 0.0)
        ));
    }

    @Test
    void sidewaysRailCorrectionCannotDamage() {
        assertFalse(ImpactKinematics.isDamagingApproach(
                new Vec3d(1.0, 0.0, 0.0),
                new Vec3d(0.0, 0.0, 0.25)
        ));
    }

    @Test
    void backwardCorrectionCannotDamage() {
        assertFalse(ImpactKinematics.isDamagingApproach(
                new Vec3d(1.0, 0.0, 0.0),
                new Vec3d(-0.1, 0.0, 0.0)
        ));
    }

    @Test
    void movingFastIntoAContactCanDamage() {
        assertTrue(ImpactKinematics.isDamagingApproach(
                new Vec3d(1.0, 0.0, 0.0),
                new Vec3d(0.01, 0.0, 0.0)
        ));
    }
}
