package ilostmy_fish.physics;

/**
 * Converts minecart speed into impact damage potential and back again.
 *
 * <p>The breakpoint table is intentionally centralized here. It is provisional until the
 * intended CSV curve is available; replacing these points changes both damage and residual
 * velocity without changing collision handling.</p>
 */
public final class ImpactPhysics {
    public static final double TICKS_PER_SECOND = 20.0;

    // Keep vanilla-speed carts harmless. Above that, the provisional curve grows progressively
    // steeper so high-speed carts have enough potential to pass through ordinary mobs.
    private static final double[] SPEED_BREAKPOINTS_BPS = {
            0.0, 8.0, 16.0, 24.0, 32.0, 40.0, 60.0, 80.0
    };
    private static final double[] DAMAGE_BREAKPOINTS = {
            0.0, 0.0, 4.0, 12.0, 24.0, 40.0, 80.0, 120.0
    };

    private ImpactPhysics() {
    }

    public static double damagePotentialForSpeed(double speedBlocksPerSecond) {
        double speed = sanitizeNonNegative(speedBlocksPerSecond);
        return interpolateAndExtrapolate(speed, SPEED_BREAKPOINTS_BPS, DAMAGE_BREAKPOINTS);
    }

    public static double speedForDamagePotential(double damagePotential) {
        double potential = sanitizeNonNegative(damagePotential);
        if (potential == 0.0) {
            return 0.0;
        }
        return interpolateAndExtrapolate(potential, DAMAGE_BREAKPOINTS, SPEED_BREAKPOINTS_BPS);
    }

    /**
     * Consumes the target's maximum health from the cart's impact potential.
     *
     * <p>Maximum health deliberately acts as the generic resistance cost. Current health,
     * armor, shields, absorption, and entity type do not affect the speed calculation.</p>
     */
    public static ImpactResult impact(double speedBlocksPerSecond, double targetMaxHealth) {
        double speed = sanitizeNonNegative(speedBlocksPerSecond);
        double potential = damagePotentialForSpeed(speed);
        double healthCost = sanitizeNonNegative(targetMaxHealth);
        double remainingPotential = Math.max(0.0, potential - healthCost);
        double residualSpeed = speedForDamagePotential(remainingPotential);
        double speedScale = speed > 0.0 ? Math.clamp(residualSpeed / speed, 0.0, 1.0) : 0.0;
        return new ImpactResult(potential, remainingPotential, residualSpeed, speedScale);
    }

    private static double sanitizeNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double interpolateAndExtrapolate(double x, double[] xs, double[] ys) {
        if (x <= xs[0]) {
            return ys[0];
        }

        for (int index = 1; index < xs.length; index++) {
            if (x <= xs[index]) {
                return interpolate(x, xs[index - 1], xs[index], ys[index - 1], ys[index]);
            }
        }

        int last = xs.length - 1;
        return interpolate(x, xs[last - 1], xs[last], ys[last - 1], ys[last]);
    }

    private static double interpolate(double x, double x0, double x1, double y0, double y1) {
        double width = x1 - x0;
        if (width == 0.0) {
            return Math.max(y0, y1);
        }
        return y0 + (x - x0) * (y1 - y0) / width;
    }

    public record ImpactResult(
            double damagePotential,
            double remainingPotential,
            double residualSpeedBlocksPerSecond,
            double speedScale
    ) {
    }
}
