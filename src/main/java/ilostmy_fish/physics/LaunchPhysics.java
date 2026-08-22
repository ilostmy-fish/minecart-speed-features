package ilostmy_fish.physics;

/**
 * Pure launch-transition calculations kept separate from entity and rail traversal state.
 */
public final class LaunchPhysics {
    private static final double LAUNCH_SPEED_SCALE = 0.95 / 2.0;

    private LaunchPhysics() {
    }

    /**
     * Converts the incoming velocity magnitude into the impulse used when leaving a slope.
     */
    public static double calculateLaunchSpeed(double incomingSpeed) {
        return incomingSpeed * LAUNCH_SPEED_SCALE;
    }

    /**
     * Keeps the previous-tick behavior once history exists, while allowing a cart that reaches
     * the end of an ascending rail on its first rail tick to use that tick's nonzero sample.
     */
    public static double selectTransitionLaunchSpeed(
            double currentSample,
            double previousSample,
            boolean hasPreviousSample
    ) {
        return hasPreviousSample ? previousSample : currentSample;
    }
}
