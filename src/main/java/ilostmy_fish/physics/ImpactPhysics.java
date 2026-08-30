package ilostmy_fish.physics;

/**
 * Converts minecart speed into impact damage potential and back again.
 *
 * <p>The breakpoint table is intentionally centralized here. The final tranche is extrapolated
 * indefinitely, so the +7 damage-per-block-per-second slope continues through the mod's current
 * 1000 b/s maximum speed without introducing a physics-specific cap.</p>
 */
public final class ImpactPhysics {
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double DAMAGE_SPEED_OFFSET_BPS = 2.0;

    private static final double[] SPEED_BREAKPOINTS_BPS = {
            0.0, 17.0, 50.0, 83.0, 116.0, 149.0, 182.0, 200.0
    };
    private static final double[] DAMAGE_BREAKPOINTS = {
            0.0, 17.0, 83.0, 182.0, 314.0, 479.0, 677.0, 803.0
    };

    private ImpactPhysics() {
    }

    public static double damagePotentialForSpeed(double speedBlocksPerSecond) {
        double speed = sanitizeNonNegative(speedBlocksPerSecond);
        double effectiveSpeed = Math.max(0.0, speed - DAMAGE_SPEED_OFFSET_BPS);
        return interpolateAndExtrapolate(effectiveSpeed, SPEED_BREAKPOINTS_BPS, DAMAGE_BREAKPOINTS);
    }

    public static double speedForDamagePotential(double damagePotential) {
        double potential = sanitizeNonNegative(damagePotential);
        if (potential == 0.0) {
            return DAMAGE_SPEED_OFFSET_BPS;
        }
        return interpolateAndExtrapolate(potential, DAMAGE_BREAKPOINTS, SPEED_BREAKPOINTS_BPS)
                + DAMAGE_SPEED_OFFSET_BPS;
    }

    /**
     * Consumes the target's maximum health from the cart's impact potential.
     *
     * <p>Maximum health deliberately acts as the generic resistance cost. Therefore, current health,
     * armor, shields, absorption, and entity type do not affect the speed calculation here.</p>
     */
    public static ImpactResult impact(double speedBlocksPerSecond, double targetMaxHealth) {
        double speed = sanitizeNonNegative(speedBlocksPerSecond);
        if (speed <= DAMAGE_SPEED_OFFSET_BPS) {
            return new ImpactResult(0.0, 0.0, speed, 1.0);
        }

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
