package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * Vanilla's own RangedCrossbowAttackGoal is bound to T extends Monster, which the (friendly)
 * WarriorVillagerEntity isn't - this is an exact copy of its logic with that bound relaxed to Mob,
 * so any Mob implementing RangedAttackMob + CrossbowAttackMob can use it. Also adds the strafing
 * (side-step/back-off) movement vanilla's RangedBowAttackGoal has but RangedCrossbowAttackGoal never
 * did - without it the Warrior just stood still in place once in range, instead of moving like a
 * Skeleton while it reloads.
 */
public class GenericRangedCrossbowAttackGoal<T extends Mob & RangedAttackMob & CrossbowAttackMob> extends Goal {

    private static final UniformInt PATHFINDING_DELAY_RANGE = TimeUtil.rangeOfSeconds(1, 2);

    private final T mob;
    private CrossbowState crossbowState = CrossbowState.UNCHARGED;
    private final double speedModifier;
    private final float attackRadiusSqr;
    private int seeTime;
    private int attackDelay;
    private int updatePathDelay;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public GenericRangedCrossbowAttackGoal(T mob, double speedModifier, float attackRadius) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.isValidTarget() && this.isHoldingCrossbow();
    }

    private boolean isHoldingCrossbow() {
        return this.mob.isHolding(is -> is.getItem() instanceof CrossbowItem);
    }

    @Override
    public boolean canContinueToUse() {
        return this.isValidTarget() && (this.canUse() || !this.mob.getNavigation().isDone()) && this.isHoldingCrossbow();
    }

    private boolean isValidTarget() {
        return this.mob.getTarget() != null && this.mob.getTarget().isAlive();
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.mob.setTarget(null);
        this.seeTime = 0;
        this.strafingTime = -1;
        if (this.mob.isUsingItem()) {
            this.mob.stopUsingItem();
            this.mob.setChargingCrossbow(false);
            CrossbowItem.setCharged(this.mob.getUseItem(), false);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        boolean canSee = this.mob.getSensing().hasLineOfSight(target);
        boolean couldSee = this.seeTime > 0;
        if (canSee != couldSee) {
            this.seeTime = 0;
        }

        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        double distSqr = this.mob.distanceToSqr(target);
        boolean shouldReposition = (distSqr > (double) this.attackRadiusSqr || this.seeTime < 5) && this.attackDelay == 0;
        if (shouldReposition) {
            --this.updatePathDelay;
            if (this.updatePathDelay <= 0) {
                this.mob.getNavigation().moveTo(target, this.canRun() ? this.speedModifier : this.speedModifier * 0.5D);
                this.updatePathDelay = PATHFINDING_DELAY_RANGE.sample(this.mob.getRandom());
            }
            this.strafingTime = -1;
        } else {
            this.updatePathDelay = 0;
            this.mob.getNavigation().stop();
            ++this.strafingTime;
        }

        if (this.strafingTime >= 20) {
            if (this.mob.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if (this.mob.getRandom().nextFloat() < 0.3F) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }

        if (this.strafingTime > -1) {
            if (distSqr > (double) (this.attackRadiusSqr * 0.75F)) {
                this.strafingBackwards = false;
            } else if (distSqr < (double) (this.attackRadiusSqr * 0.25F)) {
                this.strafingBackwards = true;
            }
            this.mob.getMoveControl().strafe(this.strafingBackwards ? -0.5F : 0.5F, this.strafingClockwise ? 0.5F : -0.5F);
        }

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.crossbowState == CrossbowState.UNCHARGED) {
            if (!shouldReposition) {
                this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof CrossbowItem));
                this.crossbowState = CrossbowState.CHARGING;
                this.mob.setChargingCrossbow(true);
            }
        } else if (this.crossbowState == CrossbowState.CHARGING) {
            if (!this.mob.isUsingItem()) {
                this.crossbowState = CrossbowState.UNCHARGED;
            }

            int ticksUsing = this.mob.getTicksUsingItem();
            ItemStack usedItem = this.mob.getUseItem();
            if (ticksUsing >= CrossbowItem.getChargeDuration(usedItem)) {
                this.mob.releaseUsingItem();
                this.crossbowState = CrossbowState.CHARGED;
                this.attackDelay = 20 + this.mob.getRandom().nextInt(20);
                this.mob.setChargingCrossbow(false);
            }
        } else if (this.crossbowState == CrossbowState.CHARGED) {
            --this.attackDelay;
            if (this.attackDelay == 0) {
                this.crossbowState = CrossbowState.READY_TO_ATTACK;
            }
        } else if (this.crossbowState == CrossbowState.READY_TO_ATTACK && canSee) {
            this.mob.performRangedAttack(target, 1.0F);
            ItemStack heldStack = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof CrossbowItem));
            CrossbowItem.setCharged(heldStack, false);
            this.crossbowState = CrossbowState.UNCHARGED;
        }
    }

    private boolean canRun() {
        return this.crossbowState == CrossbowState.UNCHARGED;
    }

    private enum CrossbowState {
        UNCHARGED, CHARGING, CHARGED, READY_TO_ATTACK
    }
}
