package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.item.custom.WitherspineItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class WitherspineItemUseAnimationMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void tommemod$suppressWitherspineFiringBob(InteractionHand hand, CallbackInfo ci) {
        Player player = minecraft.player;
        if (player != null
                && player.getItemInHand(hand).getItem() instanceof WitherspineItem
                && player.getPersistentData().getInt(WitherspineItem.FIRING_TICKS_LEFT_TAG) > 0) {
            ci.cancel();
        }
    }
}
