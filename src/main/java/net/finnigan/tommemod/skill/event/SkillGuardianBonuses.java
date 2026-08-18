package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Guardian tree's two ends: the Aegis, who keeps a party standing, and the Sentinel, who simply
 * refuses to fall.
 *
 * The shared trunk is a shield and a set of armor. What the subclasses add is other people - every key
 * below reaches somebody else, and none of them make the Guardian hit harder. That is the trade the
 * class is: the only way this build wins a fight is by outlasting it on somebody else's behalf.
 *
 * The share of damage a Guardian takes for their party lives in {@code AllyDamageShare}, alongside the
 * Allprot chestplate that does the same thing, so the two can never both take their cut of one blow.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillGuardianBonuses {

    private static final UUID ALLY_KNOCKBACK_MODIFIER = UUID.fromString("5d8f3a02-7c41-4be9-95a7-2f60c1b8e4d3");

    /** How far a Guardian's presence reaches, and how often the auras are paid out. */
    private static final double AURA_RADIUS = 8.0;
    private static final int AURA_INTERVAL_TICKS = 20;

    /** How far in front of a Sentinel an ally has to be to count as sheltering behind them. */
    private static final double SHELTER_DOT = -0.2;

    /** How much of a bond's care goes into cutting bad effects short, per point of the bonus. */
    private static final int BOND_EFFECT_TICKS = 20;

    /** Who each Guardian has bonded to. Cleared when either party leaves. */
    private static final Map<UUID, UUID> BONDS = new HashMap<>();

    /** Guardians currently soaking a fatal blow, so the soak cannot chain into another one. */
    private static final Set<UUID> ABSORBING_FATAL = new HashSet<>();

    // ---- Healing ----

    /**
     * Field Medic, both halves, on one event.
     *
     * Forge fires {@link LivingHealEvent} for the entity being healed and says nothing about who did
     * it, so "healing given" cannot be read off the event - it is paid instead by every Guardian near
     * the patient, which is the same thing in every case the node was written for and is not gameable:
     * a Guardian standing next to somebody eating an apple is doing the job the node describes.
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player patient)) return;
        if (patient.level().isClientSide) return;

        double bonus = SkillBonuses.get(patient, ModSkillBonuses.HEALING_RECEIVED);
        bonus += bestNearbyHealingGiven(patient);

        if (bonus > 0.0) event.setAmount(event.getAmount() * (float) (1.0 + bonus));
    }

    /** The best Field Medic near this patient, not the sum - three medics are a spare, not a stack. */
    private static double bestNearbyHealingGiven(Player patient) {
        double best = 0.0;
        for (Player medic : nearbyPlayers(patient)) {
            if (medic == patient) continue;
            best = Math.max(best, SkillBonuses.get(medic, ModSkillBonuses.HEALING_GIVEN));
        }
        return best;
    }

    // ---- Taking a fatal blow for somebody ----

    /**
     * Last Stand: a blow that would kill an ally is taken by the Guardian instead.
     *
     * At LOWEST, after every reduction the party has - including the Guardian's own damage share - so
     * "would have killed them" means what it says rather than what it would have meant before their
     * armor was counted. Only the blow that actually kills is intercepted; a Guardian who soaked every
     * hit would be {@code ALLY_DAMAGE_SHARE} at 100% under a different name.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAllyHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (victim.level().isClientSide) return;
        if (ABSORBING_FATAL.contains(victim.getUUID())) return;

        // Absorption first, exactly as the real damage pipeline will count it.
        float lethal = event.getAmount() - victim.getAbsorptionAmount();
        if (lethal < victim.getHealth()) return;

        Player guardian = nearestFatalAbsorber(victim);
        if (guardian == null) return;

        event.setCanceled(true);

        ABSORBING_FATAL.add(guardian.getUUID());
        try {
            guardian.hurt(event.getSource(), event.getAmount());
        } finally {
            ABSORBING_FATAL.remove(guardian.getUUID());
        }

        guardian.level().playSound(null, guardian.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        victim.displayClientMessage(Component.literal(guardian.getName().getString() + " took that one.")
                .withStyle(ChatFormatting.GOLD), true);
    }

    @Nullable
    private static Player nearestFatalAbsorber(Player victim) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player guardian : nearbyPlayers(victim)) {
            if (guardian == victim || !guardian.isAlive() || guardian.isSpectator()) continue;
            if (ABSORBING_FATAL.contains(guardian.getUUID())) continue;
            if (!SkillBonuses.has(guardian, ModSkillBonuses.ALLY_FATAL_ABSORB)) continue;

            double distance = guardian.distanceToSqr(victim);
            if (distance < bestDistance) {
                best = guardian;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---- The auras ----

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % AURA_INTERVAL_TICKS != 0) return;

        for (Player guardian : level.players()) {
            paySanctuary(guardian);
            payBond(guardian);
        }

        // Applied to the sheltered rather than granted by the sheltering, because it is an attribute on
        // whoever is standing there and has to come off again the moment they move out.
        for (Player sheltered : level.players()) {
            applyAllyKnockback(sheltered, bestShelterFor(sheltered));
        }
    }

    /** Sanctuary: a slow, steady trickle to everyone nearby. */
    private static void paySanctuary(Player guardian) {
        double perSecond = SkillBonuses.get(guardian, ModSkillBonuses.ALLY_REGEN);
        if (perSecond <= 0.0) return;

        for (Player ally : nearbyPlayers(guardian)) {
            if (ally == guardian || !ally.isAlive()) continue;
            if (ally.getHealth() >= ally.getMaxHealth()) continue;
            ally.heal((float) perSecond);
        }
    }

    /**
     * Bonded: one ally gets the Guardian's whole attention.
     *
     * Extra healing and time cut off whatever is wrong with them. Deliberately one person - spread
     * across a party this would be Sanctuary with a larger number, and the node is meant to be a
     * choice about who is worth keeping alive.
     */
    private static void payBond(Player guardian) {
        double care = SkillBonuses.get(guardian, ModSkillBonuses.BONDED_ALLY);
        if (care <= 0.0) return;

        Player bonded = bondedAlly(guardian);
        if (bonded == null) return;

        if (bonded.getHealth() < bonded.getMaxHealth()) bonded.heal((float) care);

        int cut = (int) Math.round(BOND_EFFECT_TICKS * care);
        if (cut <= 0) return;

        // Copied out first: shortening an effect replaces the instance, which would otherwise be a
        // modification of the collection being walked.
        for (MobEffectInstance effect : new ArrayList<>(bonded.getActiveEffects())) {
            if (effect.getEffect().isBeneficial() || effect.getDuration() <= cut) continue;

            bonded.removeEffectNoUpdate(effect.getEffect());
            bonded.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration() - cut,
                    effect.getAmplifier(), effect.isAmbient(), effect.isVisible()));
        }
    }

    /** The best Immovable near this player, counting only Guardians they are actually behind. */
    private static double bestShelterFor(Player sheltered) {
        double best = 0.0;

        for (Player guardian : nearbyPlayers(sheltered)) {
            if (guardian == sheltered) continue;

            double aura = SkillBonuses.get(guardian, ModSkillBonuses.ALLY_KNOCKBACK_AURA);
            if (aura <= 0.0) continue;

            Vec3 toSheltered = sheltered.position().subtract(guardian.position());
            if (toSheltered.lengthSqr() < 1.0E-4) continue;
            if (guardian.getLookAngle().dot(toSheltered.normalize()) > SHELTER_DOT) continue;

            best = Math.max(best, aura);
        }
        return best;
    }

    private static void applyAllyKnockback(Player sheltered, double amount) {
        AttributeInstance resistance = sheltered.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance == null) return;

        AttributeModifier existing = resistance.getModifier(ALLY_KNOCKBACK_MODIFIER);
        if (amount <= 0.0) {
            if (existing != null) resistance.removeModifier(ALLY_KNOCKBACK_MODIFIER);
            return;
        }
        if (existing != null && existing.getAmount() == amount) return;

        if (existing != null) resistance.removeModifier(ALLY_KNOCKBACK_MODIFIER);
        resistance.addTransientModifier(new AttributeModifier(ALLY_KNOCKBACK_MODIFIER,
                "Skill guardian shelter", amount, AttributeModifier.Operation.ADDITION));
    }

    // ---- Choosing who to bond to ----

    /**
     * Bonds the Guardian to whoever they are looking at.
     *
     * Returns false when the node is unowned or nobody is in view, so the caller can say why nothing
     * happened. Pointing at nobody breaks an existing bond, which is how a Guardian drops one without
     * needing a second key for it.
     */
    public static boolean tryBond(Player guardian) {
        if (SkillBonuses.get(guardian, ModSkillBonuses.BONDED_ALLY) <= 0.0) return false;

        Player looked = lookedAtAlly(guardian);
        if (looked == null) {
            if (BONDS.remove(guardian.getUUID()) == null) return false;

            guardian.displayClientMessage(Component.literal("Bond released.")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }

        BONDS.put(guardian.getUUID(), looked.getUUID());
        guardian.displayClientMessage(Component.literal("Bonded to " + looked.getName().getString())
                .withStyle(ChatFormatting.GOLD), true);
        guardian.level().playSound(null, guardian.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.4F, 1.6F);
        return true;
    }

    @Nullable
    private static Player lookedAtAlly(Player guardian) {
        Vec3 look = guardian.getLookAngle();

        Player best = null;
        double bestDot = 0.94; // roughly a twenty-degree cone, so a bond is aimed rather than swept up

        for (Player candidate : nearbyPlayers(guardian)) {
            if (candidate == guardian || !candidate.isAlive()) continue;

            Vec3 toward = candidate.getEyePosition().subtract(guardian.getEyePosition());
            if (toward.lengthSqr() < 1.0E-4) continue;

            double dot = look.dot(toward.normalize());
            if (dot > bestDot) {
                best = candidate;
                bestDot = dot;
            }
        }
        return best;
    }

    @Nullable
    private static Player bondedAlly(Player guardian) {
        UUID bondedId = BONDS.get(guardian.getUUID());
        if (bondedId == null) return null;

        Player bonded = guardian.level().getPlayerByUUID(bondedId);
        // Out of range or gone: the bond is kept rather than dropped, so walking apart and back together
        // does not cost the Guardian a keypress.
        return bonded != null && bonded.isAlive()
                && bonded.distanceToSqr(guardian) <= AURA_RADIUS * AURA_RADIUS ? bonded : null;
    }

    private static List<Player> nearbyPlayers(Player around) {
        return around.level().getEntitiesOfClass(Player.class,
                around.getBoundingBox().inflate(AURA_RADIUS));
    }
}
