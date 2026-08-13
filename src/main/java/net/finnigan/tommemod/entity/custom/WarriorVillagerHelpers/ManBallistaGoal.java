package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Sends a Warrior to crew a Ballista instead of charging something it cannot reach.
 *
 * A Ballista does nothing on its own - it has no targeting goals at all, by design - so the only way
 * one ever defends a village unattended is if somebody who lives there climbs into it. This is that
 * somebody. When a Warrior picks up a target too far off to close with, it breaks for the nearest
 * emplacement it is entitled to use, mounts it, and works it: laying the arm on the mark through
 * {@link BallistaEntity#operateAt} and loosing whenever the weapon is wound and lined up.
 *
 * It stays aboard across targets rather than remounting per kill, and gives the seat up the moment the
 * fight comes close enough to answer with the halberd - a Warrior sitting in a Ballista while a raider
 * hits it is worse than useless.
 *
 * Priority sits above the melee and ranged goals so this wins the MOVE/LOOK flags while it runs; the
 * target selector is untouched, so the Warrior's idea of who it is fighting keeps updating underneath.
 */
public class ManBallistaGoal extends Goal {

    /** How far a Warrior will break off to reach an emplacement. */
    private static final double SEARCH_RADIUS = 24.0;
    /** Close enough to climb aboard, squared. Matches the Ballista's own footprint plus a step. */
    private static final double MOUNT_REACH_SQR = 4.0;
    /**
     * Nearer than this and the halberd is the better answer - the arm cannot depress fast enough to
     * hold something already on top of the frame, and a mounted Warrior cannot defend itself.
     */
    private static final double MIN_ENGAGEMENT_DISTANCE = 10.0;
    private static final double MIN_ENGAGEMENT_DISTANCE_SQR = MIN_ENGAGEMENT_DISTANCE * MIN_ENGAGEMENT_DISTANCE;

    private static final double SPEED_MODIFIER = 1.0D;

    /** Give up the run-up after this long: the path is blocked, or something got there first. */
    private static final int APPROACH_TIMEOUT_TICKS = 100;
    /** Kept off ballistas this long after standing down, so a target hovering around the engagement
     * range doesn't make a Warrior climb in and out every other tick. */
    private static final int STAND_DOWN_COOLDOWN_TICKS = 40;

    private final WarriorVillagerEntity warrior;

    @Nullable
    private BallistaEntity ballista;
    private int approachTicks;
    private int cooldownTicks;

    public ManBallistaGoal(WarriorVillagerEntity warrior) {
        this.warrior = warrior;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // Aim is laid a tick at a time. On the alternate ticks where vanilla only runs goals that ask
        // for it, an unasking goal would leave the arm frozen mid-swing and the shot unfired.
        return true;
    }

    @Override
    public boolean canUse() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }
        if (warrior.isPassenger()) return false;

        LivingEntity target = warrior.getTarget();
        if (!isWorthABallista(target)) return false;

        this.ballista = findUsableBallista(target);
        return this.ballista != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (ballista == null || !ballista.isAlive()) return false;

        LivingEntity target = warrior.getTarget();
        if (!isWorthABallista(target)) return false;

        if (isAboard()) return canReachFrom(ballista, target);

        // Still walking to it. Somebody else's Warrior may have taken the seat in the meantime.
        return approachTicks < APPROACH_TIMEOUT_TICKS && !ballista.isVehicle();
    }

    @Override
    public void start() {
        this.approachTicks = 0;
        if (ballista != null) warrior.getNavigation().moveTo(ballista, SPEED_MODIFIER);
    }

    @Override
    public void stop() {
        if (isAboard()) warrior.stopRiding();
        warrior.getNavigation().stop();
        this.ballista = null;
        this.approachTicks = 0;
        this.cooldownTicks = STAND_DOWN_COOLDOWN_TICKS;
    }

    @Override
    public void tick() {
        if (ballista == null) return;

        LivingEntity target = warrior.getTarget();
        if (target == null) return;

        if (!isAboard()) {
            approach();
            return;
        }

        // Aboard and working it. The Warrior turns its own head to the mark as well, so it reads as
        // aiming rather than as a passenger being carried by a weapon that aims itself.
        warrior.getLookControl().setLookAt(target, 30.0F, 30.0F);

        boolean onTarget = ballista.operateAt(warrior, target);
        if (onTarget && ballista.hasLineOfSight(target)) {
            ballista.fireForRider(warrior);
        }
    }

    private void approach() {
        approachTicks++;
        if (ballista == null) return;

        warrior.getLookControl().setLookAt(ballista, 30.0F, 30.0F);

        if (warrior.distanceToSqr(ballista) <= MOUNT_REACH_SQR) {
            warrior.getNavigation().stop();
            warrior.startRiding(ballista);
        } else if (warrior.getNavigation().isDone()) {
            warrior.getNavigation().moveTo(ballista, SPEED_MODIFIER);
        }
    }

    private boolean isAboard() {
        return ballista != null && warrior.getVehicle() == ballista;
    }

    /** Whether a target is the kind of problem a Ballista solves: real, alive, and out of arm's reach. */
    private boolean isWorthABallista(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && warrior.canAttack(target)
                && warrior.distanceToSqr(target) >= MIN_ENGAGEMENT_DISTANCE_SQR;
    }

    /** Whether a given emplacement can actually put a bolt on this target from where it stands. */
    private static boolean canReachFrom(BallistaEntity emplacement, @Nullable LivingEntity target) {
        return target != null
                && emplacement.distanceToSqr(target) <= BallistaEntity.RANGE * BallistaEntity.RANGE;
    }

    @Nullable
    private BallistaEntity findUsableBallista(LivingEntity target) {
        AABB box = warrior.getBoundingBox().inflate(SEARCH_RADIUS);
        List<BallistaEntity> candidates = warrior.level().getEntitiesOfClass(BallistaEntity.class, box,
                emplacement -> emplacement.isAlive()
                        && !emplacement.isVehicle()
                        && canReachFrom(emplacement, target)
                        && mayCrew(emplacement));
        return candidates.stream().min(Comparator.comparingDouble(warrior::distanceToSqr)).orElse(null);
    }

    /**
     * Whether this Warrior is entitled to crew a given emplacement.
     *
     * Scoped the same way the Ballista's own friendly-fire protection is (see
     * BallistaEntity#isProtectedFrom): a Ballista belongs to its owner, and its owner's Warriors are
     * the ones whose village it was built to cover. A Warrior will not climb into a stranger's weapon
     * and start firing it. An unowned one - spawned in, or left by a Chief who has since stood down -
     * is fair game for whoever is defending the ground it stands on.
     */
    private boolean mayCrew(BallistaEntity emplacement) {
        UUID owner = emplacement.getOwnerUUID();
        if (owner == null) return true;
        if (!(warrior.level() instanceof ServerLevel serverLevel)) return false;

        VillageManager manager = VillageManager.get(serverLevel);
        UUID villageId = warrior.getVillageId() != null
                ? warrior.getVillageId()
                : manager.resolveVillage(serverLevel, warrior.blockPosition()).orElse(null);
        if (villageId == null) return false;

        return owner.equals(manager.getChief(villageId).orElse(null));
    }
}
