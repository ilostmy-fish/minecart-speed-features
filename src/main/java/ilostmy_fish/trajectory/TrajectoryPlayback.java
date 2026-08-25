package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** An ordered client buffer that retains one consecutive trajectory of playback lookahead. */
public final class TrajectoryPlayback {
    private static final int MAX_QUEUED_TRAJECTORIES = 8;

    private final NavigableMap<Long, BufferedTrajectory> queued = new TreeMap<>();
    private BufferedTrajectory current;
    private boolean currentComplete;

    public void accept(MinecartTrajectory trajectory, TrajectoryStreamPhase phase) {
        Objects.requireNonNull(trajectory, "trajectory");
        Objects.requireNonNull(phase, "phase");
        if (this.current != null && trajectory.serverTick() <= this.current.serverTick()) {
            return;
        }

        long serverTick = trajectory.serverTick();
        if (phase == TrajectoryStreamPhase.START) {
            // A start marker is an explicit resynchronization boundary. Keep the current endpoint
            // on screen until this new run has its lookahead, but discard older queued work.
            this.queued.headMap(serverTick, false).clear();
        }
        this.queued.put(serverTick, new BufferedTrajectory(trajectory, phase));
        while (this.queued.size() > MAX_QUEUED_TRAJECTORIES) {
            this.queued.pollFirstEntry();
        }
    }

    /** Advances playback before the client world snapshots entity positions for the next tick. */
    public void advance() {
        if (this.current == null) {
            this.startBufferedRun();
            return;
        }

        long expectedTick = this.current.serverTick() + 1L;
        BufferedTrajectory next = this.queued.get(expectedTick);
        if (next != null && this.canAdvanceTo(expectedTick, next)) {
            this.startAt(expectedTick);
            return;
        }

        this.currentComplete = true;
        Long restart = this.findBufferedStart(
                this.current.phase() == TrajectoryStreamPhase.END
        );
        if (restart != null && restart > this.current.serverTick()) {
            this.startAt(restart);
        }
    }

    @Nullable
    public TrajectorySample sample(float tickDelta) {
        if (this.current == null) {
            return null;
        }
        double progress = this.currentComplete
                ? 1.0
                : Math.clamp(tickDelta, 0.0F, 1.0F);
        return this.current.trajectory().sample(progress);
    }

    public long currentServerTick() {
        return this.current == null ? Long.MIN_VALUE : this.current.serverTick();
    }

    public boolean hasCurrent() {
        return this.current != null;
    }

    @Nullable
    public Vec3d currentFinalVelocity() {
        return this.current == null ? null : this.current.trajectory().finalVelocity();
    }

    private boolean canAdvanceTo(long serverTick, BufferedTrajectory next) {
        if (this.current.phase() == TrajectoryStreamPhase.END
                && next.phase() != TrajectoryStreamPhase.START) {
            return false;
        }
        if (next.phase() == TrajectoryStreamPhase.END) {
            return true;
        }
        return this.hasConsecutiveLookahead(serverTick);
    }

    private void startBufferedRun() {
        Long start = this.findBufferedStart(false);
        if (start != null) {
            this.startAt(start);
        }
    }

    @Nullable
    private Long findBufferedStart(boolean requireExplicitStart) {
        // Prefer the protocol's explicit boundaries. This makes an intentional idle pause and a
        // later restart unambiguous even when their server ticks are consecutive.
        for (Map.Entry<Long, BufferedTrajectory> entry : this.queued.entrySet()) {
            if (entry.getValue().phase() == TrajectoryStreamPhase.START
                    && this.hasConsecutiveLookahead(entry.getKey())) {
                return entry.getKey();
            }
        }

        if (requireExplicitStart) {
            return null;
        }

        // A pair remains a safe fallback for packets accepted without their start marker.
        for (Map.Entry<Long, BufferedTrajectory> entry : this.queued.entrySet()) {
            long serverTick = entry.getKey();
            BufferedTrajectory next = this.queued.get(serverTick + 1L);
            if (entry.getValue().phase() != TrajectoryStreamPhase.END
                    && next != null
                    && next.phase() != TrajectoryStreamPhase.START) {
                return serverTick;
            }
        }
        return null;
    }

    private boolean hasConsecutiveLookahead(long serverTick) {
        return this.queued.containsKey(serverTick + 1L);
    }

    private void startAt(long serverTick) {
        this.queued.headMap(serverTick, false).clear();
        this.current = this.queued.remove(serverTick);
        this.currentComplete = false;
    }

    private record BufferedTrajectory(
            MinecartTrajectory trajectory,
            TrajectoryStreamPhase phase
    ) {
        private long serverTick() {
            return this.trajectory.serverTick();
        }
    }
}
