package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Your own pets shrug off your attacks while they're standing - sit one down and you can hit it
 * again, which is how you still get to put an unwanted pet down deliberately.
 *
 * Cancels at LivingAttackEvent rather than LivingHurtEvent so the hit is discarded before knockback,
 * hurt animation and invulnerability frames are applied - a LivingHurtEvent cancel would leave the
 * dog being visibly punched around for zero damage.
 *
 * Keyed on TamableAnimal, not Wolf, so it covers cats and parrots and any modded pet that extends the
 * vanilla class. getOwnerUUID is compared directly instead of resolving getOwner(), which would force
 * the owner entity to be looked up on every hit.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class PetFriendlyFireHandler {

    @SubscribeEvent
    public static void onPetAttacked(LivingAttackEvent event) {
        if (!ModConfig.PET_FRIENDLY_FIRE_PROTECTION.get()) return;
        if (!(event.getEntity() instanceof TamableAnimal pet)) return;
        if (!pet.isTame() || pet.isInSittingPose()) return;

        // getEntity() (not getDirectEntity()) so the pet's owner is still shielded from their own
        // arrows, snowballs and thrown potions, not just their fists.
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;

        UUID ownerId = pet.getOwnerUUID();
        if (ownerId != null && ownerId.equals(player.getUUID())) {
            event.setCanceled(true);
        }
    }
}
