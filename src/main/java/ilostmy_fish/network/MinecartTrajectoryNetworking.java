package ilostmy_fish.network;

import ilostmy_fish.trajectory.MinecartTrajectory;
import ilostmy_fish.trajectory.TrajectoryStreamPhase;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

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

    /** Finds only tracking players that negotiated support for this payload. */
    public static List<ServerPlayerEntity> trackingRecipients(
            AbstractMinecartEntity minecart
    ) {
        List<ServerPlayerEntity> recipients = new ArrayList<>();
        for (ServerPlayerEntity player : PlayerLookup.tracking(minecart)) {
            if (ServerPlayNetworking.canSend(player, MinecartTrajectoryPayload.ID)) {
                recipients.add(player);
            }
        }
        return recipients;
    }

    public static void send(
            ServerPlayerEntity player,
            AbstractMinecartEntity minecart,
            MinecartTrajectory trajectory,
            TrajectoryStreamPhase phase
    ) {
        MinecartTrajectoryPayload payload = new MinecartTrajectoryPayload(
                minecart.getId(),
                trajectory,
                phase
        );
        ServerPlayNetworking.send(player, payload);
    }
}
