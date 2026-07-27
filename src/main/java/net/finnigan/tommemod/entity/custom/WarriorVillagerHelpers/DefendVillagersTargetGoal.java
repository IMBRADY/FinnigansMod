package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generalizes vanilla's IronGolem-only DefendVillageTargetGoal into reactive "someone/something is
 * currently hurting a nearby villager" targeting, usable by any Mob.
 */
public class DefendVillagersTargetGoal extends TargetGoal {

    private static final double SEARCH_RADIUS = 24.0;

    private final Map<UUID, Integer> handledTimestamps = new HashMap<>();
    private LivingEntity foundAttacker;

    public DefendVillagersTargetGoal(Mob mob) {
        super(mob, false, true);
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        foundAttacker = null;
        AABB box = mob.getBoundingBox().inflate(SEARCH_RADIUS);

        List<LivingEntity> victims = new ArrayList<>();
        victims.addAll(mob.level().getEntitiesOfClass(Villager.class, box));
        victims.addAll(mob.level().getEntitiesOfClass(ElderVillagerEntity.class, box));
        victims.addAll(mob.level().getEntitiesOfClass(WarriorVillagerEntity.class, box));

        double bestDistSqr = Double.MAX_VALUE;
        for (LivingEntity victim : victims) {
            LivingEntity attacker = victim.getLastHurtByMob();
            if (attacker == null || !attacker.isAlive()) continue;
            if (attacker instanceof Villager || attacker instanceof ElderVillagerEntity
                    || attacker instanceof WarriorVillagerEntity || attacker instanceof IronGolem) {
                continue;
            }

            int timestamp = victim.getLastHurtByMobTimestamp();
            Integer handled = handledTimestamps.get(victim.getUUID());
            if (handled != null && handled == timestamp) continue;

            double distSqr = attacker.distanceToSqr(mob);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                foundAttacker = attacker;
                handledTimestamps.put(victim.getUUID(), timestamp);
            }
        }

        return foundAttacker != null;
    }

    @Override
    public void start() {
        mob.setTarget(foundAttacker);
        super.start();
    }
}
