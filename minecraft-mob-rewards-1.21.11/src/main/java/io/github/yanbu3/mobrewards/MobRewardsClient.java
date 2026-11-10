package io.github.yanbu3.mobrewards;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side climb prediction. Server tick alone cannot make movement feel like
 * climbing, because vanilla client movement continually overwrites the velocity.
 */
public final class MobRewardsClient implements ClientModInitializer {
    private static boolean canClimb = false;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpiderClimbPayload.TYPE, (payload, context) -> {
            canClimb = payload.unlocked();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> canClimb = false);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null || !canClimb || player.isShiftKeyDown() ||
                player.getAbilities().flying || !client.options.keyUp.isDown() ||
                !player.horizontalCollision) return;

            // Holding forward into a wall is enough, including standing on ground.
            Vec3 movement = player.getDeltaMovement();
            if (movement.y < 0.28D) {
                player.setDeltaMovement(movement.x, 0.28D, movement.z);
            }
            player.resetFallDistance();
        });
    }
}
