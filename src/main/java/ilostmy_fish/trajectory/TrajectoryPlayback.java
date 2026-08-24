package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.NavigableMap;
import java.util.TreeMap;

/** An ordered client buffer that retains one consecutive trajectory of playback lookahead. */
public final class TrajectoryPlayback {
    private static final int MAX_QUEUED_TRAJECTORIES = 8;

    private final NavigableMap<Long, MinecartTrajectory> queued = new TreeMap<>();
    private MinecartTrajectory current;
    private boolean currentComplete;

    public void accept(MinecartTrajectory trajectory) {
        if (this.current != null && trajectory.serverTick() <= this.current.serverTick()) {
            return;
        }
        this.queued.put(trajectory.serverTick(), trajectory);
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
        MinecartTrajectory next = this.queued.get(expectedTick);
        if (next != null && this.queued.containsKey(expectedTick + 1L)) {
            this.current = this.queued.remove(expectedTick);
            this.currentComplete = false;
            return;
        }

        this.currentComplete = true;
        Long restart = this.findBufferedStart();
        if (restart != null && restart > expectedTick) {
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
        return this.current.sample(progress);
    }

    public long currentServerTick() {
        return this.current == null ? Long.MIN_VALUE : this.current.serverTick();
    }

    public boolean hasCurrent() {
        return this.current != null;
    }

    @Nullable
    public Vec3d currentFinalVelocity() {
        return this.current == null ? null : this.current.finalVelocity();
    }

    private void startBufferedRun() {
        Long start = this.findBufferedStart();
        if (start != null) {
            this.startAt(start);
        }
    }

    @Nullable
    private Long findBufferedStart() {
        for (Long tick : this.queued.navigableKeySet()) {
            if (this.queued.containsKey(tick + 1L)) {
                return tick;
            }
        }
        return null;
    }

    private void startAt(long serverTick) {
        this.queued.headMap(serverTick, false).clear();
        this.current = this.queued.remove(serverTick);
        this.currentComplete = false;
    }
}
