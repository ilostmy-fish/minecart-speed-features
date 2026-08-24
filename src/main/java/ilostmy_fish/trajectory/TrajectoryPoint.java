package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;

import java.util.Objects;

/** One authoritative position at a fraction of a server tick. */
public record TrajectoryPoint(double timeFraction, Vec3d position) {
    public TrajectoryPoint {
        Objects.requireNonNull(position, "position");
    }
}
