package net.finnigan.tommemod.entity.custom;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * A passive companion that follows the nearest player while that player is within 30 blocks.
 */
public class HarringtonEntity extends PathfinderMob {

    public static final double DETECTION_RADIUS = 30.0D;
    private static final double FOLLOW_SPEED = 1.0D;
    private static final double FOLLOW_DISTANCE = 3.0D;

    public HarringtonEntity(EntityType<? extends HarringtonEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, DETECTION_RADIUS);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FollowNearbyPlayerGoal());
    }

    private final class FollowNearbyPlayerGoal extends Goal {
        private Player target;
        private int repathDelay;

        private FollowNearbyPlayerGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            target = HarringtonEntity.this.level().getNearestPlayer(HarringtonEntity.this, DETECTION_RADIUS);
            return target != null && !target.isSpectator();
        }

        @Override
        public boolean canContinueToUse() {
            return target != null
                    && target.isAlive()
                    && !target.isSpectator()
                    && HarringtonEntity.this.distanceToSqr(target) <= DETECTION_RADIUS * DETECTION_RADIUS;
        }

        @Override
        public void stop() {
            target = null;
            HarringtonEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            HarringtonEntity.this.getLookControl().setLookAt(target, 10.0F, HarringtonEntity.this.getMaxHeadXRot());

            if (HarringtonEntity.this.distanceToSqr(target) <= FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
                HarringtonEntity.this.getNavigation().stop();
                return;
            }

            if (--repathDelay <= 0) {
                repathDelay = 10;
                HarringtonEntity.this.getNavigation().moveTo(target, FOLLOW_SPEED);
            }
        }
    }
}
