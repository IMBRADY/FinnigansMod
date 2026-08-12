package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cosmetic: Fire Aspect / Flame items trail flame particles - held, lying on the ground, or in
 * flight as a burning arrow.
 *
 * Client-only and purely visual, so nothing here is ever sent to the server. The work is bounded by
 * a scan radius and a tick interval rather than run every frame per item - unbounded particle spawns
 * are the only way a decoration like this can hurt.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, value = Dist.CLIENT)
public class FireEnchantParticleHandler {

    private static final int SPAWN_INTERVAL_TICKS = 3;
    private static final double SCAN_RADIUS = 24.0D;
    /** Particles laid down per tick along a burning projectile's path. */
    private static final int TRAIL_SEGMENTS = 6;
    /**
     * Distance from an arrow's entity position to the point of its head, from ArrowRenderer's
     * geometry: the shaft spans local x -8..8, shifted by -4 and scaled by 0.05625.
     */
    private static final double ARROW_TIP_OFFSET = 0.225D;
    /** Below this much movement in a tick a projectile counts as stopped (stuck in a block). */
    private static final double MOVING_EPSILON_SQR = 1.0E-6D;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ModConfig.FIRE_ENCHANT_PARTICLES.get()) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) return;
        if (level.getGameTime() % SPAWN_INTERVAL_TICKS != 0) return;

        AABB scanBox = minecraft.player.getBoundingBox().inflate(SCAN_RADIUS);

        for (Player player : level.players()) {
            if (!player.getBoundingBox().intersects(scanBox)) continue;
            for (InteractionHand hand : InteractionHand.values()) {
                if (isFireEnchanted(player.getItemInHand(hand))) {
                    spawnFlame(level, handPosition(player, hand));
                }
            }
        }

        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, scanBox,
                entity -> entity.isAlive() && isFireEnchanted(entity.getItem()))) {
            spawnFlame(level, item.position().add(0.0D, 0.2D, 0.0D));
        }

        // Arrows fired from a Flame bow/longbow/crossbow are set alight by the enchantment itself, so
        // trailing every burning projectile covers all three weapons without having to ask a projectile
        // in flight what it was launched from - which it no longer knows.
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, scanBox,
                entity -> entity.isAlive() && entity.isOnFire())) {
            spawnTrail(level, projectile);
        }
    }

    private static boolean isFireEnchanted(ItemStack stack) {
        if (stack.isEmpty() || !stack.isEnchanted()) return false;
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, stack) > 0
                || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0;
    }

    /** Rough held-item position: forward of the chest and out to whichever side that hand is on. */
    private static Vec3 handPosition(Player player, InteractionHand hand) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();

        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        double sideways = (mainHand == rightHanded) ? 0.42D : -0.42D;

        return player.position()
                .add(0.0D, player.getEyeHeight() * 0.75D, 0.0D)
                .add(look.scale(0.35D))
                .add(right.scale(sideways));
    }

    private static void spawnFlame(ClientLevel level, Vec3 pos) {
        double jitter = (level.random.nextDouble() - 0.5D) * 0.08D;
        level.addParticle(ParticleTypes.FLAME, pos.x, pos.y, pos.z, jitter, 0.01D, jitter);
    }

    /**
     * Laid down along the whole stretch the projectile covered since the last spawn, not just at its
     * current position - an arrow crosses several blocks per tick and this only runs every few ticks,
     * so a single point per pass would read as scattered embers instead of a trail. Velocity is near
     * enough constant over that window to walk the gap back from the current position.
     *
     * Once the projectile stops - an arrow stuck in the ground - there is no stretch left to trail,
     * and laying one down anyway is what used to leave a burning smear hanging off the back of every
     * landed arrow forever. A stopped arrow gets a single flame at its head instead.
     *
     * Measured from last tick's position rather than getDeltaMovement(): a stuck arrow keeps whatever
     * residual delta AbstractArrow#onHitBlock left on it, so velocity never actually reads as zero.
     */
    private static void spawnTrail(ClientLevel level, Projectile projectile) {
        Vec3 head = projectile.position();
        Vec3 movedLastTick = head.subtract(projectile.xo, projectile.yo, projectile.zo);

        if (movedLastTick.lengthSqr() < MOVING_EPSILON_SQR) {
            spawnFlame(level, tipOf(projectile, facing(projectile)));
            return;
        }

        Vec3 tip = tipOf(projectile, movedLastTick.normalize());
        Vec3 tail = tip.subtract(movedLastTick.scale(SPAWN_INTERVAL_TICKS));
        for (int i = 0; i < TRAIL_SEGMENTS; i++) {
            spawnFlame(level, tail.lerp(tip, (i + 1) / (double) TRAIL_SEGMENTS));
        }
    }

    /** Where a stopped projectile is pointing - its rotation, which an arrow keeps once embedded. */
    private static Vec3 facing(Projectile projectile) {
        return Vec3.directionFromRotation(projectile.getXRot(), projectile.getYRot());
    }

    /** An arrow's entity position sits back along its shaft; everything else is its own tip. */
    private static Vec3 tipOf(Projectile projectile, Vec3 direction) {
        if (!(projectile instanceof AbstractArrow)) return projectile.position();
        return projectile.position().add(direction.scale(ARROW_TIP_OFFSET));
    }
}
