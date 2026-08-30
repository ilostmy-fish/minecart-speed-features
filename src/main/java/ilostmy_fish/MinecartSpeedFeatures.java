package ilostmy_fish;

import ilostmy_fish.network.MinecartTrajectoryNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.GameRules;

/**
 * Common initialization and gamerule state for the standalone backport.
 */
public final class MinecartSpeedFeatures implements ModInitializer {
    public static final String MOD_ID = "minecart-speed-features";

    public static GameRules.Key<GameRules.IntRule> MINECART_MAX_SPEED;
    public static GameRules.Key<GameRules.IntRule> MINECART_DAMAGE_PERCENT;

    @Override
    public void onInitialize() {
        MinecartTrajectoryNetworking.initialize();
    }
}
