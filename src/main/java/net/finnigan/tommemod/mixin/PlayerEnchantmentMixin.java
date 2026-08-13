package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.event.SkillCraftingTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts the Smithing skill's enchanting action.
 *
 * A mixin rather than an event because Forge has none that fits: EnchantmentLevelSetEvent fires while
 * the table is deciding what to *offer*, several times per open and long before anything is bought,
 * so paying out on it would reward standing next to a table. onEnchantmentPerformed is called exactly
 * once, at the moment the player commits, and is handed both the item and what it cost.
 */
@Mixin(Player.class)
public abstract class PlayerEnchantmentMixin {

    @Inject(method = "onEnchantmentPerformed", at = @At("HEAD"))
    private void tommemod$awardEnchantingSkill(ItemStack enchanted, int levelCost, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            SkillCraftingTracker.onEnchanted(player, enchanted, levelCost);
        }
    }
}
