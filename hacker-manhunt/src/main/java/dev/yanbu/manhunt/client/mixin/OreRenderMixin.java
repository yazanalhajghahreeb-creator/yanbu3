package dev.yanbu.manhunt.client.mixin;

import dev.yanbu.manhunt.client.ManhuntClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public abstract class OreRenderMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void hackermanhunt$hideOtherBlocks(BlockState state, BlockPos pos, BlockRenderView world,
            MatrixStack matrices, VertexConsumer vertices, boolean cull, Random random, CallbackInfo ci) {
        if (ManhuntClient.hasXray() && !ManhuntClient.isOre(state)) ci.cancel();
    }

    @ModifyVariable(method = "renderBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean hackermanhunt$showEveryOreFace(boolean cull, BlockState state, BlockPos pos,
            BlockRenderView world, MatrixStack matrices, VertexConsumer vertices) {
        return ManhuntClient.hasXray() && ManhuntClient.isOre(state) ? false : cull;
    }
}
