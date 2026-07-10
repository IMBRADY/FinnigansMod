package net.finnigan.tommemod.entity.custom.UndeadSwordHelpers;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SoulFireballAttackGoal extends Goal {

    private final PathfinderMob mob;
    private int attackCooldown;
    private static final int MAX_COOLDOWN = 40; // 2 seconds

    public SoulFireballAttackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.getTarget().isAlive();
    }

    @Override
    public void start() {
        attackCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (--attackCooldown <= 0 && mob.hasLineOfSight(target)) {
            attackCooldown = MAX_COOLDOWN;

            Vec3 lookVec = new Vec3(
                    target.getX() - mob.getX(),
                    target.getY(0.5) - mob.getY(0.5),
                    target.getZ() - mob.getZ()
            ).normalize();

            LargeFireball fireball = new LargeFireball(mob.level(), mob,
                    lookVec.x, lookVec.y, lookVec.z, 1);
            fireball.setPos(mob.getX(), mob.getEyeY(), mob.getZ());
            mob.level().addFreshEntity(fireball);
        }
    }
}