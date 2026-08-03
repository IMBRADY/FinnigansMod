package net.finnigan.tommemod.event.CandeliereEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.CandeliereHelpers.CandeliereBurnTracker;
import net.finnigan.tommemod.item.custom.CandeliereItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Candeliere's two melee passives, both keyed off a swing landing while the weapon is in the main hand:
 * a burning target takes 20% more damage from it, and the hit stokes an ability-lit fire by one 10%
 * step (see CandeliereBurnTracker for the +50% ceiling and reset rule).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class CandeliereCombatHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!(player.getMainHandItem().getItem() instanceof CandeliereItem)) return;

        LivingEntity target = event.getEntity();
        if (target.isOnFire()) {
            event.setAmount(event.getAmount() * CandeliereItem.BURNING_TARGET_DAMAGE_MULTIPLIER);
        }

        CandeliereBurnTracker.extendOnMelee(target);
    }
}
