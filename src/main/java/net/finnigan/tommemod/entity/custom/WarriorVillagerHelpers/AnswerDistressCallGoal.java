package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sends an idle Warrior to the aid of one of its own village who is being attacked, from far enough
 * away that it could not possibly have seen it happen.
 *
 * Only the walking is here. Once the Warrior arrives, AidAllyTargetGoal - or simply seeing the fight
 * for itself, through DefendVillagersTargetGoal - is what puts it into the fight.
 */
public class AnswerDistressCallGoal extends MarchToPositionGoal {

    /** How far a cry for help carries, as a multiple of the Warrior's own follow range. */
    private static final double ALERT_RANGE_MULTIPLIER = 3.0;

    public AnswerDistressCallGoal(WarriorVillagerEntity warrior, double speedModifier) {
        super(warrior, speedModifier);
    }

    @Nullable
    @Override
    protected BlockPos destination() {
        // Its own village only - a Warrior owes nothing to the next village over.
        VillageAlarm.DistressCall call =
                VillageAlarm.current(warrior.getVillageId(), warrior.level().getGameTime());
        if (call == null) return null;

        double alertRange = warrior.getAttributeValue(Attributes.FOLLOW_RANGE) * ALERT_RANGE_MULTIPLIER;
        if (warrior.distanceToSqr(Vec3.atCenterOf(call.where())) > alertRange * alertRange) return null;

        return call.where();
    }
}
