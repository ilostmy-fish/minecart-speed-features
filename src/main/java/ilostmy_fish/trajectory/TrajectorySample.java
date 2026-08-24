package ilostmy_fish.trajectory;

import net.minecraft.util.math.Vec3d;

/** A render position and forward direction sampled from the same authoritative trajectory. */
public record TrajectorySample(Vec3d position, Vec3d tangent) {
}
