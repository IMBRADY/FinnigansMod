package net.finnigan.tommemod.entity.custom.BallistaHelpers;

import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Turns the Ballista on whoever last struck the player who placed it, so an emplacement covers its
 * owner and not merely itself.
 *
 * Vanilla's OwnerHurtByTargetGoal does the same job but is written against TamableAnimal, which this
 * deliberately is not - a Ballista is a building, not a pet.
 */
public class DefendOwnerTargetGoal extends TargetGoal {

    private final BallistaEntity ballista;
    private final TargetingConditions conditions = TargetingConditions.forCombat();

    @Nullable
    private LivingEntity aggressor;
    /** The owner's hurt-stamp already answered for, so one blow doesn't re-trigger every tick. */
    private int handledTimestamp;

    public DefendOwnerTargetGoal(BallistaEntity ballista) {
        super(ballista, false, false);
        this.ballista = ballista;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        aggressor = null;

        UUID ownerId = ballista.getOwnerUUID();
        if (ownerId == null) return false;

        Player owner = ballista.level().getPlayerByUUID(ownerId);
        if (owner == null) return false; // logged off, or too far away to be loaded

        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker == null) return false;

        int timestamp = owner.getLastHurtByMobTimestamp();
        if (timestamp == handledTimestamp) return false;
        if (!canAttack(attacker, conditions)) return false;

        handledTimestamp = timestamp;
        aggressor = attacker;
        return true;
    }

    @Override
    public void start() {
        ballista.setTarget(aggressor);
        super.start();
    }
}
