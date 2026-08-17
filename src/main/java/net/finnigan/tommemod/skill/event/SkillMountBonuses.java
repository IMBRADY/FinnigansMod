package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What Riding does to the animal underneath the player.
 *
 * Everything here is scoped to the rider rather than the horse: a mount carries its rider's skill
 * while they are on it and is an ordinary animal again the moment they get off, so a borrowed horse
 * is worth exactly what its current rider is worth. That is also why every one of these is a
 * transient attribute modifier rather than anything written into the animal - nothing survives the
 * dismount, and nothing is left behind on an entity that gets saved to disk.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillMountBonuses {

    private static final UUID SUSTAINED_SPEED_MODIFIER = UUID.fromString("2f6d0a13-7c48-4b21-9e35-8a1c7d04f9e6");
    private static final UUID STEP_HEIGHT_MODIFIER = UUID.fromString("9b41c7e2-5d38-4a06-8f17-2c93e6b5a704");
    private static final UUID JUMP_POWER_MODIFIER = UUID.fromString("6c8b39f4-1e75-42a0-9d63-5f08b2c7e419");
    private static final UUID HEALTH_MODIFIER = UUID.fromString("d05a8e63-3b17-4c92-8e40-7a26f1b9c583");

    /** How long a mount has to be running before Warhorse is paying out in full. */
    private static final int SUSTAINED_SPEED_RAMP_TICKS = 60;
    /** Below this, a mount is milling about rather than running, and the ramp resets. */
    private static final double RUNNING_SPEED_THRESHOLD = 0.08;
    /**
     * Blocks per tick a mount settles at, per point of its movement speed attribute. Horses run the
     * same relationship players do, so the same ratio reads a given animal's real top speed back out.
     */
    private static final double MOUNT_TOP_SPEED_FACTOR = 2.806;

    /** Ticks each rider's mount has been running without a break. Keyed by rider, cleared on dismount. */
    private static final Map<UUID, Integer> RUN_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;

        LivingEntity mount = mountOf(player);

        if (player.level().isClientSide()) {
            // A ridden animal is simulated by its rider's client and its position sent up, the same
            // as a boat, so the shove that gets it moving belongs here and nowhere else.
            if (mount != null) applyMountAcceleration(player, mount);
            return;
        }

        if (mount == null) {
            RUN_TICKS.remove(player.getUUID());
            return;
        }

        applySustainedSpeed(player, mount);
        applyTrample(player, mount);
        applyMountRegen(player, mount);
        applyModifier(mount, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_HEIGHT_MODIFIER, "Skill mount step height",
                SkillBonuses.get(player, ModSkillBonuses.MOUNT_STEP_HEIGHT), AttributeModifier.Operation.ADDITION);
        applyModifier(mount, Attributes.JUMP_STRENGTH, JUMP_POWER_MODIFIER, "Skill mount jump",
                SkillBonuses.get(player, ModSkillBonuses.MOUNT_JUMP_POWER), AttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mount, Attributes.MAX_HEALTH, HEALTH_MODIFIER, "Skill mount health",
                SkillBonuses.get(player, ModSkillBonuses.MOUNT_HEALTH), AttributeModifier.Operation.ADDITION);
    }

    /**
     * Warhorse: a mount that has been held at a run gradually finds another gear.
     *
     * Ramped rather than switched on at the three second mark, so it reads as an animal opening up
     * instead of a speed boost arriving. Any tick spent not running puts it back to nothing - the
     * node rewards a long gallop, and stopping to fight ends the gallop.
     */
    private static void applySustainedSpeed(Player player, LivingEntity mount) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.MOUNT_SUSTAINED_SPEED);
        if (bonus <= 0.0) {
            RUN_TICKS.remove(player.getUUID());
            applyModifier(mount, Attributes.MOVEMENT_SPEED, SUSTAINED_SPEED_MODIFIER, "Skill sustained speed",
                    0.0, AttributeModifier.Operation.MULTIPLY_BASE);
            return;
        }

        Vec3 motion = mount.getDeltaMovement();
        boolean running = Math.sqrt(motion.x * motion.x + motion.z * motion.z) >= RUNNING_SPEED_THRESHOLD;

        int ticks = running ? Math.min(RUN_TICKS.getOrDefault(player.getUUID(), 0) + 1, SUSTAINED_SPEED_RAMP_TICKS) : 0;
        RUN_TICKS.put(player.getUUID(), ticks);

        double ramped = bonus * ((double) ticks / SUSTAINED_SPEED_RAMP_TICKS);
        applyModifier(mount, Attributes.MOVEMENT_SPEED, SUSTAINED_SPEED_MODIFIER, "Skill sustained speed",
                ramped, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /**
     * Gallop: half a ton of horse at speed is an argument in itself.
     *
     * Knockback only, with no damage - a mount that killed things by walking into them would quietly
     * be the best weapon in the game and would do it without the rider deciding anything. What this
     * buys is the ability to get through a crowd rather than to fight one, which is what a horse is
     * actually for. Charge remains the node about hitting people.
     */
    private static void applyTrample(Player player, LivingEntity mount) {
        double strength = SkillBonuses.get(player, ModSkillBonuses.MOUNT_TRAMPLE);
        if (strength <= 0.0) return;

        Vec3 motion = mount.getDeltaMovement();
        if (Math.sqrt(motion.x * motion.x + motion.z * motion.z) < RUNNING_SPEED_THRESHOLD) return;

        for (LivingEntity struck : mount.level().getEntitiesOfClass(
                LivingEntity.class, mount.getBoundingBox().inflate(0.4))) {
            if (struck == mount || struck == player) continue;
            struck.knockback(strength, mount.getX() - struck.getX(), mount.getZ() - struck.getZ());
        }
    }

    /**
     * Courser: a horse bred for the road mends on it.
     *
     * Only while actually travelling, so it is a reward for the long ride rather than a way to park a
     * wounded mount somewhere safe and wait.
     */
    private static void applyMountRegen(Player player, LivingEntity mount) {
        if (player.tickCount % 20 != 0) return;

        double perSecond = SkillBonuses.get(player, ModSkillBonuses.MOUNT_REGEN);
        if (perSecond <= 0.0 || mount.getHealth() >= mount.getMaxHealth()) return;

        Vec3 motion = mount.getDeltaMovement();
        if (Math.sqrt(motion.x * motion.x + motion.z * motion.z) < RUNNING_SPEED_THRESHOLD) return;

        mount.heal((float) perSecond);
    }

    /** Spurs: the animal answers at once instead of gathering itself first. */
    private static void applyMountAcceleration(Player player, LivingEntity mount) {
        if (!SkillBonuses.has(player, ModSkillBonuses.MOUNT_ACCELERATION)) return;

        Vec3 motion = mount.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (speed < 0.01) return;

        double target = mount.getAttributeValue(Attributes.MOVEMENT_SPEED) * MOUNT_TOP_SPEED_FACTOR;
        if (speed >= target) return;

        double scale = target / speed;
        mount.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
    }

    /**
     * Armored Mount, which has to answer for two falls rather than one: vanilla hurts a horse for the
     * drop and then hurts everyone it was carrying, as two separate events on two separate entities.
     * Both are the same rider's node paying out, so both are found from whichever end fired.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        Player rider = riderFor(event.getEntity());
        if (rider == null) return;

        double reduction = SkillBonuses.reduction(rider, ModSkillBonuses.MOUNTED_FALL_DAMAGE_REDUCTION);
        if (reduction <= 0.0) return;

        event.setDamageMultiplier(event.getDamageMultiplier() * (float) (1.0 - reduction));
    }

    /** The player whose Riding governs a fall: the faller themselves if mounted, else who is aboard. */
    private static Player riderFor(LivingEntity falling) {
        if (falling instanceof Player player) return player.isPassenger() ? player : null;
        return falling.getControllingPassenger() instanceof Player player ? player : null;
    }

    /** The living thing this player is riding, and only while they are the one steering it. */
    private static LivingEntity mountOf(Player player) {
        if (!(player.getVehicle() instanceof LivingEntity mount)) return null;
        return player.equals(mount.getControllingPassenger()) ? mount : null;
    }

    /**
     * Sets one modifier to one value, or takes it away at zero. Rewritten only when the number has
     * actually moved, so a mount ridden for an hour is not churning its attribute map every tick.
     */
    private static void applyModifier(LivingEntity target, Attribute attribute, UUID id, String name,
                                      double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) return; // not every mount has every attribute - a pig has no jump strength

        AttributeModifier existing = instance.getModifier(id);
        if (amount <= 0.0) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing != null && existing.getAmount() == amount) return;

        if (existing != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }
}
