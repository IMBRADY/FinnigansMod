package net.finnigan.tommemod.entity.custom.UndeadSwordHelpers;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SoulFlyingStrollGoal extends Goal {

    private final PathfinderMob mob;
    private final double speedModifier;

    public SoulFlyingStrollGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && mob.getNavigation().isDone() && mob.getRandom().nextInt(30) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        double x = mob.getX() + (mob.getRandom().nextDouble() * 2.0 - 1.0) * 8.0;
        double y = mob.getY() + (mob.getRandom().nextDouble() * 2.0 - 1.0) * 4.0;
        double z = mob.getZ() + (mob.getRandom().nextDouble() * 2.0 - 1.0) * 8.0;
        mob.getNavigation().moveTo(x, y, z, speedModifier);
    }
}