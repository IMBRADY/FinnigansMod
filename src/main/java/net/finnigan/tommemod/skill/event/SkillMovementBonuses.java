package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
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

    /** How much of a boat's momentum one point of Sailing may add each tick. */
    private static final double BOAT_ACCELERATION_SCALE = 0.02;
    /** How much forward push one point of Gliding may add each tick of flight. */
    private static final double GLIDE_ACCELERATION_SCALE = 0.006;

    // ---- Agility ----

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double extra = SkillBonuses.get(player, ModSkillBonuses.JUMP_POWER);
        if (extra <= 0.0) return;

        // Runs on both sides. The jump is simulated on the client and the result sent up, so a
        // server-only boost would be overwritten by the very next position packet.
        player.setDeltaMovement(player.getDeltaMovement().add(0.0, extra, 0.0));
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
            // apply it twice on anything the server does simulate.
            applyBoatSpeed(player);
            applyGlideSpeed(player);
            return;
        }

        // Attributes are synced down to the client by the game, so they are set once, on the server,
        // and the client's own movement prediction picks them up without help.
        applyConditionalSpeed(player, SPRINT_SPEED_MODIFIER, "Skill sprint speed",
                player.isSprinting() ? SkillBonuses.get(player, ModSkillBonuses.SPRINT_SPEED) : 0.0);

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
}
