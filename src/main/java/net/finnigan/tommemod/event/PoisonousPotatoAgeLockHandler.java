package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Feed a baby animal a poisonous potato and it stops growing up; feed it another to release it.
 *
 * Vanilla has no age lock, so the flag is stored in the entity's Forge persistent data (which saves
 * with the entity and needs no capability) and the age is pushed back down on a slow tick. setAge is
 * effectively free while the age is already negative - AgeableMob only fires ageBoundaryReached when
 * the value actually crosses zero - so re-applying it costs nothing.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class PoisonousPotatoAgeLockHandler {

    private static final String AGE_LOCK_TAG = "tommemod:AgeLocked";
    /** Deep enough that nothing nudges it back over zero between refreshes. */
    private static final int HELD_AGE = -24000;
    private static final int REFRESH_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onFeedPotato(PlayerInteractEvent.EntityInteract event) {
        if (!ModConfig.POISONOUS_POTATO_AGE_LOCK.get()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof AgeableMob mob)) return;

        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.is(Items.POISONOUS_POTATO)) return;

        // Only ever a baby: locking an adult would do nothing, and swallowing the click there would
        // block whatever the mob's own right-click behaviour is.
        boolean locked = isAgeLocked(mob);
        if (!locked && !mob.isBaby()) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) return;

        if (locked) {
            mob.getPersistentData().remove(AGE_LOCK_TAG);
        } else {
            mob.getPersistentData().putBoolean(AGE_LOCK_TAG, true);
            mob.setAge(HELD_AGE);
        }

        if (!player.getAbilities().instabuild) held.shrink(1);

        mob.level().playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(locked ? ParticleTypes.SMOKE : ParticleTypes.HAPPY_VILLAGER,
                    mob.getX(), mob.getY(0.8D), mob.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    @SubscribeEvent
    public static void onAgeableTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof AgeableMob mob)) return;
        if (mob.level().isClientSide) return;
        if (mob.tickCount % REFRESH_INTERVAL_TICKS != 0) return;
        // Cheap filter before touching persistent data: a locked mob is always a baby, and
        // getPersistentData() allocates (and then saves) an empty ForgeData tag on first call.
        if (!mob.isBaby()) return;
        // Deliberately not gated on the config: an already-locked animal must still be held in place
        // (and stay releasable) if the toggle is turned off later, rather than silently growing up.
        if (!isAgeLocked(mob)) return;

        mob.setAge(HELD_AGE);
    }

    private static boolean isAgeLocked(AgeableMob mob) {
        return mob.getPersistentData().getBoolean(AGE_LOCK_TAG);
    }
}
