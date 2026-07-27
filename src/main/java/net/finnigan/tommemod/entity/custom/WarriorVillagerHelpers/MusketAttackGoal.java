package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.item.custom.MusketItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A simplified mob-side Musket attack: MusketItem's own firing logic is hardcoded to Player (it
 * reads player.getCooldowns()/player.getInventory() for ammo), which a Mob doesn't have, so this
 * reimplements an equivalent instant-hit shot rather than calling the item directly. Unlike the
 * player version, this doesn't consume Bullet ammo - the Warrior's Musket never runs dry.
 */
public class MusketAttackGoal extends Goal {

    private final Mob mob;
    private final double range;
    private final int cooldownTicks;
    private int cooldown;

    public MusketAttackGoal(Mob mob, double range, int cooldownTicks) {
        this.mob = mob;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isHoldingMusket() {
        return mob.getMainHandItem().getItem() instanceof MusketItem;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && isHoldingMusket();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        cooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSqr = mob.distanceToSqr(target);

        if (distSqr > range * range) {
            mob.getNavigation().moveTo(target, 1.0D);
        } else {
            mob.getNavigation().stop();
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (distSqr <= range * range && mob.getSensing().hasLineOfSight(target)) {
            fire(target);
            cooldown = cooldownTicks;
        }
    }

    private void fire(LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel level)) return;

        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.3F);

        Vec3 muzzle = mob.getEyePosition(1.0F).add(mob.getLookAngle().scale(0.8));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.02, 0.02, 0.02, 0.01);

        target.hurt(mob.level().damageSources().mobAttack(mob), 6.0F);
    }
}
