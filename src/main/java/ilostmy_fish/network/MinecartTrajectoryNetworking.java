package ilostmy_fish.network;

import ilostmy_fish.trajectory.MinecartTrajectory;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Registration and server delivery for authoritative minecart trajectories. */
public final class MinecartTrajectoryNetworking {
    private MinecartTrajectoryNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(
                MinecartTrajectoryPayload.ID,
                MinecartTrajectoryPayload.CODEC
        );
    }

    public static void send(AbstractMinecartEntity minecart, MinecartTrajectory trajectory) {
        MinecartTrajectoryPayload payload = new MinecartTrajectoryPayload(
                minecart.getId(),
                trajectory
        );
        for (ServerPlayerEntity player : PlayerLookup.tracking(minecart)) {
            if (ServerPlayNetworking.canSend(player, MinecartTrajectoryPayload.ID)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
