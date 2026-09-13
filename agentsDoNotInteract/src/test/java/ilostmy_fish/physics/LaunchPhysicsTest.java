package ilostmy_fish.physics;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LaunchPhysicsTest {
    private static final double TOLERANCE = 1.0E-12;

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0",
            "0.25, 0.11875",
            "1.0, 0.475",
            "4.0, 1.9",
            "9.0, 4.275"
    })
    void launchImpulseScalesLinearlyWithIncomingSpeed(double incomingSpeed, double expectedImpulse) {
        assertEquals(expectedImpulse, LaunchPhysics.calculateLaunchSpeed(incomingSpeed), TOLERANCE);
    }

    @Test
    void firstTickLeavingAscendingRailUsesCurrentSampleInsteadOfDefaultZero() {
        double currentSample = LaunchPhysics.calculateLaunchSpeed(2.0);

        double launchSpeed = LaunchPhysics.selectTransitionLaunchSpeed(currentSample, 0.0, false);

        assertEquals(currentSample, launchSpeed, TOLERANCE);
        assertNotEquals(0.0, launchSpeed, TOLERANCE);
    }

    @Test
    void establishedCartStillUsesPreviousTickSampleForLaunchTransition() {
        double currentSample = LaunchPhysics.calculateLaunchSpeed(2.0);
        double previousSample = LaunchPhysics.calculateLaunchSpeed(1.5);

        double launchSpeed = LaunchPhysics.selectTransitionLaunchSpeed(currentSample, previousSample, true);

        assertEquals(previousSample, launchSpeed, TOLERANCE);
    }
}
