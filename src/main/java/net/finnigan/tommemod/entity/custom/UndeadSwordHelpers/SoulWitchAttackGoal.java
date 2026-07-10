package net.finnigan.tommemod.entity.custom.UndeadSwordHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.EnumSet;

public class SoulWitchAttackGoal extends Goal {

    private final PathfinderMob mob;
    private int attackCooldown;
    private static final int MAX_COOLDOWN = 60; // 3 seconds

    public SoulWitchAttackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.getTarget().isAlive();
    }

    @Override
    public void start() {
        attackCooldown = 20; // small initial delay before first throw
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double distSqr = mob.distanceToSqr(target);

        if (--attackCooldown <= 0 && distSqr < 256.0 && mob.hasLineOfSight(target)) {
            attackCooldown = MAX_COOLDOWN;

            ThrownPotion potion = new ThrownPotion(mob.level(), mob);
            potion.setItem(PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(Items.SPLASH_POTION), Potions.HARMING));
            potion.setPos(mob.getX(), mob.getEyeY() - 0.1, mob.getZ());

            double dx = target.getX() - mob.getX();
            double dy = target.getY(0.5) - potion.getY();
            double dz = target.getZ() - mob.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            potion.shoot(dx, dy + horizontalDist * 0.33, dz, 0.75F, 8.0F); // change 0.33 for more distance on throw

            mob.level().addFreshEntity(potion);
            mob.level().broadcastEntityEvent(mob, (byte) 15);
        }
    }
}