package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.BallistaHelpers.BallistaBoltEntity;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * moves under its own power, never wanders, and never turns - only its arm swings, and only while
 * somebody is working it. Falls if the ground goes out from under it, and can be taken back up by the
 * player who put it there.
 *
 * It is a crewed weapon, not an automatic one: unmanned it picks no targets and fires nothing, and the
 * arm holds exactly where the last operator left it, wound and waiting. Two things can crew it - the
 * owner, who aims it with their own view and fires with the attack key, and a Warrior Villager, which
 * climbs aboard during a fight and drives it through {@link #operateAt} (see ManBallistaGoal).
 *
 * Built on Mob rather than a BlockEntity because everything wanted of it is entity behaviour: it has
 * health, mobs must be able to target it, something has to be able to ride it, and it has to fall. A
 * block would need every one of those rebuilt by hand.
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
        // Nothing here uses look targets - the aim is written straight into yHeadRot/xRot by aimAt -
        // so the whole control is silenced. Left in vanilla's hands it would undo that aim every
        // single tick, twice over: with no look target set it flattens xRot to zero, and it drags
        // yHeadRot ten degrees back toward yBodyRot. Mob.serverAiStep runs it AFTER
        // customServerAiStep, so it always got the last word.
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {
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
        // None at all. It doesn't move, and it doesn't hunt: a Ballista is only ever as dangerous as
        // whoever is sitting in it. What it shoots at is entirely the operator's choice - a player's
        // crosshair, or the target a Warrior brought aboard with it.
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
            // A Warrior working it stands down for the owner rather than locking them out of their
            // own weapon. Its goal sees the seat taken on its next tick and goes back to fighting.
            if (getControllingPassenger() instanceof WarriorVillagerEntity warrior) {
                warrior.stopRiding();
            }
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
    public int getMaxHeadYRot() {
        // Vanilla's 75 is a neck. This is a turntable, and it has to be able to shoot behind itself:
        // the frame never turns, so every bearing the weapon can cover has to be head rotation.
        return 180;
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
        if (loadTicksRemaining > 0) loadTicksRemaining--;

        // A player rider takes the weapon over completely: it points wherever they are looking, and
        // fires when they say so (BallistaFirePacket). A Warrior crew aims through operateAt instead,
        // driven from its own goal, so there is nothing to do for it here.
        //
        // Unmanned there is deliberately no branch at all. The arm is simply left alone, holding the
        // bearing the last operator left it on, wound and ready for whoever climbs in next.
        if (getControllingPassenger() instanceof Player rider) {
            this.yHeadRot = rider.getYHeadRot();
            this.setXRot(rider.getXRot());
        }
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

    /** Fires on an operator's order. Returns whether a bolt actually left the barrel. */
    public boolean fireForRider(LivingEntity operator) {
        if (!operator.equals(getControllingPassenger())) return false;
        if (!isLoaded()) return false;

        // A player's aim is their view, read fresh at the moment they pull rather than trusting the
        // last tick's copy. A Warrior has already laid the weapon itself, through operateAt.
        if (operator instanceof Player player) {
            this.yHeadRot = player.getYHeadRot();
            this.setXRot(player.getXRot());
        }
        fire();
        return true;
    }

    /**
     * Lays the weapon on a mark for a non-player crew, and reports whether it is lined up well enough
     * to shoot. Called every tick by ManBallistaGoal while a Warrior is aboard.
     *
     * Refuses anyone who is not actually the one sitting in it, so a Warrior whose seat was taken by
     * the owner mid-swing cannot keep steering the thing from the ground.
     */
    public boolean operateAt(LivingEntity operator, LivingEntity target) {
        if (!operator.equals(getControllingPassenger())) return false;
        return aimAt(target);
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

        this.yHeadRot = approachAngle(this.yHeadRot, wantYaw);
        this.setXRot(approachAngle(this.getXRot(), wantPitch));

        // Measured after the swing, not before it. Judged on the old bearing, a weapon that turns at
        // 45 degrees a tick against a 4 degree tolerance reports "off target" on the very tick it
        // arrives, and the shot is held for no reason.
        float yawError = Mth.wrapDegrees(wantYaw - this.yHeadRot);
        float pitchError = Mth.wrapDegrees(wantPitch - this.getXRot());

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
        return getFirstPassenger() instanceof LivingEntity occupant && canOperate(occupant) ? occupant : null;
    }

    /** Who is capable of crewing one: its owner, and the Warriors of the village it defends. */
    public static boolean canOperate(Entity candidate) {
        return candidate instanceof Player || candidate instanceof WarriorVillagerEntity;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && canOperate(passenger);
    }

    /** Whether somebody is working it right now - the one thing that decides if the arm may move. */
    public boolean isManned() {
        return getControllingPassenger() != null;
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
