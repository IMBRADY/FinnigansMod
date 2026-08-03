package net.finnigan.tommemod.event.CustodireGladioEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.item.custom.CustodireGladioHelpers.ChiefTierResolver;
import net.finnigan.tommemod.item.custom.CustodireGladioItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Custodire Gladio's Chief-reputation passive. Max health rides on an attribute modifier refreshed
 * while the weapon is held; melee damage is scaled at hit time instead, since the weapon's swing
 * damage is what the spec scales, not every source of damage the wielder deals. The ability's own
 * feed/heal rate and damage budget are scaled where they're used, in ShieldWallManager.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class CustodireGladioPassiveHandler {

    private static final UUID MAX_HEALTH_UUID = UUID.fromString("9d3f1c20-6666-4b3f-8a1d-000000000070");

    // Resolving the tier walks the Chief registry and the player's reputation capability, and the
    // answer only changes on reputation milestones, so it deliberately isn't recomputed every tick.
    private static final int RECHECK_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % RECHECK_INTERVAL_TICKS != 0) return;

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        if (!CustodireGladioItem.isHeldBy(player)) {
            if (maxHealth.getModifier(MAX_HEALTH_UUID) != null) {
                maxHealth.removeModifier(MAX_HEALTH_UUID);
            }
            return;
        }

        ReputationTier tier = ChiefTierResolver.bestChiefTier(player);
        stampHeldStacks(player, tier);

        double bonus = ChiefTierResolver.BONUS_PER_TIER * tier.ordinal();
        AttributeModifier existing = maxHealth.getModifier(MAX_HEALTH_UUID);
        if (existing != null && existing.getAmount() == bonus) return;
        if (existing != null) maxHealth.removeModifier(MAX_HEALTH_UUID);

        if (bonus > 0) {
            maxHealth.addTransientModifier(new AttributeModifier(MAX_HEALTH_UUID,
                    "Custodire Gladio chief vitality", bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Puts the resolved tier on whichever hand is holding the weapon, so its tooltip can report it. */
    private static void stampHeldStacks(Player player, ReputationTier tier) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof CustodireGladioItem) {
                CustodireGladioItem.stampChiefTier(stack, tier);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!(player.getMainHandItem().getItem() instanceof CustodireGladioItem)) return;

        event.setAmount((float) (event.getAmount() * ChiefTierResolver.scaleFor(player)));
    }
}
