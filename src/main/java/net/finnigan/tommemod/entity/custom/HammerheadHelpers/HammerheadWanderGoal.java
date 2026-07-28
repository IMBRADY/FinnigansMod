package net.finnigan.tommemod.entity.custom.HammerheadHelpers;

import net.finnigan.tommemod.entity.custom.HammerheadSharkEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Unlike vanilla's RandomSwimmingGoal (which rolls a random chance to start a new path, leaving small
 * gaps of stillness), this immediately queues a new destination the instant the current one finishes -
 * the shark should never stop moving while it has no target.
 */
public class HammerheadWanderGoal extends Goal {

    private static final double SPEED = 1.0;

    private final HammerheadSharkEntity mob;

    public HammerheadWanderGoal(HammerheadSharkEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null;
    }

    @Override
    public void start() {
        pickNewDestination();
    }

    @Override
    public void tick() {
        if (mob.getNavigation().isDone()) {
            pickNewDestination();
        }
    }

    private void pickNewDestination() {
        Vec3 pos = BehaviorUtils.getRandomSwimmablePos(mob, 10, 7);
        if (pos != null) {
            mob.getNavigation().moveTo(pos.x, pos.y, pos.z, SPEED);
        }
    }
}
