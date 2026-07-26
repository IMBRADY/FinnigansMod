package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.BeeWingsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class BeeWingsEvents {

    // Bee Wings glide like an Elytra but shouldn't get the firework-rocket
    // boost — block the boost-triggering use specifically while wearing them.
    @SubscribeEvent
    public static void onFireworkBoostAttempt(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!player.isFallFlying()) return;
        if (!(event.getItemStack().getItem() instanceof FireworkRocketItem)) return;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof BeeWingsItem) {
            event.setCanceled(true);
        }
    }
}
