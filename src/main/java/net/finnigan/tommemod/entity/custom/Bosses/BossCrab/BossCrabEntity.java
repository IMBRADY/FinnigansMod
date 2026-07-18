package net.finnigan.tommemod.entity.custom.Bosses.BossCrab;

import net.finnigan.tommemod.sound.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BossCrabEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(BossCrabEntity.class, EntityDataSerializers.INT);

    public enum CrabState {
        SPAWN, IDLE, WALK, RUNSET,
        DOUBLEPINCH, BIGPINCH, GROUNDPOUND,
        JUMP_RISE, JUMP_LAND,
        DASH_LEFT, DASH_RIGHT,
        PRESTRIKE
    }

    // ---- Tunables ----
    public static double JUMP_OVERSHOOT_MULTIPLIER = 1.15; // >1.0 = slight overshoot buffer for a moving player

    private int repathCooldown = 0;
    private static final int REPATH_INTERVAL = 10;

    private static final float TURN_SPEED_ATTACK = 12.0F; // degrees/tick while locked into an attack facing
    private static final float TURN_SPEED_WALK = 18.0F;   // degrees/tick while approaching

    private Vec3 jumpHorizontalVelocity = Vec3.ZERO;

    public static double ATTACK_RANGE = 5.0;   // 0-5 = attack
    public static double WALK_MAX_RANGE = 13.0; // 7-13 = walk
    public static double JUMP_MIN_RANGE = 14.0;       // > close range
    public static double MAX_JUMP_DISTANCE = 16.0;

    public static double CLOSE_ATTACK_RANGE = 6.0;   // <= this => pinches / groundpound
    public static double DASH_DODGE_CHANCE = 0.35;   // chance to dodge-dash instead of standing still in prestrike
    public static double JUMP_UP_VELOCITY = 0.85;
    public static double MAX_JUMP_HORIZONTAL_SPEED = 0.8;
    public static double DASH_DISTANCE = 7.0;        // blocks covered during a dash
    private static final double GRAVITY_APPROX = 0.08;
    public static double DASH_FORWARD_BIAS = 0.4; // 0 = sideways, 1 = straight at player

    private static final int SPAWN_DURATION = 18;     // 0.9167s
    private static final int RUNSET_DURATION = 5;      // 0.25s
    private static final int JUMP_RISE_DURATION = 8;   // 0.3811s
    private static final int DASH_DURATION = 12;       // 0.5833s
    private static final int PRESTRIKE_DURATION = 15;  // 0.75s

    private CrabState state = CrabState.SPAWN;
    private int stateTicks = 0;
    private Vec3 dashDirection = Vec3.ZERO;
    private final Set<Integer> processedHitTicks = new HashSet<>();

    private final AnimationController<BossCrabEntity> mainController =
            new AnimationController<>(this, "main", 0, this::predicate);

    public BossCrabEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    private void turnTowards(LivingEntity target, float maxTurnPerTick) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float newYaw = approachAngle(this.getYRot(), targetYaw, maxTurnPerTick);
        this.setYRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
    }

    private float approachAngle(float current, float target, float maxDelta) {
        float delta = Mth.wrapDegrees(target - current);
        delta = Mth.clamp(delta, -maxDelta, maxDelta);
        return current + delta;
    }

    @Override
    protected void registerGoals() {
        // Movement + attacks are fully custom in customServerAiStep(). Only target
        // acquisition uses a vanilla goal.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (LivingEntity target) -> {
                    if (!(target instanceof Player player)) return false;
                    return !player.isSpectator() && !player.isCreative();
                }));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public void checkDespawn() {
        // Bosses shouldn't vanish mid-fight.
    }

    // ---- Synced state ----

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_STATE, CrabState.SPAWN.ordinal());
    }

    private void setState(CrabState newState) {
        this.state = newState;
        this.stateTicks = 0;
        this.processedHitTicks.clear();
        this.entityData.set(DATA_ATTACK_STATE, newState.ordinal());
    }

    private CrabState getSyncedState() {
        return CrabState.values()[this.entityData.get(DATA_ATTACK_STATE)];
    }

    // ---- Brain ----

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        stateTicks++;

        LivingEntity target = this.getTarget();

        switch (state) {
            case SPAWN -> tickSpawn();
            case IDLE -> tickIdle(target);
            case WALK -> tickWalk(target);
            case RUNSET -> tickRunset();
            case DOUBLEPINCH -> tickAttack(CrabAttackType.DOUBLEPINCH, CrabState.PRESTRIKE);
            case BIGPINCH -> tickAttack(CrabAttackType.BIGPINCH, CrabState.PRESTRIKE);
            case GROUNDPOUND -> tickAttack(CrabAttackType.GROUNDPOUND, CrabState.PRESTRIKE);
            case JUMP_RISE -> tickJumpRise();
            case JUMP_LAND -> tickAttack(CrabAttackType.POUND_DOWN, CrabState.PRESTRIKE);
            case DASH_LEFT -> tickDash();
            case DASH_RIGHT -> tickDash();
            case PRESTRIKE -> tickPrestrike(target);
        }
    }

    private void tickSpawn() {
        this.getNavigation().stop();
        if (stateTicks >= SPAWN_DURATION) {
            setState(CrabState.IDLE);
        }
    }

    private void tickIdle(@Nullable LivingEntity target) {
        this.getNavigation().stop();
        if (target == null || !target.isAlive()) {
            return;
        }
        decideNextAction(target);
    }

    private void decideNextAction(LivingEntity target) {
        double dist = this.distanceTo(target);
        turnTowards(target, TURN_SPEED_WALK);

        if (dist <= ATTACK_RANGE) {
            CrabAttackType pick = pickWeighted(
                    new CrabAttackType[] { CrabAttackType.DOUBLEPINCH, CrabAttackType.BIGPINCH, CrabAttackType.GROUNDPOUND },
                    new double[] { 0.45, 0.30, 0.25 }
            );
            beginAttack(pick);
        } else if (dist >= JUMP_MIN_RANGE) {
            beginJump(target);
        } else {
            setState(CrabState.RUNSET); // 7-13 blocks: walk in
        }
    }

    private CrabAttackType pickWeighted(CrabAttackType[] options, double[] weights) {
        double total = 0;
        for (double w : weights) total += w;
        double roll = this.random.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < options.length; i++) {
            cumulative += weights[i];
            if (roll <= cumulative) return options[i];
        }
        return options[options.length - 1];
    }

    private void beginAttack(CrabAttackType attack) {
        this.getNavigation().stop();
        switch (attack) {
            case DOUBLEPINCH -> setState(CrabState.DOUBLEPINCH);
            case BIGPINCH -> setState(CrabState.BIGPINCH);
            case GROUNDPOUND -> setState(CrabState.GROUNDPOUND);
            case POUND_DOWN -> setState(CrabState.JUMP_LAND);
        }
    }

    private void tickAttack(CrabAttackType attack, CrabState nextState) {
        this.getNavigation().stop();
        LivingEntity target = this.getTarget();
        if (target != null) {
            turnTowards(target, TURN_SPEED_ATTACK);
        }

        for (int i = 0; i < attack.hitTicks.length; i++) {
            int hitTick = attack.hitTicks[i];
            if (stateTicks == hitTick && !processedHitTicks.contains(hitTick)) {
                processedHitTicks.add(hitTick);
                resolveHit(attack, attack.hitDamage[i]);
            }
        }

        if (stateTicks >= attack.durationTicks) {
            setState(nextState);
        }
    }

    private void resolveHit(CrabAttackType attack, double damage) {
        double searchRadius = Math.max(attack.coneRange, attack.cylinderRadius) + 2.0;
        List<LivingEntity> nearby = CrabHitboxUtil.getNearbyLivingExcludingSelf(this, searchRadius);

        for (LivingEntity le : nearby) {
            boolean hit = switch (attack.shape) {
                case CONE -> CrabHitboxUtil.isInCone(this, le, attack.coneRange, attack.coneAngleDegrees, attack.cylinderVerticalRange);
                case CYLINDER -> CrabHitboxUtil.isInCylinder(this, le, attack.cylinderRadius, attack.cylinderVerticalRange);
            };
            if (hit) {
                le.hurt(this.damageSources().mobAttack(this), (float) damage);
                Vec3 kb = le.position().subtract(this.position()).normalize().scale(0.4).add(0, 0.15, 0);
                le.push(kb.x, kb.y, kb.z);
            }
        }
    }

    private void tickRunset() {
        this.getNavigation().stop();
        if (stateTicks >= RUNSET_DURATION) {
            repathCooldown = 0; // force an immediate path calc on entering WALK
            setState(CrabState.WALK);
        }
    }

    private void tickWalk(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) {
            this.getNavigation().stop();
            setState(CrabState.IDLE);
            return;
        }

        if (repathCooldown <= 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(target, 1.0);
            repathCooldown = REPATH_INTERVAL;
        } else {
            repathCooldown--;
        }

        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double dist = this.distanceTo(target);
        if (dist <= ATTACK_RANGE) {
            this.getNavigation().stop();
            decideNextAction(target);
        } else if (dist >= JUMP_MIN_RANGE) {
            beginJump(target);
        }
    }

    private void beginJump(LivingEntity target) {
        this.getNavigation().stop();
        setState(CrabState.JUMP_RISE);

        Vec3 toTarget = target.position().subtract(this.position());
        Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
        double rawDist = flat.length();
        double dist = Math.min(rawDist, MAX_JUMP_DISTANCE);
        Vec3 dir = rawDist > 1.0E-4 ? flat.scale(1.0 / rawDist) : this.getLookAngle();

        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;

        double hangTicks = (2.0 * JUMP_UP_VELOCITY) / GRAVITY_APPROX;
        double horizontalSpeed = Math.min((dist / hangTicks) * JUMP_OVERSHOOT_MULTIPLIER, MAX_JUMP_HORIZONTAL_SPEED);

        this.jumpHorizontalVelocity = new Vec3(dir.x * horizontalSpeed, 0, dir.z * horizontalSpeed);
        this.setDeltaMovement(jumpHorizontalVelocity.x, JUMP_UP_VELOCITY, jumpHorizontalVelocity.z);
        this.hasImpulse = true;
    }

    private void tickJumpRise() {
        if (!this.onGround()) {
            this.setDeltaMovement(jumpHorizontalVelocity.x, this.getDeltaMovement().y, jumpHorizontalVelocity.z);
        }
        if (stateTicks > JUMP_RISE_DURATION && this.onGround()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.BOSS_CRAB_LAND.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            setState(CrabState.JUMP_LAND);
        }
    }

    private void beginDash(int direction, LivingEntity target) {
        // direction: -1 = left, +1 = right
        Vec3 look = this.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(direction);

        Vec3 toTarget = target.position().subtract(this.position());
        Vec3 flatToTarget = new Vec3(toTarget.x, 0, toTarget.z);
        Vec3 forward = flatToTarget.lengthSqr() > 1.0E-4 ? flatToTarget.normalize() : look.multiply(1, 0, 1).normalize();

        Vec3 blended = side.scale(1.0 - DASH_FORWARD_BIAS).add(forward.scale(DASH_FORWARD_BIAS));
        this.dashDirection = blended.lengthSqr() > 1.0E-4 ? blended.normalize() : side;

        setState(direction < 0 ? CrabState.DASH_LEFT : CrabState.DASH_RIGHT);
    }

    private void tickDash() {
        double speedPerTick = DASH_DISTANCE / DASH_DURATION;
        this.setDeltaMovement(dashDirection.x * speedPerTick, this.getDeltaMovement().y, dashDirection.z * speedPerTick);
        this.hasImpulse = true;

        if (stateTicks >= DASH_DURATION) {
            setState(CrabState.IDLE);
        }
    }

    private void tickPrestrike(@Nullable LivingEntity target) {
        this.getNavigation().stop();

        if (stateTicks == 1 && target != null && this.random.nextDouble() < DASH_DODGE_CHANCE) {
            beginDash(this.random.nextBoolean() ? 1 : -1, target);
            return;
        }

        if (target != null) {
            turnTowards(target, TURN_SPEED_ATTACK);
        }

        if (stateTicks >= PRESTRIKE_DURATION) {
            setState(CrabState.IDLE);
        }
    }

    private PlayState predicate(AnimationState<BossCrabEntity> animState) {
        CrabState s = getSyncedState();

        RawAnimation anim = switch (s) {
            case SPAWN -> RawAnimation.begin().then("spawn", Animation.LoopType.HOLD_ON_LAST_FRAME);
            case IDLE -> RawAnimation.begin().then("prestrike", Animation.LoopType.LOOP);
            case WALK -> RawAnimation.begin().then("walk", Animation.LoopType.LOOP);
            case RUNSET -> RawAnimation.begin().then("runset", Animation.LoopType.PLAY_ONCE);
            case DOUBLEPINCH -> RawAnimation.begin().then("doublepinch", Animation.LoopType.PLAY_ONCE);
            case BIGPINCH -> RawAnimation.begin().then("bigpinch", Animation.LoopType.PLAY_ONCE);
            case GROUNDPOUND -> RawAnimation.begin().then("groundpound", Animation.LoopType.PLAY_ONCE);
            case JUMP_RISE -> RawAnimation.begin().then("jump up", Animation.LoopType.HOLD_ON_LAST_FRAME);
            case JUMP_LAND -> RawAnimation.begin().then("pound down", Animation.LoopType.HOLD_ON_LAST_FRAME);
            case DASH_LEFT -> RawAnimation.begin().then("dash_left", Animation.LoopType.PLAY_ONCE);
            case DASH_RIGHT -> RawAnimation.begin().then("dash_right", Animation.LoopType.PLAY_ONCE);
            case PRESTRIKE -> RawAnimation.begin().then("prestrike", Animation.LoopType.PLAY_ONCE);
        };

        animState.getController().setAnimationSpeed(getAnimationSpeed(s));
        animState.getController().setAnimation(anim);
        return PlayState.CONTINUE;
    }

    private double getAnimationSpeed(CrabState s) {
        return switch (s) {
            case PRESTRIKE -> 2.0;
            default -> 1.0;
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(mainController);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}