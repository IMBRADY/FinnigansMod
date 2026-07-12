package net.finnigan.tommemod.event.SanguisGladioEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.SanguisGladioItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SanguisGladioEvents {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack weapon = player.getMainHandItem();
            if (weapon.getItem() instanceof SanguisGladioItem) {
                SanguisGladioItem.addKill(weapon);
            }
        }
    }
}