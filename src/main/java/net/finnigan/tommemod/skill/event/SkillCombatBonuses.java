package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the combat and defence trees grant, on both sides of a blow.
 *
 * Outgoing multipliers are gathered and applied once rather than in sequence, so a player with Melee
 * damage, undead damage and a critical bonus gets the sum of the three rather than their product -
 * multiplying them together is how three modest-looking nodes quietly become a fourfold increase.
 *
 * Runs at NORMAL priority, ahead of SkillCombatTracker's LOWEST-priority reading, so the experience a
 * blow earns is measured on the damage it actually did.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillCombatBonuses {

    /** How many blocks of projectile flight one full point of LONG_SHOT_DAMAGE is measured against. */
    private static final double LONG_SHOT_REFERENCE_DISTANCE = 10.0;
    /** A ceiling on the long-shot bonus, so a shot across a render distance isn't a guaranteed kill. */
    private static final double LONG_SHOT_MAX_MULTIPLIER = 1.0;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        applyOutgoing(event);
        applyIncoming(event);
    }

    // ---- Dealing ----

    private static void applyOutgoing(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.equals(event.getEntity())) return;

        Entity direct = event.getSource().getDirectEntity();
        boolean ranged = direct instanceof Projectile;
        boolean unarmed = !ranged && player.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();

        double bonus = 0.0;
        if (ranged) {
            bonus += SkillBonuses.get(player, ModSkillBonuses.RANGED_DAMAGE);
            bonus += longShotBonus(player, direct);
            bonus += headshotBonus(player, event.getEntity(), direct);
        } else if (unarmed) {
            bonus += SkillBonuses.get(player, ModSkillBonuses.UNARMED_DAMAGE);
        } else {
            bonus += SkillBonuses.get(player, ModSkillBonuses.MELEE_DAMAGE);
        }

        if (isUndead(event.getEntity())) {
            bonus += SkillBonuses.get(player, ModSkillBonuses.UNDEAD_DAMAGE);
        }

        if (bonus > 0.0) event.setAmount(event.getAmount() * (float) (1.0 + bonus));

        if (unarmed) applyUnarmedKnockback(player, event.getEntity());
        applyLifesteal(player, event.getAmount(), ranged);
    }

    /**
     * Upgrades ordinary hits into criticals.
     *
     * Handled through CriticalHitEvent rather than by inflating damage afterwards so that everything
     * downstream agrees it was a critical - the particles, the sound, and any other mod watching.
     */
    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();

        if (!event.isVanillaCritical()) {
            double chance = SkillBonuses.get(player, ModSkillBonuses.CRIT_CHANCE);
            if (chance > 0.0 && player.getRandom().nextDouble() < Math.min(chance, 1.0)) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
            }
        }

        double extra = SkillBonuses.get(player, ModSkillBonuses.CRIT_DAMAGE);
        if (extra > 0.0) event.setDamageModifier(event.getDamageModifier() + (float) extra);
    }

    private static double longShotBonus(Player player, @Nullable Entity projectile) {
        double perReference = SkillBonuses.get(player, ModSkillBonuses.LONG_SHOT_DAMAGE);
        if (perReference <= 0.0 || projectile == null) return 0.0;

        double distance = projectile.distanceTo(player);
        return Math.min(perReference * (distance / LONG_SHOT_REFERENCE_DISTANCE), LONG_SHOT_MAX_MULTIPLIER);
    }

    /** A hit above the target's mid-line. Coarse by design - vanilla has no per-part hit detection. */
    private static double headshotBonus(Player player, LivingEntity target, @Nullable Entity projectile) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.HEADSHOT_DAMAGE);
        if (bonus <= 0.0 || projectile == null) return 0.0;

        double midline = target.getY() + target.getBbHeight() * 0.7;
        return projectile.getY() >= midline ? bonus : 0.0;
    }

    private static void applyUnarmedKnockback(Player player, LivingEntity target) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.UNARMED_KNOCKBACK);
        if (bonus <= 0.0) return;

        target.knockback(bonus, player.getX() - target.getX(), player.getZ() - target.getZ());
    }

    private static void applyLifesteal(Player player, float damage, boolean ranged) {
        if (ranged) return; // deliberately melee-only: healing from safety is a different game

        double fraction = SkillBonuses.get(player, ModSkillBonuses.LIFESTEAL);
        if (fraction <= 0.0) return;

        player.heal((float) (damage * fraction));
    }

    private static boolean isUndead(LivingEntity target) {
        return target.getMobType() == MobType.UNDEAD || target.getType().is(EntityTypeTags.SKELETONS);
    }

    // ---- Taking ----

    private static void applyIncoming(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        DamageSource source = event.getSource();
        double reduction = SkillBonuses.get(player, ModSkillBonuses.DAMAGE_REDUCTION);

        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            reduction += SkillBonuses.get(player, ModSkillBonuses.BLAST_RESISTANCE);
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            reduction += SkillBonuses.get(player, ModSkillBonuses.FIRE_RESISTANCE);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            reduction += SkillBonuses.get(player, ModSkillBonuses.PROJECTILE_RESISTANCE);
        }
        if (player.isPassenger()) {
            reduction += SkillBonuses.get(player, ModSkillBonuses.RIDER_DAMAGE_REDUCTION);
        }

        // Capped below total immunity however many nodes stack: a build that cannot be hurt at all is
        // not a build, and every tree here is meant to remain a trade-off.
        reduction = Math.min(reduction, 0.8);
        if (reduction > 0.0) event.setAmount(event.getAmount() * (float) (1.0 - reduction));
    }

    /** A mount's own protection comes from its rider's Riding tree. */
    @SubscribeEvent
    public static void onMountHurt(LivingHurtEvent event) {
        LivingEntity mount = event.getEntity();
        if (mount instanceof Player || !(mount.getControllingPassenger() instanceof Player rider)) return;

        double reduction = SkillBonuses.reduction(rider, ModSkillBonuses.MOUNT_DAMAGE_REDUCTION);
        if (reduction > 0.0) event.setAmount(event.getAmount() * (float) (1.0 - Math.min(reduction, 0.8)));
    }

    /**
     * What a shield does beyond stopping the blow.
     *
     * Vanilla already blocks the full amount, so there is nothing to absorb harder - what a shield
     * user actually loses is durability, and what they lack is any way to punish the attacker. Those
     * are the two things the Defense tree gets to change.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.shieldTakesDamage() && SkillBonuses.roll(player, ModSkillBonuses.SHIELD_DURABILITY_SAVE)) {
            event.setShieldTakesDamage(false);
        }

        double riposte = SkillBonuses.get(player, ModSkillBonuses.RIPOSTE);
        if (riposte > 0.0 && event.getDamageSource().getEntity() instanceof LivingEntity attacker) {
            attacker.hurt(player.damageSources().thorns(player), (float) (event.getBlockedDamage() * riposte));
        }
    }
}
