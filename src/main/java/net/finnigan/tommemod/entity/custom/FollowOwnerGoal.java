package net.finnigan.tommemod.entity.custom;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class FollowOwnerGoal extends Goal {

    private final PathfinderMob mob;
    private final Player owner;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance = 3.0f;
    private final PathNavigation navigation;
    private int timeToRecalcPath;

    public FollowOwnerGoal(PathfinderMob mob, Player owner, double speedModifier, float startDistance) {
        this.mob = mob;
        this.owner = owner;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (owner == null || !owner.isAlive()) return false;
        return mob.distanceToSqr(owner) > (startDistance * startDistance);
    }

    @Override
    public boolean canContinueToUse() {
        return owner.isAlive()
                && !navigation.isDone()
                && mob.distanceToSqr(owner) > (stopDistance * stopDistance);
    }

    @Override
    public void start() {
        timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        navigation.stop();
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(owner, 10.0f, mob.getMaxHeadXRot());
        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = 10;
            if (!mob.isLeashed()) {
                navigation.moveTo(owner, speedModifier);
            }
        }
    }
}