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
        if (ManhuntClient.hasXray() && e instanceof LivingEntity
            && e != MinecraftClient.getInstance().player) {
            cir.setReturnValue(true);
        }
    }
}
