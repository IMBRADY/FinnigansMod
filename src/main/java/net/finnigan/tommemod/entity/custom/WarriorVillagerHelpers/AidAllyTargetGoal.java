package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Puts an idle Warrior onto whoever the village last called for help about, the moment it can see
 * them. Fills the gap DefendVillagersTargetGoal leaves: that one reacts to a blow being struck within
 * sight, so a Warrior that arrives during a lull - or after the villager it came to save is already
 * dead - would otherwise wander up to the attacker and do nothing.
 */
public class AidAllyTargetGoal extends TargetGoal {

    private final WarriorVillagerEntity warrior;
    private final TargetingConditions aidConditions = TargetingConditions.forCombat();

    @Nullable
    private LivingEntity aidTarget;

    public AidAllyTargetGoal(WarriorVillagerEntity warrior) {
        super(warrior, false, false);
        this.warrior = warrior;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        aidTarget = null;

        // Only the unoccupied answer a call, so a Warrior mid-fight is never pulled off it.
        LivingEntity current = warrior.getTarget();
        if (current != null && current.isAlive()) return false;

        VillageAlarm.DistressCall call =
                VillageAlarm.current(warrior.getVillageId(), warrior.level().getGameTime());
        if (call == null) return false;

        // Requires line of sight, so a Warrior still marching over doesn't lock onto something through
        // a wall; TargetGoal drops anything past follow range on its own from here.
        if (!canAttack(call.attacker(), aidConditions)) return false;

        aidTarget = call.attacker();
        return true;
    }

    @Override
    public void start() {
        warrior.setTarget(aidTarget);
        super.start();
    }
}
