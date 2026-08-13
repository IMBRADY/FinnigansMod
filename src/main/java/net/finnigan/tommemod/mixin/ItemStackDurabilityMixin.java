package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.item.custom.totems.TotemOfMaintenanceItem;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {

    @ModifyVariable(method = "hurtAndBreak", at = @At("HEAD"), argsOnly = true)
    private int tommemod$reduceDurabilityLoss(int amount, int p_41421_, LivingEntity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide) {
            return amount;
        }

        int reduced = player.getCapability(ModCapabilities.ACCESSORY_HANDLER).map(handler -> {
            ItemStack totemStack = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
            if (totemStack.getItem() instanceof TotemOfMaintenanceItem) {
                return Math.max(1, amount / 2); // -50% durability loss
            }
            return amount;
        }).orElse(amount);

        // Skills waive the whole cost some of the time rather than halving it every time. Stacking
        // with the totem is intentional - a Smithing specialist wearing one should notice.
        if (SkillBonuses.roll(player, ModSkillBonuses.TOOL_DURABILITY_SAVE)) return 0;
        if (player.isFallFlying() && SkillBonuses.roll(player, ModSkillBonuses.ELYTRA_DURABILITY_SAVE)) return 0;

        return reduced;
    }
}