package ilostmy_fish.collision;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks when a minecart most recently made real contact with an entity.
 *
 * <p>An impact records contact even when movement clipping leaves the two boxes face-to-face.
 * Subsequent callers must record contact only for a strict bounding-box overlap. Merely checking
 * whether an entity is still disarmed never extends the latch.</p>
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

    public boolean isDisarmed(UUID entityId, long currentTick) {
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
