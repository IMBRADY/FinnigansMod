package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.LivingGuardEntity;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The only way a Living Guard comes into existence: right-click an iron golem with an Ancient Armor
 * Fragment and the golem is rebuilt as one, consuming the fragment.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class AncientArmorFragmentEvents {

    @SubscribeEvent
    public static void onFragmentUsedOnGolem(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;

        ItemStack held = event.getItemStack();
        if (!held.is(ModItems.ANCIENT_ARMOR_FRAGMENT.get())) return;

        // A Living Guard is itself an IronGolem, so exclude it or fragments would be eaten for nothing.
        if (!(event.getTarget() instanceof IronGolem golem) || golem instanceof LivingGuardEntity) return;

        LivingGuardEntity guard = golem.convertTo(ModEntityTypes.LIVING_GUARD.get(), false);
        if (guard == null) return;

        guard.setPlayerCreated(golem.isPlayerCreated());
        guard.setHealth(guard.getMaxHealth());
        ForgeEventFactory.onLivingConvert(golem, guard);

        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.swing(event.getHand(), true);

        guard.level().playSound(null, guard.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (guard.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    guard.getX(), guard.getY(0.5D), guard.getZ(), 40, 0.6D, 0.8D, 0.6D, 0.1D);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
