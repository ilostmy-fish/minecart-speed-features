package ilostmy_fish.physics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpactPhysicsTest {
    private static final double TOLERANCE = 1.0E-9;

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0",
            "1.0, 0.0",
            "2.0, 0.0",
            "3.0, 1.0",
            "19.0, 17.0",
            "19.5, 18.0",
            "20.0, 19.0",
            "52.0, 83.0",
            "53.0, 86.0",
            "85.0, 182.0",
            "86.0, 186.0",
            "118.0, 314.0",
            "119.0, 319.0",
            "151.0, 479.0",
            "152.0, 485.0",
            "184.0, 677.0",
            "185.0, 684.0",
            "202.0, 803.0",
            "1000.0, 6389.0"
    })
    void damagePotentialUsesConfiguredCurve(double speedBps, double expectedDamage) {
        assertEquals(expectedDamage, ImpactPhysics.damagePotentialForSpeed(speedBps), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({
            "3.0",
            "19.5",
            "44.25",
            "85.0",
            "134.75",
            "184.5",
            "202.0",
            "1000.0"
    })
    void curveRoundTripsPositiveDamageSpeeds(double speedBps) {
        double potential = ImpactPhysics.damagePotentialForSpeed(speedBps);
        assertEquals(speedBps, ImpactPhysics.speedForDamagePotential(potential), TOLERANCE);
    }

    @Test
    void zeroPotentialInverseUsesNonDestructiveBaselineSpeed() {
        assertEquals(2.0, ImpactPhysics.speedForDamagePotential(0.0), TOLERANCE);
    }

    @Test
    void ordinaryMobConsumesItsMaximumHealthFromPotential() {
        ImpactPhysics.ImpactResult result = ImpactPhysics.impact(21.0, 20.0);

        assertEquals(21.0, result.damagePotential(), TOLERANCE);
        assertEquals(1.0, result.remainingPotential(), TOLERANCE);
        assertEquals(3.0, result.residualSpeedBlocksPerSecond(), TOLERANCE);
        assertEquals(1.0 / 7.0, result.speedScale(), TOLERANCE);
    }

    @Test
    void insufficientPotentialLeavesTheNonDestructiveBaselineSpeed() {
        ImpactPhysics.ImpactResult result = ImpactPhysics.impact(20.0, 20.0);

        assertEquals(19.0, result.damagePotential(), TOLERANCE);
        assertEquals(0.0, result.remainingPotential(), TOLERANCE);
        assertEquals(2.0, result.residualSpeedBlocksPerSecond(), TOLERANCE);
        assertEquals(0.1, result.speedScale(), TOLERANCE);
    }

    @Test
    void subThresholdImpactNeverAcceleratesTheCart() {
        ImpactPhysics.ImpactResult result = ImpactPhysics.impact(1.5, 20.0);

        assertEquals(0.0, result.damagePotential(), TOLERANCE);
        assertEquals(0.0, result.remainingPotential(), TOLERANCE);
        assertEquals(1.5, result.residualSpeedBlocksPerSecond(), TOLERANCE);
        assertEquals(1.0, result.speedScale(), TOLERANCE);
    }
}
