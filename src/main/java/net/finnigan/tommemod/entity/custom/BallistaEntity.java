package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.BallistaHelpers.BallistaBoltEntity;
import net.finnigan.tommemod.entity.custom.BallistaHelpers.DefendOwnerTargetGoal;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A mounted crossbow that a player sets down and leaves: a building far more than a creature. It never
 * moves under its own power, never wanders, and never turns - only its arm swings, tracking whatever it
 * is shooting at. Falls if the ground goes out from under it, and can be taken back up by the player
 * who put it there.
 *
 * Built on Mob rather than a BlockEntity because everything wanted of it is entity behaviour: it has
 * health, mobs must be able to target it, it needs vanilla's targeting goals to pick its own marks, and
 * it has to fall. A block would need every one of those rebuilt by hand.
 *
 * Shot timing is a two-beat cycle driven by the animation, not the other way round: it winds ("load"),
 * and only once the wind is finished does it loose ("fire") and put an arrow in the air. Standing idle
 * it holds the end of the wind, so it always reads as a loaded weapon waiting for something to shoot.
 */
public class BallistaEntity extends PathfinderMob implements GeoEntity {

    public static final float MAX_HEALTH = 100.0F;
    /** Reach, in blocks. Deliberately past what a Warrior can see - it is a siege weapon. */
    public static final double RANGE = 64.0;

    private static final float ARROW_DAMAGE = 8.0F;
    /** Blocks per tick. Crosses the full 64 in about eight ticks, which reads as instant. */
    private static final float ARROW_SPEED = 8.0F;

    /** Height the bolt leaves from, in blocks. Level with the bow on the model, and clear of the
     * ground - fired from lower down, bolts clipped the block the Ballista is standing on. */
    private static final double MUZZLE_HEIGHT = 1.5;
    /** How far in front of the pivot the bolt appears, so it starts outside the frame's own hitbox. */
    private static final double MUZZLE_REACH = 1.2;
    /** Where a rider sits, in blocks above the Ballista's feet. */
    private static final double SEAT_HEIGHT = 2.0;

    /** Within this many degrees of the mark counts as on target, and free to shoot. */
    private static final float AIM_TOLERANCE_DEGREES = 4.0F;

    /** Matches the 0.75s "load" animation in ballista.animation.json. */
    private static final int LOAD_DURATION_TICKS = 15;
    /** Matches the 0.125s "fire" animation, rounded up. The bolt leaves as this begins. */
    private static final int FIRE_DURATION_TICKS = 3;

    /** How fast the arm swings, in degrees per tick - a full about-face in under half a second. */
    private static final float TURN_SPEED_DEGREES = 45.0F;

    private static final RawAnimation LOAD = RawAnimation.begin().thenPlayAndHold("load");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("fire");

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(BallistaEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** Ticks left in the current fire animation; drives the renderer's choice of animation. */
    private static final EntityDataAccessor<Integer> DATA_FIRING_TICKS =
            SynchedEntityData.defineId(BallistaEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Counts down the wind-up. Zero means loaded and free to loose. */
    private int loadTicksRemaining = 0;

    public BallistaEntity(EntityType<? extends BallistaEntity> type, Level level) {
        super(type, level);
        // Vanilla's LookControl flattens xRot to zero on any tick nothing is feeding it a look
        // target, which would drag the arm back level between shots. Nothing here uses look targets -
        // the aim is set by hand in aimAt - so the control is replaced with one that leaves it alone.
        this.lookControl = new LookControl(this) {
            @Override
            protected boolean resetXRotOnTick() {
                return false;
            }
        };
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D) // a bolted-down weapon does not slide
                .add(Attributes.FOLLOW_RANGE, RANGE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER, Optional.empty());
        this.entityData.define(DATA_FIRING_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        // No movement goals at all: the emplacement picks targets and nothing else.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new DefendOwnerTargetGoal(this));
        // LivingEntity + an Enemy test rather than Monster.class, so raid-only hostiles such as
        // Ravagers and Vindicators - which are not Monsters - are shot at like anything else.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 0, true, false,
                candidate -> candidate instanceof Enemy) {
            @Override
            protected AABB getTargetSearchArea(double range) {
                // Vanilla only ever looks four blocks up and down, however far it looks sideways -
                // fine for something that walks, useless for a weapon meant to cover a valley or
                // clear the sky. Reach is the same in every direction here.
                return this.mob.getBoundingBox().inflate(range, range, range);
            }
        });
    }

