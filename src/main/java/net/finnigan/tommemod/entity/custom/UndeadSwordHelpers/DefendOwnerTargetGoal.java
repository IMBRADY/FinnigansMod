package net.finnigan.tommemod.entity.custom.UndeadSwordHelpers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class DefendOwnerTargetGoal extends TargetGoal {

    private final PathfinderMob mob;
    private final Player owner;
    private LivingEntity ownerLastHurtBy;
    private int timestamp;

    public DefendOwnerTargetGoal(PathfinderMob mob, Player owner) {
        super(mob, false);
        this.mob = mob;
        this.owner = owner;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (owner == null || !owner.isAlive()) return false;

        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker == null) return false;
        if (attacker.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) return false; // never target other allies

        int currentTimestamp = owner.getLastHurtByMobTimestamp();
        if (currentTimestamp == this.timestamp) return false;

        this.ownerLastHurtBy = attacker;
        if (ownerLastHurtBy.isRemoved() || !ownerLastHurtBy.isAlive()) return false;
        if (ownerLastHurtBy.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) return false;
        double range = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        return mob.distanceToSqr(ownerLastHurtBy) <= range * range;
    }

    @Override
    public void start() {
        mob.setTarget(ownerLastHurtBy);
        this.timestamp = owner.getLastHurtByMobTimestamp();
        super.start();
    }
}