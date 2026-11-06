package dev.yanbu.manhunt.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class CompassHandMixin {
    @Shadow private float equipProgressMainHand;
    @Shadow private float prevEquipProgressMainHand;
    @Shadow private float equipProgressOffHand;
    @Shadow private float prevEquipProgressOffHand;

    @Inject(method = "updateHeldItems", at = @At("TAIL"))
    private void hackermanhunt$preventCompassReequip(CallbackInfo ci) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (player.getMainHandStack().isOf(Items.COMPASS)) {
            equipProgressMainHand = 1.0F;
            prevEquipProgressMainHand = 1.0F;
        }
        if (player.getOffHandStack().isOf(Items.COMPASS)) {
            equipProgressOffHand = 1.0F;
            prevEquipProgressOffHand = 1.0F;
        }
    }
}
