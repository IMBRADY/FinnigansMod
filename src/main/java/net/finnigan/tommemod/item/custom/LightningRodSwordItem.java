package net.finnigan.tommemod.item.custom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.finnigan.tommemod.event.ChainLightningHandler;


import java.util.List;

public class LightningRodSwordItem extends SwordItem {

    private static final double BEAM_LENGTH = 50.0;       // how far the beam reaches, in blocks
    private static final double BOLT_SPACING = 2.5;       // distance between each visual bolt along the beam
    private static final double BEAM_HIT_RADIUS = 2;     // how close an entity must be to the beam line to get hit
    private static final float DAMAGE_PER_USE = 20.0F;     // total damage dealt once per entity, not per bolt
    private static final int COOLDOWN_TICKS = 60;

    private static final int CHAIN_HOPS_FROM_BEAM = 4; // extra jumps beyond the direct beam hits

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public LightningRodSwordItem(Tier tier, int attackDamage, float attackSpeed, Item.Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    private void spawnLightningBeamParticles(Level level, Vec3 startPos, Vec3 endPos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        double totalDistance = startPos.distanceTo(endPos);
        int particleCount = Math.max(10, (int) (totalDistance * 4)); // density along the beam

        Vec3 direction = endPos.subtract(startPos).normalize();

        // Two perpendicular axes so we can jitter the beam sideways in 3D, not just on one axis
        Vec3 arbitrary = Math.abs(direction.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perpendicularA = direction.cross(arbitrary).normalize();
        Vec3 perpendicularB = direction.cross(perpendicularA).normalize();

        for (int i = 0; i <= particleCount; i++) {
            double t = (double) i / particleCount;
            Vec3 pointOnLine = startPos.lerp(endPos, t);

            double jitterAmount = 0.15; // how jagged the bolt looks; raise for a wilder arc
            double jitterA = (level.random.nextDouble() - 0.5) * jitterAmount;
            double jitterB = (level.random.nextDouble() - 0.5) * jitterAmount;

            Vec3 jitteredPoint = pointOnLine
                    .add(perpendicularA.scale(jitterA))
                    .add(perpendicularB.scale(jitterB));

            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    jitteredPoint.x, jitteredPoint.y, jitteredPoint.z,
                    2, 0, 0, 0, 0);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            Vec3 startPos = player.getEyePosition();
            Vec3 endPos = raycastEndpoint(level, player, startPos);

            spawnLightningBeamParticles(level, startPos, endPos);
            damageEntitiesAlongBeam(level, player, startPos, endPos);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.0F);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.swing(hand);
        return InteractionResultHolder.success(stack);
    }

    private Vec3 raycastEndpoint(Level level, Player player, Vec3 startPos) {
        Vec3 lookVec = player.getLookAngle();
        Vec3 maxEndPos = startPos.add(lookVec.scale(BEAM_LENGTH));

        BlockHitResult hit = level.clip(new ClipContext(
                startPos, maxEndPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : maxEndPos;
    }

    private void spawnBoltsAlongBeam(Level level, Vec3 startPos, Vec3 endPos) {
        double totalDistance = startPos.distanceTo(endPos);
        int boltCount = Math.max(1, (int) (totalDistance / BOLT_SPACING));

        for (int i = 0; i <= boltCount; i++) {
            double t = (double) i / boltCount;
            Vec3 boltPos = startPos.lerp(endPos, t);

            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(boltPos.x, boltPos.y, boltPos.z);
                bolt.setVisualOnly(true); // no fire, no mob transformation, no vanilla damage — we handle damage ourselves
                level.addFreshEntity(bolt);
            }
        }
    }

    private void damageEntitiesAlongBeam(Level level, Player player, Vec3 startPos, Vec3 endPos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        AABB sweepBox = new AABB(startPos, endPos).inflate(BEAM_HIT_RADIUS);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e != player && e.isAlive());

        java.util.Set<java.util.UUID> hitAlongBeam = new java.util.HashSet<>();

        for (LivingEntity target : targets) {
            if (distanceFromPointToSegment(target.position(), startPos, endPos) <= BEAM_HIT_RADIUS) {
                target.hurt(level.damageSources().lightningBolt(), DAMAGE_PER_USE);
                hitAlongBeam.add(target.getUUID());
            }
        }

        if (!hitAlongBeam.isEmpty() && level.isThundering()) {
            ChainLightningHandler.startChain(serverLevel, endPos, hitAlongBeam, CHAIN_HOPS_FROM_BEAM);
        }
    }

    // Standard point-to-line-segment distance check, needed because AABB alone
    // would count entities near the beam's bounding box corners, not just near the actual line.
    private double distanceFromPointToSegment(Vec3 point, Vec3 segStart, Vec3 segEnd) {
        Vec3 segment = segEnd.subtract(segStart);
        double segmentLengthSq = segment.lengthSqr();

        if (segmentLengthSq == 0) return point.distanceTo(segStart);

        double t = point.subtract(segStart).dot(segment) / segmentLengthSq;
        t = Math.max(0, Math.min(1, t));

        Vec3 closestPoint = segStart.add(segment.scale(t));
        return point.distanceTo(closestPoint);
    }
}