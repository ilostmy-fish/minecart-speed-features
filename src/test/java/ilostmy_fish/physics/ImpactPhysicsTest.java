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
            "8.0, 0.0",
            "16.0, 4.0",
            "24.0, 12.0",
            "32.0, 24.0",
            "40.0, 40.0",
            "60.0, 80.0",
            "80.0, 120.0",
            "100.0, 160.0"
    })
    void damagePotentialUsesProvisionalCurve(double speedBps, double expectedDamage) {
        assertEquals(expectedDamage, ImpactPhysics.damagePotentialForSpeed(speedBps), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({
            "10.0",
            "16.0",
            "21.5",
            "32.0",
            "47.0",
            "75.0",
            "100.0"
    })
    void curveRoundTripsPositiveDamageSpeeds(double speedBps) {
        double potential = ImpactPhysics.damagePotentialForSpeed(speedBps);
        assertEquals(speedBps, ImpactPhysics.speedForDamagePotential(potential), TOLERANCE);
    }

    @Test
    void ordinaryMobConsumesItsMaximumHealthFromPotential() {
        ImpactPhysics.ImpactResult result = ImpactPhysics.impact(32.0, 20.0);

        assertEquals(24.0, result.damagePotential(), TOLERANCE);
        assertEquals(4.0, result.remainingPotential(), TOLERANCE);
        assertEquals(16.0, result.residualSpeedBlocksPerSecond(), TOLERANCE);
        assertEquals(0.5, result.speedScale(), TOLERANCE);
    }

    @Test
    void insufficientPotentialStopsTheCart() {
        ImpactPhysics.ImpactResult result = ImpactPhysics.impact(24.0, 20.0);

        assertEquals(12.0, result.damagePotential(), TOLERANCE);
        assertEquals(0.0, result.remainingPotential(), TOLERANCE);
        assertEquals(0.0, result.residualSpeedBlocksPerSecond(), TOLERANCE);
        assertEquals(0.0, result.speedScale(), TOLERANCE);
    }
}
