package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.item.custom.totems.TotemOfInvisibilityItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Totem of Invisibility: 4 seconds of continuous wear before it takes hold, gone the tick it comes
 * off. Runs every tick (rather than riding TotemEffectEvents' 10-tick sweep) because both halves of
 * that need finer resolution than 10 ticks - the warm-up is what stops the totem being spammed on
 * and off as a free blink, and it would be worthless if removal lagged half a second behind.
 *
 * Only ever revokes invisibility this handler granted: the totem's instance is ambient with a short
 * duration, so a potion the player drank (non-ambient, long) is left alone even if it happened to be
 * running while the totem was worn.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class InvisibilityTotemHandler {

    /** Continuous wear required before the effect starts, in ticks. */
    private static final int WARMUP_TICKS = 80; // 4 seconds

    /** Granted in short slices, so an instance left behind by the last tick before the totem came
     * off can never outlive the removal below by more than a moment. */
    private static final int EFFECT_DURATION_TICKS = 40;

    /** Only topped back up once it has nearly run out - re-adding an identical effect every tick
     * would send the client an effect update packet every tick along with it. */
    private static final int REFRESH_BELOW_TICKS = 20;

    private static final Map<UUID, Integer> wearTicks = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        if (!hasTotem(player)) {
            if (wearTicks.remove(player.getUUID()) != null) {
                revokeGrantedInvisibility(player);
            }
            return;
        }

        int worn = wearTicks.merge(player.getUUID(), 1, Integer::sum);
        if (worn < WARMUP_TICKS) return;

        MobEffectInstance active = player.getEffect(MobEffects.INVISIBILITY);
        // A potion the player drank (non-ambient, long) is left to run on its own terms; the totem
        // picks the effect back up once it lapses.
        if (active == null || (active.isAmbient() && active.getDuration() <= REFRESH_BELOW_TICKS)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_DURATION_TICKS, 0, true, false));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        wearTicks.remove(event.getEntity().getUUID());
    }

    private static void revokeGrantedInvisibility(Player player) {
        MobEffectInstance active = player.getEffect(MobEffects.INVISIBILITY);
        if (active == null) return;
        // Ours: ambient, and never longer than the window we refresh it with. Anything else (a
        // potion, a beacon) is somebody else's effect and is left running.
        if (active.isAmbient() && active.getDuration() <= EFFECT_DURATION_TICKS) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    private static boolean hasTotem(Player player) {
        return player.getCapability(ModCapabilities.ACCESSORY_HANDLER)
                .map(handler -> {
                    ItemStack totem = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
                    return totem.getItem() instanceof TotemOfInvisibilityItem;
                })
                .orElse(false);
    }
}
