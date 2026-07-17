package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void tommemod$accessoryTotemProtection(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        boolean handHasTotem = self.getItemInHand(InteractionHand.MAIN_HAND).is(Items.TOTEM_OF_UNDYING)
                || self.getItemInHand(InteractionHand.OFF_HAND).is(Items.TOTEM_OF_UNDYING);
        if (handHasTotem) return; // let vanilla's own hand check run/consume normally

        self.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(h -> {
            ItemStack totem = h.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totem.is(Items.TOTEM_OF_UNDYING)
                    && net.minecraftforge.common.ForgeHooks.onLivingUseTotem(self, source, totem, InteractionHand.MAIN_HAND)) {

                h.extractItem(AccessoryHandler.SLOT_TOTEM_ACCESSORY, 1, false);

                if (self instanceof ServerPlayer serverplayer) {
                    serverplayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING), 1);
                    CriteriaTriggers.USED_TOTEM.trigger(serverplayer, totem);
                }

                self.setHealth(1.0F);
                self.removeAllEffects();
                self.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                self.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                self.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                self.level().broadcastEntityEvent(self, (byte) 35);

                cir.setReturnValue(true);
                cir.cancel();
            }
        });
    }
}