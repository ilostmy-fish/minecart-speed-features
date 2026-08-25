package ilostmy_fish.client.trajectory;

import ilostmy_fish.network.MinecartTrajectoryPayload;
import ilostmy_fish.trajectory.TrajectoryPlayback;
import ilostmy_fish.trajectory.TrajectorySample;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Owns per-entity trajectory buffers for the current client world. */
public final class ClientTrajectoryManager {
    public static final ClientTrajectoryManager INSTANCE = new ClientTrajectoryManager();

    private final Map<Integer, TrajectoryPlayback> playbacks = new HashMap<>();
    private ClientWorld world;

    private ClientTrajectoryManager() {
    }

    public void receive(@Nullable ClientWorld packetWorld, MinecartTrajectoryPayload payload) {
        if (packetWorld == null) {
            return;
        }
        this.useWorld(packetWorld);
        if (!(packetWorld.getEntityById(payload.entityId()) instanceof AbstractMinecartEntity)) {
            return;
        }
        this.playbacks.computeIfAbsent(payload.entityId(), ignored -> new TrajectoryPlayback())
                .accept(payload.trajectory(), payload.phase());
    }

    /** Selects the trajectory that the upcoming entity tick and render interval will share. */
    public void beginTick(ClientWorld currentWorld) {
        this.useWorld(currentWorld);
        for (Map.Entry<Integer, TrajectoryPlayback> entry : this.playbacks.entrySet()) {
            Entity entity = currentWorld.getEntityById(entry.getKey());
            if (!(entity instanceof AbstractMinecartEntity minecart)) {
                continue;
            }

            TrajectoryPlayback playback = entry.getValue();
            playback.advance();
            this.applyTickStart(minecart, playback.sample(0.0F));
        }
    }

    public boolean isActive(AbstractMinecartEntity minecart) {
        if (minecart.getWorld() != this.world) {
            return false;
        }
        TrajectoryPlayback playback = this.playbacks.get(minecart.getId());
        return playback != null && playback.hasCurrent();
    }

    /** Applies the logical endpoint after vanilla has ticked the client minecart. */
    public void completeTick(AbstractMinecartEntity minecart) {
        if (minecart.getWorld() != this.world) {
            return;
        }
        TrajectoryPlayback playback = this.playbacks.get(minecart.getId());
        if (playback == null) {
            return;
        }

        TrajectorySample sample = playback.sample(1.0F);
        if (sample == null) {
            return;
        }
        // Do not move passengers here. ClientWorld.tickPassenger runs immediately after the
        // vehicle tick: it must snapshot the passenger at the trajectory start before vanilla
        // moves that passenger to this endpoint. Moving it now produces endpoint-to-endpoint
        // camera interpolation while the cart still interpolates start-to-end.
        minecart.setPosition(sample.position());
        Vec3d finalVelocity = playback.currentFinalVelocity();
        if (finalVelocity != null) {
            minecart.setVelocity(finalVelocity);
        }
    }

    @Nullable
    public TrajectorySample sample(AbstractMinecartEntity minecart, float tickDelta) {
        if (minecart.getWorld() != this.world) {
            return null;
        }
        TrajectoryPlayback playback = this.playbacks.get(minecart.getId());
        return playback == null ? null : playback.sample(tickDelta);
    }

    public void remove(int entityId) {
        this.playbacks.remove(entityId);
    }

    public void clear() {
        this.playbacks.clear();
        this.world = null;
    }

    private void useWorld(ClientWorld currentWorld) {
        if (this.world != currentWorld) {
            this.playbacks.clear();
            this.world = currentWorld;
        }
    }

    private void applyTickStart(
            AbstractMinecartEntity minecart,
            @Nullable TrajectorySample sample
    ) {
        if (sample == null) {
            return;
        }

        minecart.setPosition(sample.position());
        for (Entity passenger : minecart.getPassengerList()) {
            minecart.updatePassengerPosition(passenger);
        }
    }
}
