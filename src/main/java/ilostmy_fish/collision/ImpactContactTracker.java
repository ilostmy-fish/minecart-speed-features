package ilostmy_fish.collision;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks living entities that have already received a minecart impact while contact continues.
 *
 * <p>Contact evidence records the current tick. The caller supplies the current hysteresis so a
 * gamerule change takes effect immediately for existing trackers.</p>
 */
public final class ImpactContactTracker {
    private final Map<UUID, Long> lastContactTicks = new HashMap<>();

    public void recordContact(UUID entityId, long currentTick) {
        this.lastContactTicks.put(entityId, currentTick);
    }

    public boolean isTracked(UUID entityId) {
        return this.lastContactTicks.containsKey(entityId);
    }

    public boolean isEmpty() {
        return this.lastContactTicks.isEmpty();
    }

    public boolean isActive(UUID entityId, long currentTick, int hysteresisTicks) {
        Long lastContactTick = this.lastContactTicks.get(entityId);
        return lastContactTick != null
                && currentTick - lastContactTick <= validateHysteresis(hysteresisTicks);
    }

    public void expireSeparatedContacts(long currentTick, int hysteresisTicks) {
        int hysteresis = validateHysteresis(hysteresisTicks);
        this.lastContactTicks.entrySet().removeIf(entry ->
                currentTick - entry.getValue() > hysteresis
        );
    }

    private static int validateHysteresis(int hysteresisTicks) {
        if (hysteresisTicks < 0) {
            throw new IllegalArgumentException("hysteresisTicks must be non-negative");
        }
        return hysteresisTicks;
    }
}
