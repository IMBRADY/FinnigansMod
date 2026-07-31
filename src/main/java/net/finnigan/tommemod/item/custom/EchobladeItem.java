package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.EchobladeEventHelpers.EchobladeStunHandler;
import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Echoblade: right click fires a Warden-style forward sonic beam that pierces through every valid
 * target in a narrow corridor, damaging and briefly stunning each one.
 * Passive: hostile mobs targeting the holder lose track of them once they exceed 50% of the mob's
 * own vanilla follow range (see EchobladePassiveHandler); nearby moving hostile mobs get a subtle
 * client-only glow marker so the holder can spot them (see EchobladeGlowClientHandler).
 */
public class EchobladeItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 100; // 5 seconds
    private static final double BEAM_RANGE = 20.0;
    private static final double BEAM_HALF_WIDTH = 1.0; // ~2 block wide corridor
    private static final float BEAM_DAMAGE = 10.0F;
    private static final int STUN_DURATION_TICKS = 15; // 0.75s
    private static final int PARTICLE_STEPS = 5;

    public EchobladeItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof EchobladeItem
                || player.getOffhandItem().getItem() instanceof EchobladeItem;
    }

    /** Mirrors FireKatanaItem.isValidFireTarget's exclusion list — same "who custom abilities can hit" convention. */
    public static boolean isValidBeamTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof Player) return false;
        if (entity instanceof Wolf) return false;
        if (entity instanceof Villager) return false;
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            performSonicBeam((ServerLevel) level, player);
            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
        }

        player.swing(hand);
        return InteractionResultHolder.success(stack);
    }

    private void performSonicBeam(ServerLevel level, Player player) {
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();

        AABB box = player.getBoundingBox().expandTowards(look.scale(BEAM_RANGE)).inflate(BEAM_HALF_WIDTH + 1.0);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, EchobladeItem::isValidBeamTarget);

        DamageSource source = player.damageSources().playerAttack(player);
        for (LivingEntity target : candidates) {
            Vec3 toTarget = target.position().add(0, target.getBbHeight() / 2.0, 0).subtract(start);
            double along = toTarget.dot(look);
            if (along < 0 || along > BEAM_RANGE) continue;

            Vec3 projected = look.scale(along);
            double perpDist = toTarget.subtract(projected).length();
            if (perpDist > BEAM_HALF_WIDTH + target.getBbWidth() / 2.0) continue;

            target.hurt(source, BEAM_DAMAGE);
            EchobladeStunHandler.stun(level, target, STUN_DURATION_TICKS);
        }

        for (int i = 1; i <= PARTICLE_STEPS; i++) {
            Vec3 point = start.add(look.scale(BEAM_RANGE * i / (double) PARTICLE_STEPS));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
