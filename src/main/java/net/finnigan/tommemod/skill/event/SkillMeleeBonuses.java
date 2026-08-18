package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Melee tree's own behaviour, beyond the damage and crit numbers it shares with everything else.
 *
 * The theme is that a fighter's advantage lives in the exchange rather than in the weapon: who saw whom
 * first, how many blows have landed in a row, how hurt each party is, how many of them there are. Not
 * one of these is another percent of attack damage, which is what most of the tree used to be.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillMeleeBonuses {

    private static final UUID KILL_MOMENTUM_MODIFIER = UUID.fromString("f4b2e916-3d07-4a58-91c6-8e250bd73a4f");

    /** How long a combo survives without landing another hit on the same target. */
    private static final int COMBO_TIMEOUT_TICKS = 40;
    private static final int COMBO_CAP = 3;
    /** Below this share of its health, a target is finishable. */
    private static final double EXECUTE_THRESHOLD = 0.33;
    private static final int KILL_MOMENTUM_TICKS = 100;
    /** How close a hostile has to be to count toward being outnumbered, and how many it takes. */
    private static final double OUTNUMBERED_RADIUS = 5.0;
    private static final int OUTNUMBERED_THRESHOLD = 3;
    /** Most of the damage taken that being outnumbered may remove, however many nodes stack. */
    private static final double OUTNUMBERED_DEFENCE_CAP = 0.5;
    /** How long a bleed runs, and how often it bites. */
    private static final int BLEED_TICKS = 60;
    private static final int BLEED_INTERVAL_TICKS = 20;
    /** How far a cleave reaches past the target that was actually struck. */
    private static final double CLEAVE_RADIUS = 2.5;

    private record Combo(UUID target, int hits, long lastHitTick) {
    }

    private record Bleed(double perSecond, long endsAtTick) {
    }

    private static final Map<UUID, Combo> COMBOS = new HashMap<>();
    private static final Map<UUID, Bleed> BLEEDS = new HashMap<>();
    private static final Map<UUID, Long> KILL_MOMENTUM_UNTIL = new HashMap<>();
    /** Players currently mid-cleave, so the cleave's own damage cannot cleave again. */
    private static final Set<UUID> CLEAVING = new HashSet<>();

    // ---- Dealing damage ----

    /**
     * Everything that scales a blow this player is landing.
     *
     * Runs at LOW so the shared flat keys in {@link SkillCombatBonuses} have already been applied -
     * these are multipliers on the real number, and multiplying a pre-bonus figure would make every one
     * of them quietly weaker than it reads on the node.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.equals(event.getEntity())) return;
        // Melee only. A projectile's damage source names the projectile as its direct entity.
        if (event.getSource().getDirectEntity() != player) return;
        if (CLEAVING.contains(player.getUUID())) return;

        LivingEntity target = event.getEntity();

        double bonus = ambushBonus(player, target)
                + comboBonus(player, target)
                + executeBonus(player, target)
                + desperationBonus(player)
                + outnumberedBonus(player);
        if (bonus > 0.0) event.setAmount(event.getAmount() * (float) (1.0 + bonus));

        applyArmorRecovery(player, event);
        applyStagger(player, target);
        startBleed(player, target, event.getAmount());
        applyCleave(player, target, event.getAmount());
    }

    /** Ambush: the first blow on something that had not noticed you. */
    private static double ambushBonus(Player player, LivingEntity target) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.AMBUSH_DAMAGE);
        if (bonus <= 0.0) return 0.0;
        // A mob that has picked a target has noticed somebody. Anything else is being caught unaware.
        return target instanceof Mob mob && mob.getTarget() != null ? 0.0 : bonus;
    }

    /**
     * Follow Through: hits landed in a row on the same target escalate.
     *
     * Reset by switching target as well as by pausing, so it rewards committing to one thing rather
     * than flailing at a crowd - a crowd is what Warmaster is for.
     */
    private static double comboBonus(Player player, LivingEntity target) {
        double perHit = SkillBonuses.get(player, ModSkillBonuses.COMBO_DAMAGE);
        if (perHit <= 0.0) return 0.0;

        long now = player.level().getGameTime();
        Combo current = COMBOS.get(player.getUUID());
        boolean continues = current != null
                && current.target().equals(target.getUUID())
                && now - current.lastHitTick() <= COMBO_TIMEOUT_TICKS;

        // Vanguard's Escalation raises the ceiling as well as the step, so the subclass buys a longer
        // run of escalating blows rather than only a steeper one on the same three.
        int cap = COMBO_CAP + (int) SkillBonuses.get(player, ModSkillBonuses.COMBO_CAP_BONUS);

        int hits = continues ? Math.min(current.hits() + 1, cap) : 1;
        COMBOS.put(player.getUUID(), new Combo(target.getUUID(), hits, now));

        return perHit * (hits - 1); // the first hit of a combo is not yet a combo
    }

    private static double executeBonus(Player player, LivingEntity target) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.EXECUTE_DAMAGE);
        if (bonus <= 0.0 || target.getMaxHealth() <= 0.0F) return 0.0;
        return target.getHealth() / target.getMaxHealth() <= EXECUTE_THRESHOLD ? bonus : 0.0;
    }

    /** Desperation: scaled by the share of your own health already gone. */
    private static double desperationBonus(Player player) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.DESPERATION_DAMAGE);
        if (bonus <= 0.0 || player.getMaxHealth() <= 0.0F) return 0.0;
        return bonus * (1.0 - player.getHealth() / player.getMaxHealth());
    }

    private static double outnumberedBonus(Player player) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.OUTNUMBERED);
        return bonus > 0.0 && countHostiles(player) >= OUTNUMBERED_THRESHOLD ? bonus : 0.0;
    }

    private static int countHostiles(Player player) {
        return player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(OUTNUMBERED_RADIUS),
                mob -> mob.isAlive() && mob.getTarget() == player).size();
    }

    /**
     * Piercing Strike: takes back a share of what the target's armor was about to stop.
     *
     * Written as a share of the absorbed damage rather than as armor ignored, and that is what makes
     * the cap honest. The most it can ever hand back is everything the armor stopped, which leaves an
     * armored target taking exactly what a bare one would - never more. So it closes the gap armor
     * opens without letting a player kill an armored zombie faster than an unarmored one.
     */
    private static void applyArmorRecovery(Player player, LivingHurtEvent event) {
        if (!player.getMainHandItem().is(ModTags.Items.PIERCING_WEAPONS)) return;

        double share = SkillBonuses.reduction(player, ModSkillBonuses.ARMOR_RECOVERY);
        if (share <= 0.0) return;

        LivingEntity target = event.getEntity();
        float armor = (float) target.getArmorValue();
        if (armor <= 0.0F) return;

        float amount = event.getAmount();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float absorbed = amount - CombatRules.getDamageAfterAbsorb(amount, armor, toughness);
        if (absorbed <= 0.0F) return;

        event.setAmount(amount + absorbed * (float) share);
    }

    /**
     * Stagger: a struck enemy is knocked off its rhythm.
     *
     * Expressed as heavy slowness rather than as a longer attack cooldown, because vanilla gives a mob
     * no attack-rate handle to turn - the interval lives inside whichever goal happens to be driving
     * it, and reaching into that per mob type would be brittle for no visible gain. Slowness produces
     * what the node is for: something hit hard arrives late.
     *
     * Bosses are exempt via {@code tommemod:bosses}. A fight that can be held in a stun-lock is not a
     * fight, and a boss is the one place that matters.
     */
    private static void applyStagger(Player player, LivingEntity target) {
        double seconds = SkillBonuses.get(player, ModSkillBonuses.STAGGER);
        if (seconds <= 0.0) return;
        if (target.getType().is(ModTags.EntityTypes.BOSSES)) return;

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                (int) Math.round(seconds * 20), 2, false, false, false));
    }

    /** Bleed: a wound that keeps taking its toll after the blow. */
    private static void startBleed(Player player, LivingEntity target, float damage) {
        double perSecond = SkillBonuses.get(player, ModSkillBonuses.BLEED_DAMAGE);
        if (perSecond <= 0.0 || damage <= 0.0F) return;

        BLEEDS.put(target.getUUID(),
                new Bleed(perSecond, player.level().getGameTime() + BLEED_TICKS));
    }

    /**
     * Cleave: the blow carries into whatever else is in range.
     *
     * Its own strike rather than a change to vanilla's sweep, which deals a flat 1 damage regardless of
     * weapon and so is crowd control rather than a way to fight a crowd. The guard is essential: each
     * cleaved target fires its own LivingHurtEvent naming this same player, and without it the first
     * swing would cleave outward until it ran out of mobs.
     */
    private static void applyCleave(Player player, LivingEntity struck, float damage) {
        double share = SkillBonuses.get(player, ModSkillBonuses.SWEEP_DAMAGE);
        if (share <= 0.0 || damage <= 0.0F) return;

        float carried = (float) (damage * Math.min(share, 1.0));
        if (carried <= 0.0F) return;

        CLEAVING.add(player.getUUID());
        try {
            for (LivingEntity nearby : player.level().getEntitiesOfClass(
                    LivingEntity.class, struck.getBoundingBox().inflate(CLEAVE_RADIUS))) {
                if (nearby == struck || nearby == player) continue;
                if (nearby.isAlliedTo(player)) continue;
                nearby.hurt(player.damageSources().playerAttack(player), carried);
            }
        } finally {
            CLEAVING.remove(player.getUUID());
        }
    }

    // ---- Over time ----

    /** Bleeds bite once a second, from one place, rather than each wound tracking its own timer. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level) || BLEEDS.isEmpty()) return;
        if (level.getGameTime() % BLEED_INTERVAL_TICKS != 0) return;

        long now = level.getGameTime();
        BLEEDS.entrySet().removeIf(entry -> {
            if (now >= entry.getValue().endsAtTick()) return true;

            Entity target = level.getEntity(entry.getKey());
            if (target == null) return false; // in another dimension or unloaded; leave it to expire
            if (!(target instanceof LivingEntity living) || !living.isAlive()) return true;

            bleedTick(living, (float) entry.getValue().perSecond());
            return false;
        });
    }

    /**
     * One bite of a bleed, taken without spending the target's invulnerability.
     *
     * {@code hurt} normally sets twenty ticks of invulnerability on whatever it damages, which for a
     * wound ticking once a second means the bleed and the player who opened it are competing for the
     * same window: a swing landing in the wrong half-second was swallowed whole. The counter is
     * therefore put back exactly as it was found, so a bleed neither grants immunity nor consumes it,
     * and the player never loses a hit to their own node.
     */
    private static void bleedTick(LivingEntity target, float amount) {
        int invulnerable = target.invulnerableTime;
        int hurtTime = target.hurtTime;

        target.invulnerableTime = 0;
        target.hurt(target.damageSources().generic(), amount);

        target.invulnerableTime = invulnerable;
        target.hurtTime = hurtTime;
    }

    /** Momentum: a kill leaves the hand moving faster than it was. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (SkillBonuses.get(player, ModSkillBonuses.KILL_MOMENTUM) <= 0.0) return;

        KILL_MOMENTUM_UNTIL.put(player.getUUID(), player.level().getGameTime() + KILL_MOMENTUM_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;

        Player player = event.player;
        Long until = KILL_MOMENTUM_UNTIL.get(player.getUUID());
        boolean active = until != null && player.level().getGameTime() < until;
        if (until != null && !active) KILL_MOMENTUM_UNTIL.remove(player.getUUID());

        applyAttackSpeed(player, active ? SkillBonuses.get(player, ModSkillBonuses.KILL_MOMENTUM) : 0.0);
    }

    private static void applyAttackSpeed(Player player, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(KILL_MOMENTUM_MODIFIER);
        if (amount <= 0.0) {
            if (existing != null) speed.removeModifier(KILL_MOMENTUM_MODIFIER);
            return;
        }
        if (existing != null && existing.getAmount() == amount) return;

        if (existing != null) speed.removeModifier(KILL_MOMENTUM_MODIFIER);
        speed.addTransientModifier(new AttributeModifier(KILL_MOMENTUM_MODIFIER, "Skill kill momentum",
                amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    // ---- Taking damage ----

    /** Warmaster's other half: being surrounded cuts both ways in your favour. */
    @SubscribeEvent
    public static void onTakeDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double bonus = SkillBonuses.get(player, ModSkillBonuses.OUTNUMBERED);
        if (bonus <= 0.0 || countHostiles(player) < OUTNUMBERED_THRESHOLD) return;

        event.setAmount(event.getAmount() * (float) (1.0 - Math.min(bonus, OUTNUMBERED_DEFENCE_CAP)));
    }
}
