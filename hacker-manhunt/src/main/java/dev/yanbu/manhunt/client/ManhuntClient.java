package dev.yanbu.manhunt.client;

import dev.yanbu.manhunt.HackerManhunt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;

public final class ManhuntClient implements ClientModInitializer {
    private static volatile boolean xrayEnabled;
    private static boolean applied;

    public static boolean hasXray() { return xrayEnabled; }
    public static boolean isOre(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
            || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
            || block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.NETHER_GOLD_ORE
            || block == Blocks.ANCIENT_DEBRIS;
    }

    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HackerManhunt.XrayPayload.ID,
            (payload, context) -> context.client().execute(() -> xrayEnabled = payload.enabled()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) { xrayEnabled = false; applied = false; return; }
            if (xrayEnabled != applied) {
                applied = xrayEnabled;
                // Rebuild client chunk meshes only when the hacker gains/loses X-ray.
                client.worldRenderer.reload();
            }
        });
    }
}
