package ilostmy_fish.damage;

import ilostmy_fish.MinecartSpeedFeatures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Damage source for a minecart impact. The minecart is the direct source and an optional passenger
 * is the attacker, allowing vanilla kill attribution to work for players and mobs alike.
 */
public final class MinecartImpactDamageSource extends DamageSource {
    public static final RegistryKey<DamageType> TYPE = RegistryKey.of(
            RegistryKeys.DAMAGE_TYPE,
            Identifier.of(MinecartSpeedFeatures.MOD_ID, "minecart_impact")
    );

    private MinecartImpactDamageSource(
            RegistryEntry<DamageType> type,
            Entity minecart,
            @Nullable Entity attacker
    ) {
        super(type, minecart, attacker);
    }

    public static MinecartImpactDamageSource create(
            DamageSources damageSources,
            Entity minecart,
            @Nullable Entity attacker
    ) {
        return new MinecartImpactDamageSource(
                damageSources.registry.entryOf(TYPE),
                minecart,
                attacker
        );
    }

    @Override
    public Text getDeathMessage(LivingEntity killed) {
        Entity attacker = this.getAttacker();
        if (attacker == null) {
            return Text.translatable(
                    "death.attack.minecartImpact",
                    killed.getDisplayName()
            );
        }

        return Text.translatable(
                "death.attack.minecartImpact.attributed",
                killed.getDisplayName(),
                attacker.getDisplayName()
        );
    }
}
