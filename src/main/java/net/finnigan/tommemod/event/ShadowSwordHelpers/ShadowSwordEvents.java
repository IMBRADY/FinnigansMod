package net.finnigan.tommemod.event.ShadowSwordHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.ShadowSwordItem;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wires the Shadow Sword into the world: kills feed it souls, and every server tick of a player
 * hands off to {@link ShadowSoulManager} for the passive, the orbit and any volley still launching.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ShadowSwordEvents {

    /** The sword only works from the main hand - the swing and the throw both come from it. */
    public static boolean isWieldingShadowSword(Player player) {
        return player.getMainHandItem().getItem() instanceof ShadowSwordItem;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        ShadowSoulManager.tickPlayer(event.player);
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (killer.level().isClientSide) return;
        if (event.getEntity() == killer) return;
        if (!isWieldingShadowSword(killer)) return;

        ShadowSoulManager.addSoul(killer);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ShadowSoulManager.forget(event.getEntity());
    }
}
