package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Gliding does that is about the elytra specifically.
 *
 * Split out from {@link SkillMovementBonuses} because the split is real rather than tidy-minded: an
 * Agility node changes how a body moves and holds whether or not the player owns wings, while all of
 * this is conditional on being in the air under an elytra and is dead weight the rest of the time.
 * Gravity is the clearest case - Gliding's "less gravity" nodes used to be a flat entity_gravity
 * attribute, which also let the player moon-jump around town and drift down cliffs they had no
 * business surviving. Here it is lift, and lift only exists while the wings are out.
 *
 * The airborne half runs client-side for the same reason boats do: an elytra is flown by its pilot's
 * client and the result is sent up, so a server-side push is overwritten by the next position packet.
 * The burst and the mending are server business, being about other entities and about an item.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillGlidingBonuses {

    /** Blocks per tick of downward pull an elytra flight is subject to, which lift is a fraction of. */
    private static final double FLIGHT_GRAVITY = 0.08;
    /** How much extra forward speed one point of Dive may add per tick, at a vertical dive. */
    private static final double DIVE_ACCELERATION_SCALE = 0.05;
    /** Below this pitch the nose is level enough that nothing is being traded for speed. */
    private static final double DIVE_PITCH_THRESHOLD = 0.3;

    /** How far a takeoff burst reaches. */
    private static final double BURST_RADIUS = 5.0;
    /** Height above ground under which flying still counts as low enough to throw a burst. */
    private static final double BURST_GROUND_CLEARANCE = 4.0;
    /** Least time between bursts, so flying low along the ground is not a continuous shockwave. */
    private static final int BURST_COOLDOWN_TICKS = 60;

    /** How often idle wings are looked at. Elytra repair is quoted per second, so this is the second. */
    private static final int REPAIR_INTERVAL_TICKS = 20;

    /**
     * How much forward push one point of a conditional speed node adds per tick. Matched to the flat
     * glide bonus in SkillMovementBonuses, so a point of Slipstream at altitude is worth a point of
     * plain gliding speed and the two branches stay comparable.
     */
    private static final double SPEED_PUSH_SCALE = 0.006;

    /** Where Slipstream stops paying more. Roughly cloud height. */
    private static final double ALTITUDE_SPEED_CEILING = 200.0;
    private static final double SEA_LEVEL = 63.0;
    /** How fast a dive has to have been for pulling out of it to shift anything. */
    private static final double DIVE_IMPACT_MINIMUM_SPEED = 1.2;

    /** Last tick's horizontal speed per flier, for working out what a turn cost. Client-side only. */
    private static final Map<UUID, Double> LAST_FLIGHT_SPEED = new HashMap<>();
    /** Last tick's horizontal velocity, so a landing knows what to carry through. Client-side only. */
    private static final Map<UUID, Vec3> LAST_FLIGHT_VELOCITY = new HashMap<>();
    /** Last tick's descent rate, for spotting the moment a dive is pulled out of. Server-side only. */
    private static final Map<UUID, Double> LAST_FALL_SPEED = new HashMap<>();
    /** Who was already flying last tick, so taking off can be told from staying up. Server-side only. */
    private static final Set<UUID> AIRBORNE = new HashSet<>();
    /** Game tick each player's next burst is allowed at. */
    private static final Map<UUID, Long> BURST_READY_AT = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;

        if (player.level().isClientSide()) {
            if (player.isFallFlying()) {
                applyLift(player);
                applyDiveSpeed(player);
                applyAltitudeSpeed(player);
                applyStormSpeed(player);
                applyTurnRetention(player);
                rememberFlightVelocity(player);
            } else {
                applyLandingMomentum(player);
                LAST_FLIGHT_SPEED.remove(player.getUUID());
            }
            return;
        }

        applyTakeoffBurst(player);
        applyDiveImpact(player);
        applyElytraRepair(player);
        applyFlightRegen(player);
    }

    /**
     * Slipstream: thin air is faster air.
     *
     * Nothing at sea level and full value at cloud height, so it rewards climbing rather than simply
     * being another flat speed number bolted onto a tree that already has one.
     */
    private static void applyAltitudeSpeed(Player player) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.ALTITUDE_SPEED);
        if (bonus <= 0.0) return;

        double climbed = (player.getY() - SEA_LEVEL) / (ALTITUDE_SPEED_CEILING - SEA_LEVEL);
        double scaled = bonus * Math.max(0.0, Math.min(1.0, climbed));
        if (scaled <= 0.0) return;

        push(player, scaled * SPEED_PUSH_SCALE);
    }

    /** Swiftwing: weather a flier can use rather than weather to wait out. */
    private static void applyStormSpeed(Player player) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.STORM_SPEED);
        if (bonus <= 0.0) return;
        if (!player.level().isRaining() && !player.level().isThundering()) return;

        push(player, bonus * SPEED_PUSH_SCALE);
    }

    private static void push(Player player, double amount) {
        player.setDeltaMovement(player.getDeltaMovement().add(player.getLookAngle().scale(amount)));
    }

    /** Kept so the landing tick knows what the flight was doing a moment before it ended. */
    private static void rememberFlightVelocity(Player player) {
        Vec3 motion = player.getDeltaMovement();
        LAST_FLIGHT_VELOCITY.put(player.getUUID(), new Vec3(motion.x, 0.0, motion.z));
    }

    /**
     * Featherbones: a flight that ends in a run rather than in a stop.
     *
     * Vanilla drops almost all of a glider's horizontal speed the instant they touch down, which is
     * what makes arriving anywhere feel like hitting a wall. This hands a share of it back on the
     * landing tick and then forgets it, so it lengthens the arrival rather than granting ground speed.
     */
    private static void applyLandingMomentum(Player player) {
        Vec3 carried = LAST_FLIGHT_VELOCITY.remove(player.getUUID());
        if (carried == null || !player.onGround()) return;

        double share = SkillBonuses.reduction(player, ModSkillBonuses.LANDING_MOMENTUM);
        if (share <= 0.0) return;

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(carried.x * share, motion.y, carried.z * share);
    }

    /** Eternal Wings: time in the air is time healing. */
    private static void applyFlightRegen(Player player) {
        if (player.tickCount % 20 != 0 || !player.isFallFlying()) return;

        double perSecond = SkillBonuses.get(player, ModSkillBonuses.FLIGHT_REGEN);
        if (perSecond > 0.0 && player.getHealth() < player.getMaxHealth()) {
            player.heal((float) perSecond);
        }
    }

    /**
     * Skylord: pulling out of a dive at the last moment puts the ground out from under everything
     * standing on it.
     *
     * Distinct from Updraft's burst by when it fires rather than by what it does - that one is the
     * downbeat that gets you airborne, this one is the arrival. Needs the speed to have been real, so
     * a gentle descent onto a roof does nothing.
     */
    private static void applyDiveImpact(Player player) {
        UUID id = player.getUUID();
        double previousFall = LAST_FALL_SPEED.getOrDefault(id, 0.0);
        double currentFall = -player.getDeltaMovement().y;
        LAST_FALL_SPEED.put(id, player.isFallFlying() ? currentFall : 0.0);

        double strength = SkillBonuses.get(player, ModSkillBonuses.DIVE_IMPACT);
        if (strength <= 0.0 || !player.isFallFlying()) return;
        if (previousFall < DIVE_IMPACT_MINIMUM_SPEED) return;
        if (currentFall >= previousFall * 0.5) return; // still going down as fast; no pull-out yet
        if (!isNearGround(player)) return;

        AABB reach = player.getBoundingBox().inflate(BURST_RADIUS);
        for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class, reach)) {
            if (nearby == player) continue;
            nearby.knockback(strength, player.getX() - nearby.getX(), player.getZ() - nearby.getZ());
        }
    }

    /**
     * Stormrider: a blow struck coming out of the sky.
     *
     * Only counts while actually diving under the wings, so it is a reward for committing to the
     * approach - a flier drifting along level gets nothing, and neither does anyone on the ground.
     */
    @SubscribeEvent
    public static void onDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (!attacker.isFallFlying()) return;

        double bonus = SkillBonuses.get(attacker, ModSkillBonuses.DIVE_DAMAGE);
        if (bonus <= 0.0) return;
        if (attacker.getLookAngle().y > -DIVE_PITCH_THRESHOLD) return;

        event.setAmount(event.getAmount() * (float) (1.0 + bonus));
    }

    /** Holds the wings up. A fraction of the pull cancelled, never more than all of it. */
    private static void applyLift(Player player) {
        double lift = SkillBonuses.reduction(player, ModSkillBonuses.ELYTRA_LIFT);
        if (lift <= 0.0) return;

        player.setDeltaMovement(player.getDeltaMovement().add(0.0, FLIGHT_GRAVITY * lift, 0.0));
    }

    /**
     * Dive: pointing the nose down buys more speed than it used to.
     *
     * Scaled by how steep the dive actually is, so it is a reward for committing to one rather than a
     * flat bonus that happens to be named after diving. Level flight gets nothing.
     */
    private static void applyDiveSpeed(Player player) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.DIVE_SPEED);
        if (bonus <= 0.0) return;

        Vec3 look = player.getLookAngle();
        if (look.y > -DIVE_PITCH_THRESHOLD) return;

        double steepness = -look.y; // 0 at the threshold, 1 pointing straight down
        player.setDeltaMovement(player.getDeltaMovement()
                .add(look.scale(bonus * steepness * DIVE_ACCELERATION_SCALE)));
    }

    /**
     * Swiftwing: a hard turn stops costing everything it used to.
     *
     * An elytra bleeds speed when the wings are presented across the airflow, which is what makes
     * cornering expensive and is worth keeping - so this gives back a fraction of what a turn took
     * rather than preventing the loss. Measured as the drop in horizontal speed from one tick to the
     * next and returned along the direction of travel, so it restores the turn's momentum without
     * pointing it anywhere the player did not.
     */
    private static void applyTurnRetention(Player player) {
        Vec3 motion = player.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        Double previous = LAST_FLIGHT_SPEED.put(player.getUUID(), speed);

        double retention = SkillBonuses.reduction(player, ModSkillBonuses.TURN_RETENTION);
        if (retention <= 0.0 || previous == null) return;

        double lost = previous - speed;
        if (lost <= 0.0 || speed < 0.01) return;

        double giveBack = lost * retention;
        player.setDeltaMovement(motion.add(motion.x / speed * giveBack, 0.0, motion.z / speed * giveBack));
        LAST_FLIGHT_SPEED.put(player.getUUID(), speed + giveBack);
    }

    /**
     * Updraft: the downbeat that gets a flier off the ground throws the air out from under it.
     *
     * Fires on taking off, and again on any low pass, on a cooldown - the read is that the burst
     * needs ground close enough to push against. Anything standing near gets shoved outward and
     * slightly up, which is enough to break a mob off an attack without being a weapon in itself.
     */
    private static void applyTakeoffBurst(Player player) {
        UUID id = player.getUUID();
        boolean flying = player.isFallFlying();
        boolean tookOff = flying && AIRBORNE.add(id);
        if (!flying) AIRBORNE.remove(id);
        if (!flying) return;

        double strength = SkillBonuses.get(player, ModSkillBonuses.TAKEOFF_BURST);
        if (strength <= 0.0) return;

        long now = player.level().getGameTime();
        if (!tookOff) {
            if (now < BURST_READY_AT.getOrDefault(id, 0L)) return;
            if (!isNearGround(player)) return;
        }
        BURST_READY_AT.put(id, now + BURST_COOLDOWN_TICKS);

        AABB reach = player.getBoundingBox().inflate(BURST_RADIUS);
        for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class, reach)) {
            if (nearby == player) continue;
            nearby.knockback(strength, player.getX() - nearby.getX(), player.getZ() - nearby.getZ());
        }
    }

    private static boolean isNearGround(Player player) {
        AABB below = player.getBoundingBox().expandTowards(0.0, -BURST_GROUND_CLEARANCE, 0.0);
        return !player.level().noCollision(player, below);
    }

    /** Mending Hands: wings left off for a while come back together on their own. */
    private static void applyElytraRepair(Player player) {
        if (player.tickCount % REPAIR_INTERVAL_TICKS != 0) return;
        if (player.isFallFlying()) return;

        double perSecond = SkillBonuses.get(player, ModSkillBonuses.ELYTRA_REPAIR);
        if (perSecond <= 0.0) return;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof ElytraItem) || !chest.isDamaged()) return;

        // Whole points only, so a rate under one a second is rolled for rather than rounded away.
        int mended = (int) perSecond;
        if (player.getRandom().nextDouble() < perSecond - mended) mended++;
        if (mended <= 0) return;

        chest.setDamageValue(Math.max(0, chest.getDamageValue() - mended));
    }

    /** Tuck and Roll: only worth anything to somebody who actually has wings on. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ElytraItem)) return;

        double reduction = SkillBonuses.reduction(player, ModSkillBonuses.ELYTRA_FALL_DAMAGE_REDUCTION);
        if (reduction <= 0.0) return;

        event.setDamageMultiplier(event.getDamageMultiplier() * (float) (1.0 - reduction));
    }

    /** Soft Landing: the cost of misjudging a gap at speed. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getSource().is(DamageTypes.FLY_INTO_WALL)) return;

        double reduction = SkillBonuses.reduction(player, ModSkillBonuses.FLIGHT_COLLISION_REDUCTION);
        if (reduction <= 0.0) return;

        event.setAmount(event.getAmount() * (float) (1.0 - reduction));
    }
}
