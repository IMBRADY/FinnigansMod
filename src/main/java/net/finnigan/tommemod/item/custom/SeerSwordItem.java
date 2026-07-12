package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.SetPlayerRotationPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SeerSwordItem extends SwordItem {

    private static final double TARGET_RANGE = 15.0;
    private static final double BEHIND_DISTANCE = 1.5;
    private static final double FORWARD_DISTANCE = 12.0;
    private static final int COOLDOWN_TICKS = 40;

    public SeerSwordItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            Entity target = getTargetedEntity(player, TARGET_RANGE);

            if (target != null) {
                teleportBehind(player, target);
            } else {
                teleportForward(player);
            }

            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return InteractionResultHolder.success(stack);
    }

    private static Entity getTargetedEntity(Player player, double range) {
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getLookAngle();
        Vec3 reachVec = eyePos.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                reachVec,
                searchBox,
                entity -> !entity.isSpectator() && entity.isPickable() && entity != player,
                range * range
        );

        return hit != null ? hit.getEntity() : null;
    }

    private static void lookAt(Player player, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - (from.y + player.getEyeHeight());
        double dz = to.z - from.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontalDist) * (180.0 / Math.PI));

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SetPlayerRotationPacket(yaw, pitch)
            );
        }
    }

    private static void teleportBehind(Player player, Entity target) {
        double yawRad = Math.toRadians(target.getYRot());
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);

        double destX = target.getX() - dx * BEHIND_DISTANCE;
        double destY = target.getY();
        double destZ = target.getZ() - dz * BEHIND_DISTANCE;

        Vec3 dest = findSafeSpot(player, new Vec3(destX, destY, destZ));
        doTeleport(player, dest);

        lookAt(player, dest, target.getEyePosition(1.0F));
    }

    /** Teleports the player forward, stopping short if blocked by terrain. */
    private static void teleportForward(Player player) {
        Vec3 start = player.position();
        Vec3 lookVec = player.getLookAngle();
        Vec3 desired = start.add(lookVec.x * FORWARD_DISTANCE, lookVec.y * FORWARD_DISTANCE, lookVec.z * FORWARD_DISTANCE);

        // Raytrace against blocks so we don't teleport through walls
        ClipContext clipContext = new ClipContext(start, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        HitResult blockHit = player.level().clip(clipContext);

        Vec3 target = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : desired;
        Vec3 dest = findSafeSpot(player, target);
        doTeleport(player, dest);
    }

    /**
     * Checks a small range of Y offsets near the proposed spot to avoid landing inside blocks
     * or in the void. Falls back to the player's current position if nothing safe is found.
     */
    private static Vec3 findSafeSpot(Player player, Vec3 proposed) {
        for (double yOffset : new double[]{0, 1, -1, 2, -2}) {
            Vec3 candidate = new Vec3(proposed.x, proposed.y + yOffset, proposed.z);
            AABB box = player.getBoundingBox().move(candidate.subtract(player.position()));
            if (player.level().noCollision(player, box)) {
                return candidate;
            }
        }
        return player.position(); // no safe spot found — stay put rather than teleport into a wall
    }

    private static void doTeleport(Player player, Vec3 dest) {
        Vec3 before = player.position();
        player.teleportTo(dest.x, dest.y, dest.z);

        Level level = player.level();
        if (!level.isClientSide) {
            level.playSound(null, before.x, before.y, before.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
            ((net.minecraft.server.level.ServerLevel) level).sendParticles(
                    ParticleTypes.PORTAL, before.x, before.y + 1, before.z, 20, 0.3, 0.5, 0.3, 0.05);
            ((net.minecraft.server.level.ServerLevel) level).sendParticles(
                    ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 20, 0.3, 0.5, 0.3, 0.05);
        }
    }
}