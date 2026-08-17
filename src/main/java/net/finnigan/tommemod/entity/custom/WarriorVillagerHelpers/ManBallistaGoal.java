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
import java.util.Set;
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
    /**
     * Kept off ballistas this long after standing down, so a target hovering around the engagement
     * range doesn't make a Warrior climb in and out every other tick.
     *
     * Not below 60: vanilla stamps a dismounting passenger with a 60 tick boardingCooldown, and
     * {@code Entity.canRide} silently refuses every remount until it expires. A shorter wait here
     * only buys a Warrior the chance to walk back and be turned away.
     */
    private static final int STAND_DOWN_COOLDOWN_TICKS = 60;

    private final WarriorVillagerEntity warrior;

    @Nullable
    private BallistaEntity ballista;
    private int approachTicks;
    /**
     * Game time the stand-down expires at, rather than a counter ticked down in {@link #canUse()}.
     * Mob.serverAiStep only runs the full goal selector on every other tick, so a counter decremented
     * from canUse runs at half speed and the real wait comes out at twice what it reads as.
     */
    private long standDownUntilTick;

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
        if (warrior.level().getGameTime() < standDownUntilTick) return false;
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
        this.standDownUntilTick = warrior.level().getGameTime() + STAND_DOWN_COOLDOWN_TICKS;
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
            // Only give up the walk if the seat was actually taken. startRiding refuses silently -
            // a boardingCooldown still running, or another Warrior aboard as of this tick - and
            // stopping the navigation on a refusal leaves this one standing against the frame doing
            // nothing until the approach times out.
            if (warrior.startRiding(ballista)) warrior.getNavigation().stop();
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

        // A weapon standing in this Warrior's own village is this Warrior's to work, whoever put it
        // there. Ownership is what stops a Warrior wandering off to crew a stranger's emplacement in
        // a field somewhere; it was never meant to stop one using the weapon planted in the square it
        // is currently defending. Checked first because it is the case that actually comes up: a
        // player who has not taken the Chief seat still expects the ballista they built at home to be
        // manned when a raid arrives.
        UUID warriorVillage = warrior.reconcileVillageId(serverLevel, manager);
        if (warriorVillage != null
                && manager.resolveVillage(serverLevel, emplacement.blockPosition())
                        .filter(warriorVillage::equals).isPresent()) {
            return true;
        }

        Set<UUID> chiefed = manager.getVillagesChiefedBy(owner);
        if (chiefed.isEmpty()) return false;

        // Otherwise the owner has to be Chief somewhere this Warrior answers to. Reconciled rather
        // than read raw - a cached id left dangling by a village merge names a village nobody is
        // Chief of, and would refuse a weapon the Warrior is plainly entitled to.
        if (warriorVillage != null && chiefed.contains(warriorVillage)) return true;

        // Failing that, the ground the weapon itself stands on. A Warrior is only ever asked this
        // question mid-fight, which is exactly when it is most likely to have been drawn out past the
        // edge of the village and to resolve to nothing at all. The emplacement is bolted down inside
        // the village it was built to cover and never moves, so its own position is the steadier of
        // the two answers, and the one that matches what a player means by "my ballista".
        return manager.resolveVillage(serverLevel, emplacement.blockPosition())
                .filter(chiefed::contains)
                .isPresent();
    }
}
