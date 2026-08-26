package ilostmy_fish.collision;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactContactTrackerTest {
    private static final UUID TARGET = UUID.fromString("4f499f84-c13d-43e9-87f1-a045490af8cc");

    @Test
    void initialClippedImpactDisarmsForOneTick() {
        ImpactContactTracker contacts = new ImpactContactTracker(1L);

        contacts.recordContact(TARGET, 20L);

        assertTrue(contacts.isDisarmed(TARGET, 20L));
        assertTrue(contacts.isDisarmed(TARGET, 21L));
        assertFalse(contacts.isDisarmed(TARGET, 22L));
    }

    @Test
    void actualOverlapRefreshesTheHysteresis() {
        ImpactContactTracker contacts = new ImpactContactTracker(1L);
        contacts.recordContact(TARGET, 20L);

        contacts.recordContact(TARGET, 21L);

        assertTrue(contacts.isDisarmed(TARGET, 22L));
        assertFalse(contacts.isDisarmed(TARGET, 23L));
    }

    @Test
    void suppressedClippedCollisionDoesNotRefreshContact() {
        ImpactContactTracker contacts = new ImpactContactTracker(1L);
        contacts.recordContact(TARGET, 20L);

        assertTrue(contacts.isDisarmed(TARGET, 21L));
        assertFalse(contacts.isDisarmed(TARGET, 22L));
    }

    @Test
    void expiredContactsAreRemovedFromTracking() {
        ImpactContactTracker contacts = new ImpactContactTracker(1L);
        contacts.recordContact(TARGET, 20L);

        contacts.expireSeparatedContacts(22L);

        assertFalse(contacts.isTracked(TARGET));
    }
}
