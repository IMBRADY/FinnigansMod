package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Ranger tree's two ends: the Marksman who puts more in the air, and the Hexblade who makes what
 * is already in the air impossible to armor against.
 *
 * Everything here works on projectiles rather than on bows, which is the tree's whole premise - what a
 * Ranger buys follows arrows, bolts, thrown weapons and the abilities of unique weapons alike. Nothing
 * below asks what was in the player's hand.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillRangerBonuses {

    /** How far off the original line an extra projectile is thrown, and how many may be added. */
    private static final double SPREAD = 0.12;
    private static final int EXTRA_PROJECTILE_CAP = 4;

    /** How far ahead a homing projectile looks, and the hardest turn it may make in one tick. */
    private static final double HOMING_RANGE = 24.0;
    private static final double HOMING_MAX_TURN = 0.35;

    /** How far a Coven's cooldown aura reaches, and how often it is paid out. */
    private static final double AURA_RADIUS = 12.0;
    private static final int AURA_INTERVAL_TICKS = 20;

    /** The most of a blow true damage may ever add, however many nodes stack. */
    private static final double TRUE_DAMAGE_CAP = 0.5;

    /** How long the ultimate takes to come back, and what it costs the things around it. */
    private static final int ULTIMATE_COOLDOWN_TICKS = 1200;
    private static final double ULTIMATE_RADIUS = 10.0;
    private static final float ULTIMATE_DAMAGE = 20.0F;

    /**
     * What a blow was worth before the target's armor took its cut.
     *
     * True damage is defined against the pre-armor figure - a share of what was swung, not of what got
     * through - and by {@link LivingDamageEvent}, where it has to be added so armor cannot touch it,
     * that number is already gone. Keyed by target rather than by attacker because the pair of events
     * is per-hit and per-target, and cleared as soon as it is read.
     */
    private static final Map<UUID, Float> PRE_ARMOR = new HashMap<>();

    private static final Map<UUID, Long> ULTIMATE_READY_AT = new HashMap<>();

    /** Guards against an extra projectile spawning extras of its own. */
    private static final String EXTRA_TAG = "tommemod:ranger_extra";

    // ---- True damage ----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!isProjectile(event.getSource().getDirectEntity())) return;
        if (SkillBonuses.get(player, ModSkillBonuses.TRUE_DAMAGE_SHARE) <= 0.0) return;

        PRE_ARMOR.put(event.getEntity().getUUID(), event.getAmount());
    }

    /**
     * Hexblade: a share of the shot that armor never gets a say in.
     *
     * Added at {@link LivingDamageEvent}, which fires after armor, enchantments and resistance have all
     * taken theirs - so what is added here is what lands. That is the entire point of the subclass: the
     * answer to something wearing a great deal of plate, rather than another percentage that plate
     * would eat along with the rest.
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        Float before = PRE_ARMOR.remove(event.getEntity().getUUID());
        if (before == null) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        double share = Math.min(SkillBonuses.get(player, ModSkillBonuses.TRUE_DAMAGE_SHARE), TRUE_DAMAGE_CAP);
        if (share <= 0.0) return;

        event.setAmount(event.getAmount() + (float) (before * share));
        applyManasteal(player);
    }

    /**
     * Manasteal: a landed shot pays back some of what the last ability cost.
     *
     * Taken off every cooldown the player is holding rather than off a chosen one - the alternative is
     * asking which unique they meant, and a Hexblade mid-fight has not got a way to answer.
     */
    private static void applyManasteal(Player player) {
        double ticks = SkillBonuses.get(player, ModSkillBonuses.MANASTEAL);
        if (ticks <= 0.0) return;

        shortenCooldowns(player, ticks);
    }

    // ---- Projectiles ----

    /**
     * Salvo and Storm of Arrows: more than one thing leaves the bow.
     *
     * The aimed projectile is never touched - only the copies are spread - so owning the node can
     * never make a careful shot worse. That is the same rule Archery's Multishot follows, and for the
     * same reason: a node that adds arrows should not tax the one you were aiming.
     */
    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;
        if (projectile.getPersistentData().getBoolean(EXTRA_TAG)) return;
        if (projectile.tickCount > 0) return;

        int extras = (int) Math.min(
                Math.round(SkillBonuses.get(player, ModSkillBonuses.EXTRA_PROJECTILES)), EXTRA_PROJECTILE_CAP);
        if (extras <= 0) return;

        for (int i = 0; i < extras; i++) {
            spawnCopy(player, projectile);
        }
    }

    /**
     * One extra projectile, of whatever kind the original was.
     *
     * Only arrows are copied. A generic Projectile has no contract for being duplicated - a thrown
     * trident carries the stack it came from and a fired firework its own explosion data - and cloning
     * them by entity type would produce things that behave like neither.
     */
    private static void spawnCopy(Player player, Projectile original) {
        if (!(original instanceof AbstractArrow arrow)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        AbstractArrow copy = (AbstractArrow) arrow.getType().create(level);
        if (copy == null) return;

        copy.moveTo(arrow.getX(), arrow.getY(), arrow.getZ(), arrow.getYRot(), arrow.getXRot());
        copy.setOwner(player);
        copy.setBaseDamage(arrow.getBaseDamage());
        copy.setCritArrow(arrow.isCritArrow());
        copy.pickup = AbstractArrow.Pickup.CREATIVE_ONLY; // spare arrows are conjured, not carried
        copy.getPersistentData().putBoolean(EXTRA_TAG, true);

        Vec3 motion = arrow.getDeltaMovement();
        copy.setDeltaMovement(
                motion.x + (player.getRandom().nextDouble() - 0.5) * SPREAD,
                motion.y + (player.getRandom().nextDouble() - 0.5) * SPREAD,
                motion.z + (player.getRandom().nextDouble() - 0.5) * SPREAD);

        level.addFreshEntity(copy);
    }

    /**
     * Seeker: projectiles that correct toward whatever is in front of them.
     *
     * Steering, not teleporting. The turn is capped per tick, so a projectile bends toward a target it
     * was roughly aimed at and misses one it was not - a shot that curved through ninety degrees would
     * make aiming irrelevant, which is a different node from the one the tree describes.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;

        // Walking every entity in the level is the one genuinely expensive thing in this class, so it
        // only happens where somebody actually owns the node. On a server where nobody has taken
        // Seeker this costs one pass over the player list per tick and nothing else.
        if (anyoneHoming(level)) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Projectile projectile) steer(projectile);
            }
        }

        if (level.getGameTime() % AURA_INTERVAL_TICKS == 0) payCooldownAura(level);
    }

    private static boolean anyoneHoming(ServerLevel level) {
        for (Player player : level.players()) {
            if (SkillBonuses.get(player, ModSkillBonuses.PROJECTILE_HOMING) > 0.0) return true;
        }
        return false;
    }

    private static void steer(Projectile projectile) {
        if (!(projectile.getOwner() instanceof Player player)) return;
        if (projectile.onGround() || projectile.tickCount > 200) return;

        double homing = SkillBonuses.get(player, ModSkillBonuses.PROJECTILE_HOMING);
        if (homing <= 0.0) return;

        LivingEntity target = nearestTarget(projectile, player);
        if (target == null) return;

        Vec3 motion = projectile.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.1) return;

        Vec3 wanted = target.getEyePosition().subtract(projectile.position()).normalize();
        Vec3 steered = motion.normalize()
                .add(wanted.subtract(motion.normalize()).scale(Math.min(homing, HOMING_MAX_TURN)))
                .normalize()
                .scale(speed);

        projectile.setDeltaMovement(steered);
        projectile.hasImpulse = true;
    }

    @Nullable
    private static LivingEntity nearestTarget(Projectile projectile, Player owner) {
        LivingEntity best = null;
        double bestDistance = HOMING_RANGE * HOMING_RANGE;

        AABB reach = projectile.getBoundingBox().inflate(HOMING_RANGE);
        for (LivingEntity candidate : projectile.level().getEntitiesOfClass(LivingEntity.class, reach)) {
            if (candidate == owner || !candidate.isAlive() || candidate.isAlliedTo(owner)) continue;
            if (candidate instanceof Player) continue; // a homing shot that hunts allies is not the node

            // Only things the projectile is already heading toward, so a Seeker still has to aim.
            Vec3 toward = candidate.getEyePosition().subtract(projectile.position());
            if (toward.lengthSqr() > bestDistance) continue;
            if (projectile.getDeltaMovement().normalize().dot(toward.normalize()) < 0.5) continue;

            best = candidate;
            bestDistance = toward.lengthSqr();
        }
        return best;
    }

    // ---- The party ----

    /** Coven: everybody near a Hexblade gets their abilities back sooner. */
    private static void payCooldownAura(ServerLevel level) {
        for (Player caster : level.players()) {
            double share = SkillBonuses.reduction(caster, ModSkillBonuses.ALLY_COOLDOWN_AURA);
            if (share <= 0.0) continue;

            for (Player ally : level.getEntitiesOfClass(Player.class,
                    caster.getBoundingBox().inflate(AURA_RADIUS))) {
                if (ally == caster) continue;
                // A fraction of a second per second: the aura shortens cooldowns, it does not clear them.
                shortenCooldowns(ally, AURA_INTERVAL_TICKS * share);
            }
        }
    }

    /**
     * Fast-forwards every cooldown a player is holding.
     *
     * {@code ItemCooldowns} has no "reduce" and its internals are per-item, so this steps the whole
     * clock instead - the same thing a tick does, done several times over. Rounding leftovers up is
     * deliberate: a bonus small enough to round to zero would silently do nothing at all.
     */
    private static void shortenCooldowns(Player player, double ticks) {
        int steps = (int) Math.floor(ticks);
        if (player.getRandom().nextDouble() < ticks - steps) steps++;

        for (int i = 0; i < steps; i++) {
            player.getCooldowns().tick();
        }
    }

    /**
     * Oblivion: everything around the Hexblade takes a share it cannot armor against.
     *
     * Dealt as true damage for the same reason the subclass's shots are - the ultimate of a class built
     * to answer armor should not be the one thing in its kit that armor answers.
     */
    public static boolean tryUltimate(Player player) {
        if (!SkillBonuses.has(player, ModSkillBonuses.ULTIMATE)) return false;

        long now = player.level().getGameTime();
        Long readyAt = ULTIMATE_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) return false;

        ULTIMATE_READY_AT.put(player.getUUID(), now + ULTIMATE_COOLDOWN_TICKS);

        float power = ULTIMATE_DAMAGE
                * (float) (1.0 + SkillBonuses.get(player, ModSkillBonuses.ABILITY_POWER));

        for (LivingEntity caught : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(ULTIMATE_RADIUS))) {
            if (caught == player || caught instanceof Player || caught.isAlliedTo(player)) continue;
            caught.hurt(player.damageSources().magic(), power);
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    /**
     * What a unique's ability should be multiplied by for this player.
     *
     * The damage half is applied for every unique in the pack by {@link SkillUniqueBonuses}, which
     * needs no cooperation from the weapons. This is here for the rest - a radius, a duration, a count
     * of summons - which only the item itself can scale, and which it has to ask for.
     */
    public static double abilityMultiplier(Player player) {
        return 1.0 + SkillBonuses.get(player, ModSkillBonuses.ABILITY_POWER);
    }

    private static boolean isProjectile(@Nullable Entity direct) {
        return direct instanceof Projectile;
    }
}
