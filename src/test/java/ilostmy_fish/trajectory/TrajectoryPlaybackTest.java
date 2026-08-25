package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static ilostmy_fish.trajectory.TrajectoryStreamPhase.CONTINUE;
import static ilostmy_fish.trajectory.TrajectoryStreamPhase.END;
import static ilostmy_fish.trajectory.TrajectoryStreamPhase.START;

class TrajectoryPlaybackTest {
    private static final double TOLERANCE = 1.0E-12;

    @Test
    void waitsForOneConsecutiveTrajectoryOfLookahead() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(10L, 0.0, 1.0), START);

        playback.advance();

        assertNull(playback.sample(0.5F));

        playback.accept(trajectory(11L, 1.0, 2.0), CONTINUE);
        playback.advance();

        assertEquals(10L, playback.currentServerTick());
        assertEquals(0.5, sample(playback, 0.5F).position().getX(), TOLERANCE);
    }

    @Test
    void acceptsOutOfOrderPacketsButPlaysBySequence() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(21L, 1.0, 2.0), CONTINUE);
        playback.accept(trajectory(20L, 0.0, 1.0), START);
        playback.accept(trajectory(22L, 2.0, 3.0), CONTINUE);

        playback.advance();
        playback.advance();

        assertEquals(21L, playback.currentServerTick());
    }

    @Test
    void underrunHoldsTheLastAuthoritativeEndpoint() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(30L, 0.0, 1.0), START);
        playback.accept(trajectory(31L, 1.0, 2.0), CONTINUE);
        playback.advance();
        playback.advance();

        assertEquals(1.0, sample(playback, 0.0F).position().getX(), TOLERANCE);
        assertEquals(1.0, sample(playback, 0.75F).position().getX(), TOLERANCE);

        playback.accept(trajectory(32L, 2.0, 3.0), CONTINUE);
        playback.advance();
        playback.advance();

        assertEquals(2.0, sample(playback, 0.0F).position().getX(), TOLERANCE);
        assertEquals(2.0, sample(playback, 0.75F).position().getX(), TOLERANCE);
    }

    @Test
    void gapResynchronizesOnlyAfterAnotherBufferedPairExists() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(40L, 0.0, 1.0), START);
        playback.accept(trajectory(41L, 1.0, 2.0), CONTINUE);
        playback.advance();
        playback.advance();
        playback.accept(trajectory(43L, 3.0, 4.0), START);

        playback.advance();
        assertEquals(40L, playback.currentServerTick());

        playback.accept(trajectory(44L, 4.0, 5.0), CONTINUE);
        playback.advance();

        assertEquals(43L, playback.currentServerTick());
    }

    @Test
    void exposesTheVelocityForTheLogicalTickEndpoint() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(50L, 0.0, 2.0), START);
        playback.accept(trajectory(51L, 2.0, 4.0), CONTINUE);

        playback.advance();

        Vec3d velocity = playback.currentFinalVelocity();
        assertNotNull(velocity);
        assertEquals(2.0, velocity.getX(), TOLERANCE);
    }

    @Test
    void oneSelectedTrajectoryOwnsTheWholeRenderInterval() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(60L, 0.0, 2.0), START);
        playback.accept(trajectory(61L, 2.0, 4.0), CONTINUE);
        playback.accept(trajectory(62L, 4.0, 6.0), CONTINUE);

        playback.advance();

        assertEquals(60L, playback.currentServerTick());
        assertEquals(0.0, sample(playback, 0.0F).position().getX(), TOLERANCE);
        assertEquals(1.0, sample(playback, 0.5F).position().getX(), TOLERANCE);
        assertEquals(2.0, sample(playback, 1.0F).position().getX(), TOLERANCE);
        assertEquals(60L, playback.currentServerTick());

        playback.advance();

        assertEquals(61L, playback.currentServerTick());
        assertEquals(2.0, sample(playback, 0.0F).position().getX(), TOLERANCE);
    }

    @Test
    void terminalTrajectoryAdvancesWithoutAnUnsentLookaheadPacket() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(70L, 0.0, 1.0), START);
        playback.accept(trajectory(71L, 1.0, 1.0), END);

        playback.advance();
        playback.advance();

        assertEquals(71L, playback.currentServerTick());
        assertEquals(Vec3d.ZERO, playback.currentFinalVelocity());

        playback.advance();
        assertEquals(1.0, sample(playback, 0.75F).position().getX(), TOLERANCE);
    }

    @Test
    void endedStreamRequiresAnExplicitRestart() {
        TrajectoryPlayback playback = new TrajectoryPlayback();
        playback.accept(trajectory(80L, 0.0, 0.0), START);
        playback.accept(trajectory(81L, 0.0, 0.0), END);
        playback.advance();
        playback.advance();
        playback.advance();

        playback.accept(trajectory(82L, 0.0, 1.0), CONTINUE);
        playback.accept(trajectory(83L, 1.0, 2.0), CONTINUE);
        playback.advance();

        assertEquals(81L, playback.currentServerTick());

        playback.accept(trajectory(84L, 2.0, 3.0), START);
        playback.accept(trajectory(85L, 3.0, 4.0), CONTINUE);
        playback.advance();

        assertEquals(84L, playback.currentServerTick());
    }

    private static TrajectorySample sample(TrajectoryPlayback playback, float tickDelta) {
        return Objects.requireNonNull(playback.sample(tickDelta));
    }

    private static MinecartTrajectory trajectory(long tick, double startX, double endX) {
        return new MinecartTrajectory(
                tick,
                List.of(
                        new TrajectoryPoint(0.0, new Vec3d(startX, 0.0, 0.0)),
                        new TrajectoryPoint(1.0, new Vec3d(endX, 0.0, 0.0))
                ),
                new Vec3d(endX - startX, 0.0, 0.0)
        );
    }
}
