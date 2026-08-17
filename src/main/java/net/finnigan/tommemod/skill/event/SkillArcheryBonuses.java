package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.mixin.AbstractArrowAccessor;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Archery tree: the bow, the draw, and the arrow once it has left.
 *
 * Most of this happens in one of two places. What the draw was worth is known at
 * {@link ArrowLooseEvent}, which carries how long it was held; what the arrow is is settled at
 * {@link EntityJoinLevelEvent}, the one moment it exists and has not yet moved. Doing the work there
 * rather than in a mixin on BowItem means tipped arrows, modded bows and Infinity all keep working.
 *
 * The tree is deliberately about speed and force rather than range - a bow that outranges a crossbow
 * leaves Marksmanship with nothing of its own.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillArcheryBonuses {

    private static final UUID DRAW_MOBILITY_MODIFIER = UUID.fromString("3c9f5a71-4e82-4d16-b7a0-92e4c1750d68");

    /** Full bow power in blocks per tick, which is what a snap shot is brought up to. */
    private static final float FULL_BOW_VELOCITY = 3.0F;
    /** Ticks of draw that count as full, after which Overdraw starts paying. */
    private static final int FULL_DRAW_TICKS = 20;
    /** How still, and for how long, an archer has to be for Aimed Shot. */
    private static final int AIM_STILL_TICKS = 20;
    private static final double AIM_STILL_SPEED = 0.02;
    /** How far off centre the extra arrow of a Multishot is thrown. */
    private static final float MULTISHOT_SPREAD = 3.0F;
    /** How long after the shot the follow-up arrow arrives. */
    private static final int FOLLOW_UP_DELAY_TICKS = 4;

    /** How long each archer has been standing still. */
    private static final Map<UUID, Integer> STILL_TICKS = new HashMap<>();
    /** Follow-up shots waiting to be loosed: the shooter, a copy of the arrow's aim, and when. */
    private static final List<PendingShot> PENDING = new ArrayList<>();

    private record PendingShot(UUID shooter, Vec3 velocity, double baseDamage, boolean critical,
                               long fireAtTick) {
    }

    // ---- What the draw was worth ----

    /**
     * Overdraw, and the arrow the shot did not spend.
     *
     * ArrowLooseEvent is the only place the length of the draw is known; the arrow itself does not
     * remember. Everything that depends on how long the bow was held is therefore stamped on here and
     * read back when the arrow appears.
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        int charge = event.getCharge();

        double overdraw = SkillBonuses.get(player, ModSkillBonuses.OVERDRAW_DAMAGE);
        if (overdraw > 0.0 && charge > FULL_DRAW_TICKS) {
            double heldPastFull = (charge - FULL_DRAW_TICKS) / 20.0;
            LAST_OVERDRAW.put(player.getUUID(), overdraw * heldPastFull);
        } else {
            LAST_OVERDRAW.remove(player.getUUID());
        }
    }

    /** How much Overdraw the shot now leaving the bow earned. */
    private static final Map<UUID, Double> LAST_OVERDRAW = new HashMap<>();

    // ---- What the arrow is ----

    /**
     * Everything decided about an arrow at the instant it exists.
     *
     * Pierce, knockback, crit and speed are all properties of the arrow rather than of the shot, so
     * this is both the earliest and the only clean place to set them - before it has travelled a single
     * tick, and without needing to know which bow or which mod fired it.
     */
    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player)) return;
        if (arrow.getPersistentData().getBoolean("tommemod:skill_extra")) return;

        int pierce = (int) Math.round(SkillBonuses.get(player, ModSkillBonuses.ARROW_PIERCE));
        if (pierce > 0) arrow.setPierceLevel((byte) Math.min(127, arrow.getPierceLevel() + pierce));

        // Added to what the bow already carries: Punch is set on the arrow before it joins the level,
        // and overwriting it would make Overdraw a downgrade on a Punch II bow.
        int punch = (int) Math.round(SkillBonuses.get(player, ModSkillBonuses.BOW_KNOCKBACK));
        if (punch > 0) arrow.setKnockback(arrow.getKnockback() + punch);

        Double overdraw = LAST_OVERDRAW.remove(player.getUUID());
        if (overdraw != null) arrow.setBaseDamage(arrow.getBaseDamage() * (1.0 + overdraw));

        applySnapShot(player, arrow);
        applyAimedCrit(player, arrow);
        refundArrow(player, arrow);
        scheduleFollowUp(player, arrow);
        fireExtraArrow(player, arrow);
    }

    /**
     * Snap Shot: a shot loosed early still flies as though it was fully drawn.
     *
     * An arrow's damage comes from how fast it is travelling, not from a stored number, so bringing a
     * half-drawn shot up to full speed is what makes it hit for full - and is also why the node reads as
     * quickness rather than as raw damage.
     */
    private static void applySnapShot(Player player, AbstractArrow arrow) {
        if (!SkillBonuses.has(player, ModSkillBonuses.SNAP_SHOT)) return;

        Vec3 motion = arrow.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.1 || speed >= FULL_BOW_VELOCITY) return;

        arrow.setDeltaMovement(motion.scale(FULL_BOW_VELOCITY / speed));
    }

    /** Aimed Shot: a shot taken from a settled stance always bites. */
    private static void applyAimedCrit(Player player, AbstractArrow arrow) {
        if (!SkillBonuses.has(player, ModSkillBonuses.AIMED_CRIT)) return;
        if (STILL_TICKS.getOrDefault(player.getUUID(), 0) < AIM_STILL_TICKS) return;

        arrow.setCritArrow(true);
    }

    /** Thrift: the arrow that was not spent. Given back rather than not taken - the bow has already
     * consumed it by the time the arrow exists to be asked about. */
    private static void refundArrow(Player player, AbstractArrow arrow) {
        if (player.isCreative()) return;
        if (!SkillBonuses.roll(player, ModSkillBonuses.ARROW_CONSERVATION)) return;

        ItemStack ammo = ((AbstractArrowAccessor) arrow).tommemod$getPickupItem();
        if (ammo.isEmpty()) return;
        if (!player.getInventory().add(ammo)) player.drop(ammo, false);
    }

    private static void scheduleFollowUp(Player player, AbstractArrow arrow) {
        if (!SkillBonuses.roll(player, ModSkillBonuses.FOLLOW_UP_SHOT)) return;

        PENDING.add(new PendingShot(player.getUUID(), arrow.getDeltaMovement(), arrow.getBaseDamage(),
                arrow.isCritArrow(), player.level().getGameTime() + FOLLOW_UP_DELAY_TICKS));
    }

    /** Multishot: a second arrow alongside, and only that one is thrown off line. */
    private static void fireExtraArrow(Player player, AbstractArrow arrow) {
        if (!SkillBonuses.roll(player, ModSkillBonuses.MULTISHOT_CHANCE)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        spawnCopy(level, player, arrow.getDeltaMovement(), arrow.getBaseDamage(), arrow.isCritArrow(),
                MULTISHOT_SPREAD);
    }

    /**
     * Puts another arrow in the air on the same line.
     *
     * Marked in persistent data so the spawn handler above lets it past untouched - without that, every
     * extra arrow would roll for its own extra arrow and one shot could fill the sky.
     */
    private static void spawnCopy(ServerLevel level, Player player, Vec3 velocity, double baseDamage,
                                  boolean critical, float spread) {
        net.minecraft.world.entity.projectile.Arrow copy =
                new net.minecraft.world.entity.projectile.Arrow(level, player);
        copy.getPersistentData().putBoolean("tommemod:skill_extra", true);
        copy.setBaseDamage(baseDamage);
        copy.setCritArrow(critical);
        copy.pickup = AbstractArrow.Pickup.DISALLOWED; // a free arrow should not also be lootable
        copy.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());

        double speed = velocity.length();
        copy.shoot(velocity.x, velocity.y, velocity.z, (float) speed, spread);
        level.addFreshEntity(copy);
    }

    // ---- Per tick ----

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;
        Player player = event.player;

        trackStillness(player);
        applyDrawMobility(player);
    }

    private static void trackStillness(Player player) {
        Vec3 motion = player.getDeltaMovement();
        boolean still = Math.sqrt(motion.x * motion.x + motion.z * motion.z) < AIM_STILL_SPEED;
        STILL_TICKS.put(player.getUUID(), still ? STILL_TICKS.getOrDefault(player.getUUID(), 0) + 1 : 0);
    }

    /**
     * Steady Hands: drawing stops rooting the archer.
     *
     * Vanilla slows a player using an item through a movement multiplier rather than an attribute, which
     * cannot be cancelled - so it is compensated for instead, and only while a bow is actually drawn.
     */
    private static void applyDrawMobility(Player player) {
        boolean drawing = player.isUsingItem()
                && player.getUseItem().getItem() instanceof BowItem
                && SkillBonuses.has(player, ModSkillBonuses.DRAW_MOBILITY);

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(DRAW_MOBILITY_MODIFIER);
        if (!drawing) {
            if (existing != null) speed.removeModifier(DRAW_MOBILITY_MODIFIER);
            return;
        }
        if (existing != null) return;

        speed.addTransientModifier(new AttributeModifier(DRAW_MOBILITY_MODIFIER, "Skill draw mobility",
                4.0, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /** Follow-up shots come due here, one place, rather than each scheduling its own timer. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level) || PENDING.isEmpty()) return;

        long now = level.getGameTime();
        PENDING.removeIf(pending -> {
            if (now < pending.fireAtTick()) return false;

            Player player = level.getPlayerByUUID(pending.shooter());
            if (player != null) {
                spawnCopy(level, player, pending.velocity(), pending.baseDamage(), pending.critical(), 0.0F);
            }
            return true;
        });
    }

    // ---- On impact ----

    /**
     * What an arrow does to what it hit.
     *
     * LOW, so the damage being multiplied is the finished figure the shared handler has already worked
     * out from ranged damage, long shot and headshot.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onArrowHit(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) return;

        LivingEntity target = event.getEntity();

        double marked = SkillBonuses.get(player, ModSkillBonuses.MARKED_DAMAGE);
        if (marked > 0.0 && target.hasEffect(MobEffects.GLOWING)) {
            event.setAmount(event.getAmount() * (float) (1.0 + marked));
        }

        double markSeconds = SkillBonuses.get(player, ModSkillBonuses.HUNTERS_MARK);
        if (markSeconds > 0.0) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                    (int) Math.round(markSeconds * 20), 0, false, false, true));
        }

        if (SkillBonuses.roll(player, ModSkillBonuses.ARROW_SLOW)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true, true));
        }

        double blindSeconds = SkillBonuses.get(player, ModSkillBonuses.HEADSHOT_BLIND);
        if (blindSeconds > 0.0 && isHeadshot(target, arrow)) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                    (int) Math.round(blindSeconds * 20), 0, false, true, true));
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(),
                        6, 0.2, 0.1, 0.2, 0.0);
            }
        }
    }

    /** The same test the shared headshot bonus uses: struck above the target's mid-line. */
    private static boolean isHeadshot(LivingEntity target, AbstractArrow arrow) {
        return arrow.getY() > target.getY() + target.getBbHeight() * 0.75;
    }
}
