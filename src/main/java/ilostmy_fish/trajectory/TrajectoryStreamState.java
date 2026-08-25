package ilostmy_fish.trajectory;

import org.jetbrains.annotations.Nullable;

/**
 * Per-recipient server state for one minecart's trajectory updates.
 *
 * <p>Two stationary frames close a stream. The first can become current with the second as its
 * lookahead, and the explicit end marker lets playback consume the terminal frame without waiting
 * for a packet that the idle server deliberately will not send.</p>
 */
public final class TrajectoryStreamState {
    public static final int STATIONARY_FRAMES_BEFORE_SILENCE = 2;

    private boolean streamOpen;
    private int stationaryFramesSent;

    public boolean shouldSend(boolean meaningfulMotion) {
        return meaningfulMotion
                || this.stationaryFramesSent < STATIONARY_FRAMES_BEFORE_SILENCE;
    }

    /** Records one server tick and returns the phase to send, or {@code null} for idle silence. */
    @Nullable
    public TrajectoryStreamPhase advance(boolean meaningfulMotion) {
        if (meaningfulMotion) {
            this.stationaryFramesSent = 0;
            if (!this.streamOpen) {
                this.streamOpen = true;
                return TrajectoryStreamPhase.START;
            }
            return TrajectoryStreamPhase.CONTINUE;
        }

        if (this.stationaryFramesSent >= STATIONARY_FRAMES_BEFORE_SILENCE) {
            return null;
        }

        this.stationaryFramesSent++;
        if (!this.streamOpen) {
            this.streamOpen = true;
            return TrajectoryStreamPhase.START;
        }
        if (this.stationaryFramesSent == STATIONARY_FRAMES_BEFORE_SILENCE) {
            this.streamOpen = false;
            return TrajectoryStreamPhase.END;
        }
        return TrajectoryStreamPhase.CONTINUE;
    }
}
