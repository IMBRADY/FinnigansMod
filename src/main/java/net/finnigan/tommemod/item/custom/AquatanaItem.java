package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.finnigan.tommemod.item.custom.AquatanaHelpers.DashTrailManager;
import net.finnigan.tommemod.particle.ModParticleTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AquatanaItem extends SwordItem {

    private static final double DASH_DISTANCE = 12.0;
    private static final float DASH_HIT_DAMAGE = 10.0F;
    private static final int COOLDOWN_TICKS = 60; // 3 seconds
    private static final double TRAIL_POINT_SPACING = 1.0; // one trail segment per block of travel

    public AquatanaItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    private static final RegistryObject<SimpleParticleType>[] AQUATANA_VARIANTS = new RegistryObject[]{
            ModParticleTypes.WAVE_1, ModParticleTypes.WAVE_2, ModParticleTypes.WAVE_3, ModParticleTypes.WAVE_4, ModParticleTypes.WAVE_5,
            ModParticleTypes.FOAM_1, ModParticleTypes.FOAM_2, ModParticleTypes.FOAM_3, ModParticleTypes.FOAM_4, ModParticleTypes.FOAM_5
    };

    public static SimpleParticleType randomAquatanaParticle(RandomSource random) {
        return AQUATANA_VARIANTS[random.nextInt(AQUATANA_VARIANTS.length)].get();
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    /** Shared filter: valid dash/trail targets exclude players, wolves, villagers, and soul allies. */
    public static boolean isValidDashTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof Player) return false;
        //if (entity instanceof Wolf) return false;
        //if (entity instanceof Villager) return false;
        if (entity.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) return false;
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            performDash((ServerLevel) level, player);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return InteractionResultHolder.success(stack);
    }

    private void performDash(ServerLevel level, Player player) {
        Vec3 start = player.position();
        Vec3 lookVec = player.getLookAngle();
        Vec3 desiredEnd = start.add(lookVec.x * DASH_DISTANCE, lookVec.y * DASH_DISTANCE, lookVec.z * DASH_DISTANCE);

        ClipContext clipContext = new ClipContext(start, desiredEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        HitResult blockHit = level.clip(clipContext);
        Vec3 actualEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : desiredEnd;

        // Damage every valid enemy the dash line passes through
        Set<Entity> alreadyHit = new HashSet<>();
        AABB pathBox = new AABB(start, actualEnd).inflate(1.0);
        List<Entity> candidates = level.getEntities(player, pathBox, AquatanaItem::isValidDashTarget);

        for (Entity entity : candidates) {
            if (alreadyHit.contains(entity)) continue;
            if (isOnLine(start, actualEnd, entity.position(), 1.2)) {
                DamageSource source = player.damageSources().playerAttack(player);
                entity.hurt(source, DASH_HIT_DAMAGE);
                alreadyHit.add(entity);
            }
        }

        // Lay down trail segments along the path
        double totalDist = start.distanceTo(actualEnd);
        int segmentCount = (int) Math.max(1, totalDist / TRAIL_POINT_SPACING);
        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / segmentCount;
            Vec3 point = start.lerp(actualEnd, t);
            DashTrailManager.addSegment(level, point, player);
        }

        Vec3 safeEnd = findSafeSpot(player, actualEnd);
        player.teleportTo(safeEnd.x, safeEnd.y, safeEnd.z);

        level.playSound(null, safeEnd.x, safeEnd.y, safeEnd.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.0F);

        spawnTrailParticles(level, start, actualEnd);
    }

    private static boolean isOnLine(Vec3 start, Vec3 end, Vec3 point, double tolerance) {
        Vec3 line = end.subtract(start);
        double lineLengthSqr = line.lengthSqr();
        if (lineLengthSqr < 1.0E-4) return point.distanceTo(start) <= tolerance;

        double t = point.subtract(start).dot(line) / lineLengthSqr;
        t = Math.max(0, Math.min(1, t));
        Vec3 closestPointOnLine = start.add(line.scale(t));
        return closestPointOnLine.distanceTo(point) <= tolerance;
    }

    private void spawnTrailParticles(ServerLevel level, Vec3 start, Vec3 end) {
        double dist = start.distanceTo(end);
        int steps = (int) Math.max(1, dist * 2);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 point = start.lerp(end, t);
            level.sendParticles(randomAquatanaParticle(level.getRandom()), point.x, point.y + 0.1, point.z, 6, 0.2, 0.1, 0.2, 0.05);
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