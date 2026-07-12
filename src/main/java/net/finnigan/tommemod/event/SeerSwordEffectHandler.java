package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.SeerSwordItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SeerSwordEffectHandler {

    private static final UUID HEALTH_BUFF_UUID = UUID.fromString("7c1a9e10-2222-4b3f-8a1d-000000000010");
    private static final UUID SPEED_BUFF_UUID = UUID.fromString("7c1a9e10-2222-4b3f-8a1d-000000000011");
    private static final double BUFF_MULTIPLIER = 0.2;

    private static final int DODGE_COOLDOWN_TICKS = 80;
    private static final double DODGE_DISTANCE = 3.0;

    private static final Map<UUID, Integer> dodgeCooldowns = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        boolean hasSword = player.getMainHandItem().getItem() instanceof SeerSwordItem
                || player.getOffhandItem().getItem() instanceof SeerSwordItem;
        boolean inEnd = player.level().dimension().equals(Level.END);

        applyOrRemoveBuffs(player, hasSword && inEnd);

        dodgeCooldowns.computeIfPresent(player.getUUID(), (id, ticks) -> Math.max(0, ticks - 1));

        if (hasSword && dodgeCooldowns.getOrDefault(player.getUUID(), 0) <= 0) {
            Projectile incoming = findIncomingProjectile(player);
            if (incoming != null) {
                teleportDodge(player, incoming);
                dodgeCooldowns.put(player.getUUID(), DODGE_COOLDOWN_TICKS);
            }
        }
    }

    /**
     * Scans nearby projectiles and checks if any are on a trajectory that will
     * hit the player within the next few ticks.
     */
    private static Projectile findIncomingProjectile(Player player) {
        AABB searchArea = player.getBoundingBox().inflate(16.0);
        List<Projectile> nearby = player.level().getEntitiesOfClass(Projectile.class, searchArea,
                p -> p.isAlive() && p.getDeltaMovement().lengthSqr() > 0.01);

        for (Projectile projectile : nearby) {
            if (willHitPlayerSoon(projectile, player)) {
                return projectile;
            }
        }
        return null;
    }

    /**
     * Predicts the projectile's position over the next few ticks and checks
     * if it will pass close enough to the player's hitbox to be considered a hit.
     */
    private static boolean willHitPlayerSoon(Projectile projectile, Player player) {
        Vec3 pos = projectile.position();
        Vec3 velocity = projectile.getDeltaMovement();

        if (velocity.lengthSqr() < 1.0E-5) return false;

        for (int tick = 1; tick <= 6; tick++) {
            pos = pos.add(velocity);
            double distSqr = pos.distanceToSqr(player.position().add(0, player.getBbHeight() / 2, 0));
            if (distSqr < 1.5 * 1.5) {
                return true;
            }
        }
        return false;
    }

    private static void applyOrRemoveBuffs(Player player, boolean shouldHaveBuff) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

        if (shouldHaveBuff) {
            if (health != null && health.getModifier(HEALTH_BUFF_UUID) == null) {
                health.addTransientModifier(new AttributeModifier(
                        HEALTH_BUFF_UUID, "Seer sword health", BUFF_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (speed != null && speed.getModifier(SPEED_BUFF_UUID) == null) {
                speed.addTransientModifier(new AttributeModifier(
                        SPEED_BUFF_UUID, "Seer sword speed", BUFF_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            if (health != null && health.getModifier(HEALTH_BUFF_UUID) != null) {
                health.removeModifier(HEALTH_BUFF_UUID);
            }
            if (speed != null && speed.getModifier(SPEED_BUFF_UUID) != null) {
                speed.removeModifier(SPEED_BUFF_UUID);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        boolean hasSword = player.getMainHandItem().getItem() instanceof SeerSwordItem
                || player.getOffhandItem().getItem() instanceof SeerSwordItem;
        if (!hasSword) return;

        if (dodgeCooldowns.getOrDefault(player.getUUID(), 0) > 0) return;

        Projectile projectile = event.getProjectile();
        projectile.discard();

        teleportDodge(player, projectile);
        dodgeCooldowns.put(player.getUUID(), DODGE_COOLDOWN_TICKS);
    }

    private static void teleportDodge(Player player, Entity projectile) {
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 perpendicular = new Vec3(-velocity.z, 0, velocity.x);
        if (perpendicular.lengthSqr() < 1.0E-4) {
            perpendicular = new Vec3(1, 0, 0);
        } else {
            perpendicular = perpendicular.normalize();
        }

        double side = player.getRandom().nextBoolean() ? 1.0 : -1.0;
        Vec3 desired = player.position().add(perpendicular.scale(DODGE_DISTANCE * side));
        Vec3 dest = findSafeSpot(player, desired);

        Vec3 before = player.position();
        player.teleportTo(dest.x, dest.y, dest.z);

        Level level = player.level();
        level.playSound(null, before.x, before.y, before.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, before.x, before.y + 1, before.z, 20, 0.3, 0.5, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 20, 0.3, 0.5, 0.3, 0.05);
        }
    }

    private static Vec3 findSafeSpot(Player player, Vec3 proposed) {
        for (double yOffset : new double[]{0, 1, -1, 2, -2}) {
            Vec3 candidate = new Vec3(proposed.x, proposed.y + yOffset, proposed.z);
            AABB box = player.getBoundingBox().move(candidate.subtract(player.position()));
            if (player.level().noCollision(player, box)) {
                return candidate;
            }
        }
        return player.position();
    }
}