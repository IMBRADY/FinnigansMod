package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Agility, Riding, Sailing and Gliding bonuses that no attribute can express.
 *
 * Two different techniques appear here, and which one a bonus uses is not arbitrary. Anything that
 * changes how fast a player or their vehicle moves goes through an attribute modifier, toggled on and
 * off as the condition holds, because the server's movement checks are written against attributes -
 * pushing a player along by hand instead is what produces "moved too quickly" and rubber-banding.
 * Anything that changes a one-off outcome - a jump's impulse, a landing, a lungful of air - is a
 * direct adjustment at the moment it happens, where there is nothing to get out of step with.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillMovementBonuses {

    private static final UUID SPRINT_SPEED_MODIFIER = UUID.fromString("8f2c41d7-5b90-4e63-9a18-3d7e6c204b15");
    private static final UUID MOUNT_SPEED_MODIFIER = UUID.fromString("c31a7e05-24b8-4f96-8d70-1e59fa38b7c2");
    private static final UUID SUSTAINED_SPRINT_MODIFIER = UUID.fromString("5e7b12a4-8c60-4f39-b2d1-6a04c9e7f350");

    /** How long a sprint has to be held before Endurance is paying out in full. */
    private static final int SUSTAINED_SPRINT_RAMP_TICKS = 60;
    /** Ticks each player has held their current sprint. Cleared the moment they stop. */
    private static final Map<UUID, Integer> SPRINT_TICKS = new HashMap<>();

    /** How much of a boat's momentum one point of Sailing may add each tick. */
    private static final double BOAT_ACCELERATION_SCALE = 0.02;
    /** How much forward push one point of Gliding may add each tick of flight. */
    private static final double GLIDE_ACCELERATION_SCALE = 0.006;

    /**
     * Blocks per tick a sprint settles at, per point of the movement speed attribute. Vanilla's
     * default 0.1 attribute runs at about 0.281 blocks a tick, and the ratio holds as the attribute
     * moves, so this reads a player's real top speed back out of whatever their nodes have done to it.
     */
    private static final double SPRINT_TOP_SPEED_FACTOR = 2.806;
    /** Comfortably past the 200 ticks at which vanilla starts flashing night vision out. */
    private static final int UNDERWATER_VISION_REFRESH_TICKS = 300;
    /** Blocks per tick a player falls at once air resistance and gravity have balanced out. */
    private static final double TERMINAL_VELOCITY = 3.92;
    /**
     * How closely a sprinter has to be facing the way they are moving before the sprint boost helps.
     * About thirty degrees; wider than that is a turn, and a turn is vanilla's business.
     */
    private static final double TURN_ALIGNMENT_THRESHOLD = 0.86;

    // ---- Agility ----

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Runs on both sides. The jump is simulated on the client and the result sent up, so a
        // server-only boost would be overwritten by the very next position packet.
        double extra = SkillBonuses.get(player, ModSkillBonuses.JUMP_POWER);
        if (extra > 0.0) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, extra, 0.0));
        }

        double forward = SkillBonuses.get(player, ModSkillBonuses.JUMP_FORWARD);
        if (forward > 0.0) {
            // Carried along whatever line the player was already running, so it lengthens a jump
            // rather than steering it. Standing still there is no such line, and the shove goes where
            // they are looking instead - which is the only reading of "forward" left.
            Vec3 motion = player.getDeltaMovement();
            double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            Vec3 heading = speed > 0.01
                    ? new Vec3(motion.x / speed, 0.0, motion.z / speed)
                    : horizontalLook(player);
            player.setDeltaMovement(motion.add(heading.scale(forward)));
        }
    }

    /** The way a player is facing, flattened and normalised. Zero if they are looking straight down. */
    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        double flat = Math.sqrt(look.x * look.x + look.z * look.z);
        return flat < 1.0E-4 ? Vec3.ZERO : new Vec3(look.x / flat, 0.0, look.z / flat);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        float safeFallBonus = (float) SkillBonuses.get(player, ModSkillBonuses.SAFE_FALL_BONUS);
        if (safeFallBonus > 0.0F) event.setDistance(Math.max(0.0F, event.getDistance() - safeFallBonus));

        double reduction = SkillBonuses.reduction(player, ModSkillBonuses.FALL_DAMAGE_REDUCTION);
        if (reduction > 0.0) event.setDamageMultiplier(event.getDamageMultiplier() * (float) (1.0 - reduction));
    }

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.canBreathe() || event.getConsumeAirAmount() <= 0) return;

        double retention = SkillBonuses.reduction(player, ModSkillBonuses.BREATH_RETENTION);
        if (retention <= 0.0) return;

        // Air is consumed in whole units, so a fractional saving has to be rolled for rather than
        // scaled - at 30% that is three ticks in ten where the lungs simply don't lose anything.
        if (player.getRandom().nextDouble() < retention) event.setConsumeAirAmount(0);
    }

    /**
     * Sprint speed, applied as a modifier that exists only while the player is sprinting.
     *
     * Kept separate from a plain movement speed node on purpose: the design sketch has Movement Speed
     * and Sprint as different nodes on the same branch, and one of them ought to be worth taking even
     * once you already have the other.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;

        if (player.level().isClientSide()) {
            // Boats and elytras are flown by their pilot's client and their position is sent up, so
            // these belong here and only here - adding the same push on the server as well would
            // apply it twice on anything the server does simulate. Running is the same story.
            applyBoatSpeed(player);
            applyGlideSpeed(player);
            applySprintAcceleration(player);
            applyFallSpeedCap(player);
            return;
        }

        applyUnderwaterVision(player);

        // Attributes are synced down to the client by the game, so they are set once, on the server,
        // and the client's own movement prediction picks them up without help.
        applyConditionalSpeed(player, SPRINT_SPEED_MODIFIER, "Skill sprint speed",
                player.isSprinting() ? SkillBonuses.get(player, ModSkillBonuses.SPRINT_SPEED) : 0.0);

        applySustainedSprint(player);

        // A mount is only ever quickened for as long as this player is the one on it, so a horse
        // borrowed by someone else is an ordinary horse again.
        if (player.getVehicle() instanceof LivingEntity mount) {
            applyConditionalSpeed(mount, MOUNT_SPEED_MODIFIER, "Skill mount speed",
                    SkillBonuses.get(player, ModSkillBonuses.MOUNT_SPEED));
        }
    }

    private static void applyConditionalSpeed(LivingEntity target, UUID id, String name, double amount) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(id);
        if (amount <= 0.0) {
            if (existing != null) speed.removeModifier(id);
            return;
        }
        if (existing != null && existing.getAmount() == amount) return;

        if (existing != null) speed.removeModifier(id);
        speed.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /**
     * Both of these nudge momentum rather than setting it. Adding to the existing vector means the
     * boost only ever amplifies what the player was already doing - it cannot push a stationary boat,
     * and a player who lets go simply coasts to a stop as usual.
     */
    private static void applyBoatSpeed(Player player) {
        if (!(player.getVehicle() instanceof Boat boat)) return;
        if (!player.equals(boat.getControllingPassenger())) return;

        double bonus = SkillBonuses.get(player, ModSkillBonuses.BOAT_SPEED);
        if (bonus <= 0.0) return;

        Vec3 motion = boat.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (speed < 0.01) return;

        double push = Math.min(bonus, 1.0) * BOAT_ACCELERATION_SCALE;
        boat.setDeltaMovement(motion.add(motion.x / speed * push, 0.0, motion.z / speed * push));
    }

    private static void applyGlideSpeed(Player player) {
        if (!player.isFallFlying()) return;

        double bonus = SkillBonuses.get(player, ModSkillBonuses.GLIDE_SPEED);
        if (bonus <= 0.0) return;

        Vec3 look = player.getLookAngle();
        double push = Math.min(bonus, 1.0) * GLIDE_ACCELERATION_SCALE;
        player.setDeltaMovement(player.getDeltaMovement().add(look.scale(push)));
    }

    /**
     * Endurance: a sprint that has been held opens up, rather than one that is simply faster.
     *
     * Kept apart from Sprint Training on purpose - that node pays from the first stride, this one pays
     * nothing for the first three seconds and then more than it does. Two nodes on the same branch
     * about running fast are only worth having if one of them answers a different question, and the
     * question here is whether you can hold it.
     */
    private static void applySustainedSprint(Player player) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.SUSTAINED_SPRINT_SPEED);
        UUID id = player.getUUID();

        if (bonus <= 0.0 || !player.isSprinting()) {
            SPRINT_TICKS.remove(id);
            applyConditionalSpeed(player, SUSTAINED_SPRINT_MODIFIER, "Skill sustained sprint", 0.0);
            return;
        }

        int ticks = Math.min(SPRINT_TICKS.getOrDefault(id, 0) + 1, SUSTAINED_SPRINT_RAMP_TICKS);
        SPRINT_TICKS.put(id, ticks);

        applyConditionalSpeed(player, SUSTAINED_SPRINT_MODIFIER, "Skill sustained sprint",
                bonus * ((double) ticks / SUSTAINED_SPRINT_RAMP_TICKS));
    }

    /**
     * Marathon: the ground stops fighting the runner.
     *
     * Read by {@link net.finnigan.tommemod.mixin.StuckSpeedMixin}, which is where the actual refusal
     * happens - the drag from a cobweb is not an attribute or an event, it is a multiplier written
     * straight onto the entity, and the only place to decline it is where it is written.
     */
    public static boolean ignoresStickyGround(Player player) {
        return SkillBonuses.has(player, ModSkillBonuses.UNHINDERED_STRIDE);
    }

    /**
     * Skips the wind-up at the start of a sprint.
     *
     * Vanilla eases a runner up to speed over most of a second, which is realistic and, for a player
     * who has bought a node explicitly about getting going, precisely the thing being complained
     * about. This only ever raises horizontal speed to what the player would have reached anyway a
     * moment later, and never past it, so it changes when top speed arrives and not what it is.
     *
     * The scaling is the subtle part, and it is why this used to feel like ice. Multiplying the
     * existing motion vector keeps its <em>direction</em>, so on a hard turn it took the velocity the
     * player was leaving behind and pinned it back at full speed - the faster they were going, the
     * smaller a share of the heading one tick of input could change, and the turn came out as a skid.
     * Refusing to help while the runner is not pointed the way they are travelling fixes it without
     * touching what the node is for: vanilla handles the turn at vanilla's pace, and the moment the
     * two line up again the boost picks the sprint straight back up.
     */
    private static void applySprintAcceleration(Player player) {
        if (!player.isSprinting() || !player.onGround()) return;
        if (!SkillBonuses.has(player, ModSkillBonuses.SPRINT_ACCELERATION)) return;

        Vec3 motion = player.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        // Nothing to bring up to speed: a sprint that has not started moving has no direction to
        // accelerate along, and pushing a standing player would be a launch rather than a run.
        if (speed < 0.01) return;

        Vec3 heading = horizontalLook(player);
        if (heading.lengthSqr() < 1.0E-6) return;
        double alignment = (motion.x * heading.x + motion.z * heading.z) / speed;
        if (alignment < TURN_ALIGNMENT_THRESHOLD) return;

        double target = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * SPRINT_TOP_SPEED_FACTOR;
        if (speed >= target) return;

        double scale = target / speed;
        player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
    }

    /**
     * Featherfall: you come down slower, but you do come down.
     *
     * This replaced a {@code forge:entity_gravity} reduction, and the replacement is the point. Gravity
     * is applied every tick and compounds with itself, so stacked reductions across a tree ended in a
     * player the ground could no longer reach - and being an attribute, it applied while walking about
     * town as much as while falling. A ceiling on descent speed is the effect that was actually wanted:
     * terminal velocity drops, nothing else changes, and no amount of it adds up to flight.
     */
    private static void applyFallSpeedCap(Player player) {
        double cap = SkillBonuses.reduction(player, ModSkillBonuses.FALL_SPEED_CAP);
        if (cap <= 0.0) return;

        Vec3 motion = player.getDeltaMovement();
        if (motion.y >= 0.0 || player.isFallFlying() || player.onGround()) return;

        double limit = -TERMINAL_VELOCITY * (1.0 - cap);
        if (motion.y < limit) player.setDeltaMovement(motion.x, limit, motion.z);
    }

    /**
     * Night vision for as long as the player's eyes are under.
     *
     * Refreshed well clear of the 200 tick mark vanilla starts flashing the effect at, and taken away
     * the moment they surface rather than left to run down - the point is seeing under water, not
     * carrying a light back onto dry land. Marked ambient purely as a maker's mark, so surfacing can
     * tell this apart from a night vision potion the player drank and cancel only its own.
     */
    private static void applyUnderwaterVision(Player player) {
        boolean wanted = player.isEyeInFluid(FluidTags.WATER)
                && SkillBonuses.has(player, ModSkillBonuses.UNDERWATER_VISION);

        if (wanted) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    UNDERWATER_VISION_REFRESH_TICKS, 0, true, false, false));
            return;
        }

        MobEffectInstance existing = player.getEffect(MobEffects.NIGHT_VISION);
        if (existing != null && existing.isAmbient() && existing.getAmplifier() == 0
                && existing.getDuration() <= UNDERWATER_VISION_REFRESH_TICKS) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}
