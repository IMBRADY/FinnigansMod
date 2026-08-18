package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.enchantment.ModEnchantments;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Taking a hit meant for somebody standing next to you.
 *
 * Two things in the mod do this - the Allprot chestplate and the Guardian tree's Aegis and Sentinel -
 * and they are handled together here rather than in a subscriber each. Two subscribers would each see
 * the full incoming damage and each take their cut of it, so a Sentinel wearing Allprot would have
 * absorbed 80% and then 50% of what was left and the ally would have taken a tenth of the hit. One
 * handler picks the single best cover on offer instead, which is the same rule Aegis already follows
 * for shields: a second protector is a spare, not a multiplier.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class AllyDamageShare {

    private AllyDamageShare() {
    }

    /** How far away a protector may stand and still be covering somebody. */
    private static final double SHARE_RADIUS = 6.0;

    /** Allprot takes half of what lands on an ally... */
    private static final double ALLPROT_SHARE = 0.50;
    /** ...and shrugs off four fifths of what it took on their behalf. */
    private static final double ALLPROT_SELF_REDUCTION = 0.80;

    /**
     * Who is currently absorbing on somebody else's behalf.
     *
     * The redirected damage is dealt with {@link Player#hurt}, so it fires this same event again. A
     * protector taking a share and being covered in turn by the ally they were covering is a loop, and
     * two Allprot wearers standing together are enough to find it.
     */
    private static final Set<UUID> ABSORBING = new HashSet<>();

    /** What one protector is offering: how much of the hit they take, and how much of that lands. */
    private record Cover(Player protector, double share, double selfReduction) {
    }

    /**
     * Priority LOW so that the reductions which decide how big the hit is - the Defence tree's own,
     * and anything cancelling the hit outright - have already run. What is shared is the damage the
     * ally would actually have taken, not the number the attacker started with.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (victim.level().isClientSide) return;
        if (event.getAmount() <= 0.0F) return;

        // Damage already being passed to somebody is not passed on again.
        if (ABSORBING.contains(victim.getUUID())) return;

        Cover cover = bestCover(victim, event.getSource());
        if (cover == null) return;

        float shared = (float) (event.getAmount() * cover.share());
        event.setAmount(event.getAmount() - shared);

        float taken = (float) (shared * (1.0 - cover.selfReduction()));
        if (taken <= 0.0F) return;

        // Dealt with the original source, so a protector who dies of it was killed by the thing that
        // swung rather than by nothing at all, and so totems and Last Breath fire as they should.
        ABSORBING.add(cover.protector().getUUID());
        try {
            cover.protector().hurt(event.getSource(), taken);
        } finally {
            ABSORBING.remove(cover.protector().getUUID());
        }
    }

    /** The most cover any one nearby player is offering, or null if nobody is. */
    @Nullable
    private static Cover bestCover(Player victim, DamageSource source) {
        Cover best = null;

        for (Player protector : victim.level().getEntitiesOfClass(Player.class,
                victim.getBoundingBox().inflate(SHARE_RADIUS))) {
            if (protector == victim || !protector.isAlive() || protector.isSpectator()) continue;
            // Somebody cannot take a share of the damage they are themselves dealing.
            if (source.getEntity() == protector) continue;
            if (ABSORBING.contains(protector.getUUID())) continue;

            Cover offered = coverFrom(protector);
            if (offered != null && (best == null || offered.share() > best.share())) {
                best = offered;
            }
        }
        return best;
    }

    /**
     * What this player covers, taking their better source rather than the sum of both.
     *
     * The self-reduction travels with whichever share won, so a Sentinel wearing Allprot absorbs the
     * Sentinel's 80% and feels all of it - the chestplate's mercy is not something they get to keep
     * while using the tree's larger share.
     */
    @Nullable
    private static Cover coverFrom(Player protector) {
        double treeShare = SkillBonuses.get(protector, ModSkillBonuses.ALLY_DAMAGE_SHARE);

        boolean allprot = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ALLPROT.get(), protector.getItemBySlot(EquipmentSlot.CHEST)) > 0;

        if (allprot && ALLPROT_SHARE >= treeShare) {
            return new Cover(protector, ALLPROT_SHARE, ALLPROT_SELF_REDUCTION);
        }
        if (treeShare > 0.0) {
            return new Cover(protector, Math.min(treeShare, 1.0), 0.0);
        }
        return null;
    }
}
