package ilostmy_fish.client;

import ilostmy_fish.client.trajectory.ClientTrajectoryManager;
import ilostmy_fish.network.MinecartTrajectoryPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client registration for receiving and playing authoritative trajectories. */
public final class MinecartSpeedFeaturesClient implements ClientModInitializer {
    @Override
    @SuppressWarnings("resource")
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                MinecartTrajectoryPayload.ID,
                (payload, context) -> ClientTrajectoryManager.INSTANCE.receive(
                        context.client().world,
                        payload
                )
        );
        // Select before ClientWorld.tickEntity snapshots prev/lastRender positions. Advancing at
        // END_WORLD_TICK would make rendering sample one trajectory later than the entity state.
        ClientTickEvents.START_WORLD_TICK.register(ClientTrajectoryManager.INSTANCE::beginTick);
        ClientEntityEvents.ENTITY_UNLOAD.register(
                (entity, world) -> ClientTrajectoryManager.INSTANCE.remove(entity.getId())
        );
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientTrajectoryManager.INSTANCE.clear()
        );
    }
}
