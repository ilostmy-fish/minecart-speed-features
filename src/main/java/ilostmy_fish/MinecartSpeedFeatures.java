package ilostmy_fish;

import net.minecraft.world.GameRules;

/**
 * Shared state for the standalone backport.
 */
public final class MinecartSpeedFeatures {
    public static final String MOD_ID = "minecart-speed-features";

    /**
     * Registered during GameRules.<clinit> by GameRulesMixin.
     */
    public static GameRules.Key<GameRules.IntRule> MINECART_MAX_SPEED;

    private MinecartSpeedFeatures() {
    }
}
