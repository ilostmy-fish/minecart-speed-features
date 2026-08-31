package ilostmy_fish.collision;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps an impacted entity non-blocking until the minecart has actually cleared it.
 *
 * <p>The initial clipped impact usually leaves the two boxes exactly face-to-face. A short
 * hysteresis keeps that contact active long enough for the next movement to create a strict
 * overlap. Every subsequent overlap refreshes the contact, so low residual speeds are not forced
 * through an arbitrary fixed tick window.</p>
 */
public final class ImpactContactTracker {
    private final long separationHysteresisTicks;
    private final Map<UUID, Long> lastContactTicks = new HashMap<>();

    public ImpactContactTracker(long separationHysteresisTicks) {
        if (separationHysteresisTicks < 0L) {
            throw new IllegalArgumentException("separationHysteresisTicks must be non-negative");
        }
        this.separationHysteresisTicks = separationHysteresisTicks;
    }

    public void recordContact(UUID entityId, long currentTick) {
        this.lastContactTicks.put(entityId, currentTick);
    }

    public boolean isTracked(UUID entityId) {
        return this.lastContactTicks.containsKey(entityId);
    }

    public boolean isEmpty() {
        return this.lastContactTicks.isEmpty();
    }

    public boolean isActive(UUID entityId, long currentTick) {
        Long lastContactTick = this.lastContactTicks.get(entityId);
        return lastContactTick != null
                && currentTick - lastContactTick <= this.separationHysteresisTicks;
    }

    public void expireSeparatedContacts(long currentTick) {
        this.lastContactTicks.entrySet().removeIf(entry ->
                currentTick - entry.getValue() > this.separationHysteresisTicks
        );
    }
}