    // ---- Ownership and pickup ----

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID owner) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (this.level().isClientSide()) return InteractionResult.sidedSuccess(true);

        UUID owner = getOwnerUUID();
        // Anyone else's ballista stays where it is - including for a player in creative, who could
        // otherwise strip a server's defences by walking past them - and nobody else may man it.
        if (owner == null || !owner.equals(player.getUUID())) {
            return InteractionResult.CONSUME;
        }

        // Sneaking takes it back down; a plain right-click climbs into the seat instead.
        if (!player.isShiftKeyDown()) {
            player.startRiding(this);
            return InteractionResult.CONSUME;
        }

        ItemStack recovered = new ItemStack(ModItems.BALLISTA.get());
        if (!player.getInventory().add(recovered)) {
            player.drop(recovered, false);
        }
        this.playSound(SoundEvents.WOOD_BREAK, 1.0F, 1.0F);
        this.discard();
        return InteractionResult.CONSUME;
    }

    // ---- Immobility ----

    /**
     * Pinned in place, but not in the air: the entity is left subject to gravity so that mining out
     * what it stands on drops it, which is the one kind of movement it is meant to have.
     */
    @Override
    public void travel(Vec3 input) {
        super.travel(Vec3.ZERO);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void doPush(Entity other) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        // The frame doesn't swivel, only the arm on top of it. Vanilla's control drags yBodyRot after
        // yHeadRot, which would have the whole emplacement slowly turning to follow its target.
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
            }
        };
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    // ---- Aiming and shooting ----

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        int firingTicks = this.entityData.get(DATA_FIRING_TICKS);
        if (firingTicks > 0) this.entityData.set(DATA_FIRING_TICKS, firingTicks - 1);

        // A rider takes over completely - the weapon aims where they look and shoots when they say.
        if (getControllingPassenger() instanceof Player rider) {
            this.setTarget(null);
            this.yHeadRot = rider.getYHeadRot();
            this.setXRot(rider.getXRot());
            if (loadTicksRemaining > 0) loadTicksRemaining--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.distanceToSqr(target) > RANGE * RANGE) {
            // Idle: wind back up and sit there loaded, ready for the next thing to walk into range.
            loadTicksRemaining = 0;
            return;
        }

        boolean onTarget = aimAt(target);

        if (loadTicksRemaining > 0) {
            loadTicksRemaining--;
            return;
        }
        if (firingTicks > 0) return; // still playing out the last shot
        // Never loose mid-swing. The bolt flies where the arm points, so shooting before the arm has
        // caught up sends it somewhere the Ballista is visibly not aiming.
        if (!onTarget) return;

        fire();
    }

    /** Puts a bolt down the barrel's current line and starts the reload. */
    private void fire() {
        loose();
        this.entityData.set(DATA_FIRING_TICKS, FIRE_DURATION_TICKS);
        loadTicksRemaining = LOAD_DURATION_TICKS;
    }

    /** Whether the weapon is wound and free to shoot right now. */
    public boolean isLoaded() {
        return loadTicksRemaining <= 0 && this.entityData.get(DATA_FIRING_TICKS) <= 0;
    }

    /** Fires on a rider's order. Returns whether a bolt actually left the barrel. */
    public boolean fireForRider(Player rider) {
        if (!rider.equals(getControllingPassenger())) return false;
        if (!isLoaded()) return false;

        this.yHeadRot = rider.getYHeadRot();
        this.setXRot(rider.getXRot());
        fire();
        return true;
    }

    /**
     * Swings the arm toward the mark, and reports whether it is now lined up on it.
     *
     * Written into yHeadRot/xRot rather than a private aim field so that vanilla's own head-rotation
     * packet carries it to clients and GeckoLib interpolates it for free; the body is pinned
     * separately, so the head rotation is purely the arm.
     */
    private boolean aimAt(LivingEntity target) {
        Vec3 muzzle = muzzlePosition();
        double dx = target.getX() - muzzle.x;
        double dz = target.getZ() - muzzle.z;
        double dy = target.getBoundingBox().getCenter().y - muzzle.y;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float wantYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float wantPitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0 / Math.PI)));

        float yawError = Mth.wrapDegrees(wantYaw - this.yHeadRot);
        float pitchError = Mth.wrapDegrees(wantPitch - this.getXRot());

        this.yHeadRot = approachAngle(this.yHeadRot, wantYaw);
        this.setXRot(approachAngle(this.getXRot(), wantPitch));

        return Math.abs(yawError) <= AIM_TOLERANCE_DEGREES && Math.abs(pitchError) <= AIM_TOLERANCE_DEGREES;
    }

    private static float approachAngle(float from, float to) {
        float delta = Mth.wrapDegrees(to - from);
        return from + Mth.clamp(delta, -TURN_SPEED_DEGREES, TURN_SPEED_DEGREES);
    }

    /** The direction the barrel is currently pointing. */
    private Vec3 aimDirection() {
        return Vec3.directionFromRotation(this.getXRot(), this.yHeadRot);
    }

    /** The tip of the barrel: up at bow height and out in front, clear of the frame and the ground. */
    private Vec3 muzzlePosition() {
        return this.position().add(0.0, MUZZLE_HEIGHT, 0.0).add(aimDirection().scale(MUZZLE_REACH));
    }

    /**
     * Looses a bolt straight down the barrel's current line - never at a target's position directly,
     * so what is fired always agrees with what is drawn.
     *
     * The bolt does its own work from here: vanilla ray-traces a projectile along its whole movement
     * vector, so even at eight blocks a tick it strikes what it passes through rather than tunnelling,
     * and shields, armour and cover behave as they would against any other arrow. Gravity is off so it
     * flies exactly where the barrel points.
     */
    private void loose() {
        Vec3 muzzle = muzzlePosition();
        Vec3 direction = aimDirection();

        BallistaBoltEntity bolt = new BallistaBoltEntity(this.level(), this);
        bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
        bolt.setNoGravity(true);
        bolt.setDamage(ARROW_DAMAGE);
        bolt.setCritArrow(false);
        bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
        bolt.shoot(direction.x, direction.y, direction.z, ARROW_SPEED, 0.0F);
        this.level().addFreshEntity(bolt);

        this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        // Never shoots the player who set it down, whatever else it is aimed at.
        UUID owner = getOwnerUUID();
        return (owner != null && owner.equals(other.getUUID())) || super.isAlliedTo(other);
    }

    /**
     * Whether this Ballista refuses to harm the given entity - its own frame, and the people of any
     * village its owner is Chief of.
     *
     * Scoped to the owner's own villages on purpose: a Ballista is a personal emplacement, not a
     * neutral one, and a Chief's weapon covering a rival village's population is not what was asked
     * for. Enforced on the bolt rather than by cancelling the damage afterwards, so a protected
     * villager wandering across the line of fire is passed straight through instead of stopping it.
     */
    public boolean isProtectedFrom(Entity candidate) {
        if (candidate == this) return true;
        if (!(candidate instanceof Villager || candidate instanceof ElderVillagerEntity
                || candidate instanceof WarriorVillagerEntity)) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) return false;

        UUID owner = getOwnerUUID();
        if (owner == null) return false;

        VillageManager manager = VillageManager.get(serverLevel);
        UUID villageId = candidate instanceof WarriorVillagerEntity warrior && warrior.getVillageId() != null
                ? warrior.getVillageId()
                : manager.resolveVillage(serverLevel, candidate.blockPosition()).orElse(null);
        if (villageId == null) return false;

        return owner.equals(manager.getChief(villageId).orElse(null));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isProtectedFrom(target) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Its own bolt can never come back on it, however it got turned around.
        if (source.getDirectEntity() instanceof BallistaBoltEntity bolt && bolt.getOwner() == this) {
            return false;
        }
        return super.hurt(source, amount);
    }

    // ---- Riding ----

    @Override
    public double getPassengersRidingOffset() {
        return SEAT_HEIGHT;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player rider ? rider : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    // ---- Persistence ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) setOwnerUUID(tag.getUUID("Owner"));
    }

    // ---- Presentation ----

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WOOD_HIT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ballista", 0, this::animate));
    }

    private PlayState animate(AnimationState<BallistaEntity> state) {
        // thenPlayAndHold parks "load" on its final frame, which is what leaves the weapon sitting
        // visibly loaded between shots rather than snapping back to an unwound pose.
        state.setAndContinue(this.entityData.get(DATA_FIRING_TICKS) > 0 ? FIRE : LOAD);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }
}
