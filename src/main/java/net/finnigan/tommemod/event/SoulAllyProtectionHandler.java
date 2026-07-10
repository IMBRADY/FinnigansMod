package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SoulAllyProtectionHandler {

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;

        if (attacker.getUUID().equals(target.getPersistentData().getUUID("tommemod_soul_owner"))) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;

        if (attacker.getUUID().equals(target.getPersistentData().getUUID("tommemod_soul_owner"))) {
            event.setCanceled(true);
        }
    }
}