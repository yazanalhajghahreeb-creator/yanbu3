package dev.yanbu.manhunt.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.yanbu.manhunt.HackerManhunt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ManhuntClient implements ClientModInitializer {
    private static boolean xrayEnabled;
    private static UUID authorizedHacker;
    private static int scanTick;
    private static final List<BlockPos> nearbyOres = new ArrayList<>();
    
    // Only enable custom rendering on the exact client assigned the hacker role.
    public static boolean hasXray() {
        var client = MinecraftClient.getInstance();
        return xrayEnabled && client.player != null && authorizedHacker != null
            && authorizedHacker.equals(client.player.getUuid());
    }
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
    private static int oreTint(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return 0x00EFFF;
        if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) return 0x22EE55;
        if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) return 0xFF3333;
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE) return 0xFFCE22;
        if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE) return 0x3377FF;
        if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) return 0xFFAA66;
        if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE) return 0xDDDDDD;
        return 0xFFFFFF;
    }
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HackerManhunt.XrayPayload.ID,
            (payload, context) -> context.client().execute(() -> {
                var client = MinecraftClient.getInstance();
                UUID claimed = null;
                try { claimed = UUID.fromString(payload.authorizedHacker()); }
                catch (IllegalArgumentException ignored) {}
                authorizedHacker = claimed;
                xrayEnabled = payload.enabled() && client.player != null && claimed != null
                    && claimed.equals(client.player.getUuid());
                if (!xrayEnabled) nearbyOres.clear();
            }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                xrayEnabled = false; authorizedHacker = null; nearbyOres.clear();
                return;
            }
            if (!hasXray()) { nearbyOres.clear(); return; }
            if (++scanTick < 12) return;
            scanTick = 0;
            nearbyOres.clear();
            BlockPos origin = client.player.getBlockPos();
            // Nearby loaded blocks only. Never create server-world markers visible to hunters.
            for (BlockPos p : BlockPos.iterate(origin.add(-13, -11, -13), origin.add(13, 11, 13))) {
                if (nearbyOres.size() >= 256) break;
                if (isOre(client.world.getBlockState(p))) nearbyOres.add(p.toImmutable());
            }
        });
        WorldRenderEvents.LAST.register(ctx -> {
            if (!hasXray() || nearbyOres.isEmpty() || ctx.matrixStack() == null) return;
            MatrixStack matrices = ctx.matrixStack();
            var camera = ctx.camera().getPos();
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
            RenderSystem.lineWidth(2.0F);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            for (BlockPos pos : nearbyOres) {
                float x = pos.getX(), y = pos.getY(), z = pos.getZ();
                int rgb = oreTint(MinecraftClient.getInstance().world.getBlockState(pos));
                for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
                    drawLine(buffer, matrices, x, y + a, z + b, x + 1, y + a, z + b, rgb);
                    drawLine(buffer, matrices, x + a, y, z + b, x + a, y + 1, z + b, rgb);
                    drawLine(buffer, matrices, x + a, y + b, z, x + a, y + b, z + 1, rgb);
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            matrices.pop();
        });
    }

    private static void drawLine(BufferBuilder builder, MatrixStack stack,
                                 float x1, float y1, float z1, float x2, float y2, float z2, int rgb) {
        var matrix = stack.peek();
        int red = (rgb >> 16) & 255, green = (rgb >> 8) & 255, blue = rgb & 255;
        Vector3f direction = new Vector3f(x2-x1, y2-y1, z2-z1).normalize();
        builder.vertex(matrix.getPositionMatrix(), x1, y1, z1).color(red,green,blue,255)
            .normal(matrix, direction.x, direction.y, direction.z);
        builder.vertex(matrix.getPositionMatrix(), x2, y2, z2).color(255,255,255,255)
            .normal(matrix, direction.x, direction.y, direction.z);
    }
}
