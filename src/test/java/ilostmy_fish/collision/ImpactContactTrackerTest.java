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
    void zeroHysteresisRequiresContactEveryTick() {
        ImpactContactTracker contacts = new ImpactContactTracker();
        contacts.recordContact(TARGET, 20L);

        assertTrue(contacts.isActive(TARGET, 20L, 0));
        contacts.expireSeparatedContacts(21L, 0);

        assertFalse(contacts.isTracked(TARGET));
    }

    @Test
    void oneTickHysteresisSurvivesOneMissAndExpiresOnTheSecond() {
        ImpactContactTracker contacts = new ImpactContactTracker();
        contacts.recordContact(TARGET, 20L);

        contacts.expireSeparatedContacts(21L, 1);
        assertTrue(contacts.isActive(TARGET, 21L, 1));

        contacts.expireSeparatedContacts(22L, 1);
        assertFalse(contacts.isTracked(TARGET));
    }

    @Test
    void repeatedContactRefreshesTheHysteresisWindow() {
        ImpactContactTracker contacts = new ImpactContactTracker();
        contacts.recordContact(TARGET, 20L);

        for (long tick = 21L; tick <= 40L; tick++) {
            contacts.recordContact(TARGET, tick);
            contacts.expireSeparatedContacts(tick, 1);
            assertTrue(contacts.isActive(TARGET, tick, 1));
        }
    }

    @Test
    void fiveTickHysteresisExpiresAfterFiveMissedTicks() {
        ImpactContactTracker contacts = new ImpactContactTracker();
        contacts.recordContact(TARGET, 20L);

        contacts.expireSeparatedContacts(25L, 5);
        assertTrue(contacts.isActive(TARGET, 25L, 5));

        contacts.expireSeparatedContacts(26L, 5);
        assertFalse(contacts.isTracked(TARGET));
    }
}
