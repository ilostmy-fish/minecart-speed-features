package ilostmy_fish.client.trajectory;

import ilostmy_fish.trajectory.TrajectorySample;
import ilostmy_fish.trajectory.TrajectoryTransforms;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Shared trajectory sampling for the cart renderer, passengers, and focused camera. */
public final class TrajectoryRenderHooks {
    private static final Vec3d ZERO = Vec3d.ZERO;

    private TrajectoryRenderHooks() {
    }

    @Nullable
    public static TrajectorySample minecartSample(
            AbstractMinecartEntity minecart,
            float tickDelta
    ) {
        return ClientTrajectoryManager.INSTANCE.sample(minecart, tickDelta);
    }

    public static Vec3d renderOffset(Entity entity, float tickDelta) {
        return mountedOffset(entity, tickDelta);
    }

    /** Translation from the logical end-of-tick hitbox to the sampled render position. */
    public static Vec3d visibilityOffset(Entity entity, float tickDelta) {
        AbstractMinecartEntity minecart = findMinecart(entity);
        if (minecart == null) {
            return ZERO;
        }
        TrajectorySample sample = minecartSample(minecart, tickDelta);
        if (sample == null) {
            return ZERO;
        }
        return TrajectoryTransforms.visibilityOffset(
                sample.position(),
                minecart.getPos()
        );
    }

    public static Vec3d cameraOffset(Entity focusedEntity, float tickDelta) {
        return mountedOffset(focusedEntity, tickDelta);
    }

    public static boolean isNonZero(Vec3d value) {
        return value.lengthSquared() >= 1.0E-18;
    }

    private static Vec3d mountedOffset(Entity entity, float tickDelta) {
        AbstractMinecartEntity minecart = findMinecart(entity);
        if (minecart == null) {
            return ZERO;
        }
        TrajectorySample sample = minecartSample(minecart, tickDelta);
        if (sample == null) {
            return ZERO;
        }

        // A passenger has its own prev/current interpolation state, which is not guaranteed to
        // be identical to the vehicle's because ClientWorld snapshots passengers after ticking
        // their vehicle. Place this entity at its endpoint-relative attachment on the sampled
        // cart position instead of assuming one minecart-relative correction fits every rider.
        return TrajectoryTransforms.mountedRenderOffset(
                sample.position(),
                minecart.getPos(),
                entity.getPos(),
                entity.getLerpedPos(tickDelta)
        );
    }

    @Nullable
    private static AbstractMinecartEntity findMinecart(Entity entity) {
        Entity current = entity;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof AbstractMinecartEntity minecart) {
                return minecart;
            }
            Entity next = current.getVehicle();
            if (next == current) {
                break;
            }
            current = next;
        }
        return null;
    }
}
