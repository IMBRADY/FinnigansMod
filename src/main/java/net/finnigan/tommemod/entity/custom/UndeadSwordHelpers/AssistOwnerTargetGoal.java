package net.finnigan.tommemod.entity.custom.UndeadSwordHelpers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class AssistOwnerTargetGoal extends TargetGoal {


    private final PathfinderMob mob;
    private final Player owner;
    private LivingEntity ownerLastHurt;
    private int timestamp;

    public AssistOwnerTargetGoal(PathfinderMob mob, Player owner) {
        super(mob, false);
        this.mob = mob;
        this.owner = owner;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (owner == null || !owner.isAlive()) return false;

        LivingEntity victim = owner.getLastHurtMob();
        if (victim == null) return false;
        if (victim.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) return false; // never target other allies

        int currentTimestamp = owner.getLastHurtMobTimestamp();
        if (currentTimestamp == this.timestamp) return false;

        this.ownerLastHurt = victim;
        return canAttack(ownerLastHurt, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        mob.setTarget(ownerLastHurt);
        this.timestamp = owner.getLastHurtMobTimestamp();
        super.start();
    }
}