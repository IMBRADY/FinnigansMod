package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.event.SkillDefenseBonuses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ThornsEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * Defense's Bulwark: Thorns stops eating the armor it triggers from.
 *
 * A redirect rather than an event, because the wear is charged inline - {@code doPostHurt} calls
 * {@code hurtAndBreak(2, …)} on whichever piece carried the enchantment, and nothing is fired around
 * it. Skipping that one call is the whole node.
 *
 * Note this is the vanilla Thorns enchantment specifically. The mod's own Riposte bonus is a separate
 * system and never charged durability in the first place.
 */
@Mixin(ThornsEnchantment.class)
public abstract class ThornsDurabilityMixin {

    @Redirect(
            method = "doPostHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"))
    private void tommemod$skillSparesArmorFromThorns(ItemStack stack, int amount, LivingEntity wearer,
                                                     Consumer<LivingEntity> onBroken) {
        if (SkillDefenseBonuses.thornsSparesArmor(wearer)) return;
        stack.hurtAndBreak(amount, wearer, onBroken);
    }
}
