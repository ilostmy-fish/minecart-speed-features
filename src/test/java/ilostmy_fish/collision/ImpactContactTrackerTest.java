package ilostmy_fish.collision;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactContactTrackerTest {
    private static final UUID TARGET = UUID.fromString(
            "4f499f84-c13d-43e9-87f1-a045490af8cc"
    );

    @Test
    void clippedFaceContactSurvivesUntilTheNextMovementTick() {
        ImpactContactTracker contacts = new ImpactContactTracker(1L);

        contacts.recordContact(TARGET, 20L);
        contacts.expireSeparatedContacts(21L);

        assertTrue(contacts.isActive(TARGET, 21L));
    }

    @Test
    void sustainedOverlapDoesNotExpireAtLowResidualSpeed() {
        ImpactContactTracker contacts = new ImpactContactTracker(1L);
        contacts.recordContact(TARGET, 20L);

        for (long tick = 21L; tick <= 40L; tick++) {
            contacts.recordContact(TARGET, tick);
            contacts.expireSeparatedContacts(tick);
            assertTrue(contacts.isActive(TARGET, tick));
        }
    }

    @Test
    void contactExpiresOnlyAfterAFullSeparatedTick() {
        ImpactContactTracker contacts = new ImpactContactTracker(1L);
        contacts.recordContact(TARGET, 20L);

        contacts.expireSeparatedContacts(21L);
        assertTrue(contacts.isTracked(TARGET));

        contacts.expireSeparatedContacts(22L);
        assertFalse(contacts.isTracked(TARGET));
        assertTrue(contacts.isEmpty());
    }
}
