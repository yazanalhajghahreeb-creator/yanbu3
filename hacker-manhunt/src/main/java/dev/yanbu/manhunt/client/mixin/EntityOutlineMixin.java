package dev.yanbu.manhunt.client.mixin;

import dev.yanbu.manhunt.client.ManhuntClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityOutlineMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void hackermanhunt$localOutlines(CallbackInfoReturnable<Boolean> cir) {
        Entity e = (Entity)(Object)this;
        if (!(e instanceof LivingEntity) || e == MinecraftClient.getInstance().player) return;
        if (ManhuntClient.hasXray()) {
            cir.setReturnValue(true);
        } else if (ManhuntClient.isHunterView()) {
            // Hunters see normal mobs and players, never the mod's old white glow outlines.
            cir.setReturnValue(false);
        }
    }
}
