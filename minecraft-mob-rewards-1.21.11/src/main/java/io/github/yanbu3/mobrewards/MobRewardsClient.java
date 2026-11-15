package io.github.yanbu3.mobrewards;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Spider climbing is both client-predicted and assisted by the server.
 *
 * The old release relied solely on the player's horizontalCollision boolean
 * and a velocity change AFTER the movement tick. Some clients never entered
 * a climb, so this implementation detects a wall ahead and applies both
 * upward velocity BEFORE movement and a small collision-checked positional
 * step AFTER movement. No input binding or command is required.
 */
public final class MobRewardsClient implements ClientModInitializer {
    private static volatile boolean spiderUnlocked = false;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpiderClimbPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                boolean previouslyUnlocked = spiderUnlocked;
                spiderUnlocked = payload.unlocked();
                LocalPlayer player = context.client().player;
                if (player != null && spiderUnlocked && !previouslyUnlocked) {
                    player.displayClientMessage(
                        Component.literal("[Hensley Mode] Spider Climb active! Hold W against a wall."), true);
                }
            })
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> spiderUnlocked = false);

        // Set the vertical speed before LocalPlayer's normal movement tick.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (!canClimb(client, player)) return;
            Vec3 speed = player.getDeltaMovement();
            if (speed.y < 0.16) {
                player.setDeltaMovement(speed.x, 0.16, speed.z);
            }
            player.resetFallDistance();
        });

        // Add a small, checked positional step to start climbing even when
        // vanilla ground friction otherwise cancels the first upward movement.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (!canClimb(client, player)) return;
            double climbStep = 0.07;
            if (player.level().noCollision(player, player.getBoundingBox().move(0, climbStep, 0))) {
                player.setPos(player.getX(), player.getY() + climbStep, player.getZ());
                player.resetFallDistance();
            }
        });
    }

    private static boolean canClimb(Minecraft client, LocalPlayer player) {
        if (!spiderUnlocked || player == null || player.isShiftKeyDown()
                || player.getAbilities().flying || !client.options.keyUp.isDown()) return false;
        if (player.horizontalCollision) return true;

        // Inspect the block ahead at chest height; do not require the player to jump.
        Vec3 direction = player.getLookAngle();
        double horizontalLength = Math.hypot(direction.x, direction.z);
        if (horizontalLength < 0.001) return false;
        Vec3 target = player.position().add(
                direction.x / horizontalLength * 0.75, 0.85,
                direction.z / horizontalLength * 0.75);
        BlockPos wall = BlockPos.containing(target);
        return player.level().getBlockState(wall).isSolid();
    }
}
