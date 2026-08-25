package ilostmy_fish.trajectory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryStreamStateTest {
    @Test
    void initialIdleCartSendsAStartAndEndBeforeSilence() {
        TrajectoryStreamState stream = new TrajectoryStreamState();

        assertTrue(stream.shouldSend(false));
        assertEquals(TrajectoryStreamPhase.START, stream.advance(false));
        assertEquals(TrajectoryStreamPhase.END, stream.advance(false));
        assertFalse(stream.shouldSend(false));
        assertNull(stream.advance(false));
    }

    @Test
    void movingStreamSendsTwoStationaryFramesBeforeEnding() {
        TrajectoryStreamState stream = new TrajectoryStreamState();

        assertEquals(TrajectoryStreamPhase.START, stream.advance(true));
        assertEquals(TrajectoryStreamPhase.CONTINUE, stream.advance(true));
        assertEquals(TrajectoryStreamPhase.CONTINUE, stream.advance(false));
        assertEquals(TrajectoryStreamPhase.END, stream.advance(false));
        assertNull(stream.advance(false));
    }

    @Test
    void motionAfterIdleSilenceStartsANewStream() {
        TrajectoryStreamState stream = new TrajectoryStreamState();
        stream.advance(false);
        stream.advance(false);

        assertEquals(TrajectoryStreamPhase.START, stream.advance(true));
    }

    @Test
    void aSingleStationaryTickDoesNotSplitContinuousMotion() {
        TrajectoryStreamState stream = new TrajectoryStreamState();
        stream.advance(true);
        stream.advance(false);

        assertEquals(TrajectoryStreamPhase.CONTINUE, stream.advance(true));
    }
}
