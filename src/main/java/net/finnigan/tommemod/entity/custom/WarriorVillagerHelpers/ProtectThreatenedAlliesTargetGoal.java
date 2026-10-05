package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Puts protecting the village's own ahead of picking fights. Anything nearby that is chasing or
 * attacking a villager, an Elder, another Warrior or this Warrior itself is a threat, and a Warrior
 * busy with something that threatens nobody - a skeleton idling on a hill - drops it for the threat.
 *
 * <p>"Chasing" is read two ways: a mob whose own target is one of the village's, and a villager in
 * vanilla's panic who remembers what it is running from. The second catches the zombie that has
 * only just been spotted, before it has settled on anyone.
 *
 * <p>Plain villagers count double: they cannot fight back, so a zombie on one is more urgent than
 * a zombie on a Warrior the same distance away.
 */
public class ProtectThreatenedAlliesTargetGoal extends TargetGoal {

    private static final double SEARCH_RADIUS = 24.0;
    private static final int SCAN_INTERVAL = 5;

    private final WarriorVillagerEntity warrior;
    private final TargetingConditions conditions = TargetingConditions.forCombat().ignoreLineOfSight().range(SEARCH_RADIUS * 1.5);
    private int cooldown;
    @Nullable
    private LivingEntity threat;

    public ProtectThreatenedAlliesTargetGoal(WarriorVillagerEntity warrior) {
        super(warrior, false, false);
        this.warrior = warrior;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = SCAN_INTERVAL;
        threat = null;

        LivingEntity current = warrior.getTarget();
        if (current != null && current.isAlive() && isThreat(current)) return false;

        threat = findThreat();
        return threat != null && threat != current;
    }

    @Override
    public void start() {
        warrior.setTarget(threat);
        super.start();
    }

    @Nullable
    private LivingEntity findThreat() {
        AABB box = warrior.getBoundingBox().inflate(SEARCH_RADIUS, 8.0, SEARCH_RADIUS);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Mob mob : warrior.level().getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && !isVillageSide(m))) {
            LivingEntity victim = mob.getTarget();
            if (victim == null || !victim.isAlive() || !isVillageSide(victim)) continue;
            double score = score(mob, victim);
            if (score < bestScore && canAttack(mob, conditions)) {
                bestScore = score;
                best = mob;
            }
        }

        for (Villager villager : warrior.level().getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && v.getBrain().isActive(Activity.PANIC))) {
            LivingEntity chaser = villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
                    .or(() -> villager.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY))
                    .orElse(null);
            if (chaser == null || !chaser.isAlive() || isVillageSide(chaser)) continue;
            double score = score(chaser, villager);
            if (score < bestScore && canAttack(chaser, conditions)) {
                bestScore = score;
                best = chaser;
            }
        }
        return best;
    }

    private double score(LivingEntity attacker, LivingEntity victim) {
        double distance = warrior.distanceToSqr(attacker);
        return victim instanceof Villager ? distance * 0.5 : distance;
    }

    /** Whether this entity is after one of the village's own right now. */
    private static boolean isThreat(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            LivingEntity victim = mob.getTarget();
            if (victim != null && victim.isAlive() && isVillageSide(victim)) return true;
        }
        LivingEntity lastHit = entity.getLastHurtMob();
        return lastHit != null && lastHit.isAlive() && isVillageSide(lastHit)
                && entity.tickCount - entity.getLastHurtMobTimestamp() < 100;
    }

    private static boolean isVillageSide(LivingEntity entity) {
        return entity instanceof Villager || entity instanceof ElderVillagerEntity
                || entity instanceof WarriorVillagerEntity || entity instanceof IronGolem;
    }
}
