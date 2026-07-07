package net.finnigan.tommemod.event;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tommemod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FeatherlightHandler {

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player)) return;

        boolean holdingFeatherlight =
                player.getMainHandItem().getItem() == ModItems.FEATHERLIGHT.get()
                        || player.getOffhandItem().getItem() == ModItems.FEATHERLIGHT.get();

        if (holdingFeatherlight) {
            event.setCanceled(true);
        }
    }
}