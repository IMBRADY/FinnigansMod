package net.finnigan.tommemod.event.CandeliereEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.effect.ModMobEffects;
import net.finnigan.tommemod.item.custom.CandeliereItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Candeliere passive: Purifying Light while held (main or offhand) - the same effect Lumapier grants,
 * so immunity (PurifyingLightImmunityHandler) and light emission (LumapierLightHandler) both come
 * along with it for free. Withdrawing the effect is deliberately left to LumapierPassiveHandler, which
 * checks both weapons: two handlers each independently removing it would fight over a player holding
 * the other one.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class CandelierePassiveHandler {

    // Refreshed every tick while held so it never visibly expires; short enough that unequipping lets
    // it fade out promptly (also explicitly removed below for a crisp cutoff).
    private static final int EFFECT_REFRESH_DURATION = 30;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        if (CandeliereItem.isHeldBy(player)) {
            player.addEffect(new MobEffectInstance(ModMobEffects.PURIFYING_LIGHT.get(),
                    EFFECT_REFRESH_DURATION, 0, false, false, false));
        }
    }
}
