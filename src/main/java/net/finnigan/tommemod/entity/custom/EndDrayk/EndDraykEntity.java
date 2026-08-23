package net.finnigan.tommemod.entity.custom.EndDrayk;

import net.finnigan.tommemod.entity.ai.FlightPhysics;
import net.finnigan.tommemod.entity.ai.FlutterGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A segmented flying snake enemy for the End - a high-health hostile mob, not a boss. Ten pieces trail
 * behind a leading head, each lagging the one in front so the body slithers.
 *
 * <p>This is <em>one</em> registered entity that owns ten {@link EndDraykPart} collision boxes - not ten
 * registered entities. Every segment's position is fully derived from the head's path, so there is no
 * independent state to sync, nothing to save, and no orphan segments to clean up on chunk unload or
 * death. The parts are pure hitboxes; they are never network-spawned.
 */
public class EndDraykEntity extends Monster implements GeoEntity {
    private static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("fly");

    /** Body hits count for less than head hits, making the head the weak point. */
    private static final float BODY_DAMAGE_MULTIPLIER = 0.5F;

    /** Cruise speed while it has nothing to chase, as a fraction of its hunting speed. */
    private static final double IDLE_SPEED_MODIFIER = 0.6D;
    private static final double HUNT_SPEED = 0.6D;

    /**
     * Steering limit, in degrees per tick, for both the path and the facing. 8 is a full turn in about
     * two and a quarter seconds - loose enough that the drayk still reads as agile, tight enough that it
     * can't double back through its own body.
     */
    private static final float MAX_TURN_DEGREES_PER_TICK = 8.0F;

    /** Horizontal speed below which the heading is drift rather than intent. */
    private static final double MIN_STEERING_SPEED = 1.0E-3D;

    /** Blocks of terrain clearance the drayk tries to hold beneath itself. */
    private static final int GROUND_CLEARANCE_BLOCKS = 2;

    /** Per-tick upward nudge applied while it is inside that clearance. */
    private static final double GROUND_ESCAPE_LIFT = 0.05D;

    private final EndDraykPart[] parts;
    private final DraykTrail trail = new DraykTrail();
    private final DraykPose[] poses;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** False until the first positioning pass, so segment 0 doesn't interpolate in from the world origin. */
    private boolean segmentsPositioned = false;

    /** Last horizontal heading in degrees; NaN until it has moved fast enough to have one. */
    private float lastHeading = Float.NaN;

    public EndDraykEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 12;

        DraykSegment[] segs = DraykSegment.values();
        this.parts = new EndDraykPart[segs.length];
        this.poses = new DraykPose[segs.length];
        for (int i = 0; i < segs.length; i++) {
            this.parts[i] = new EndDraykPart(this, segs[i]);
            this.poses[i] = new DraykPose();
        }
        // Forge MC-158205: reserve a contiguous id block so part ids follow the parent's.
        this.setId(ENTITY_COUNTER.getAndAdd(segs.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.03D)
                .add(Attributes.FLYING_SPEED, HUNT_SPEED)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                // Stands in for the solid body the segments used to have. Mob#doHurtTarget turns this
                // into knockback of half its value on top of the 0.4 every melee hit already carries,
                // so a hit shoves roughly three times as far as an ordinary mob's.
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D)
                // Immune to incoming knockback. The body is a trail of where the head has been, so a
                // knockback impulse doesn't shove the drayk aside - it teleports the head sideways and
                // the whole body whips through itself to follow. Chain-hitting one made it coil into a
                // knot. Nothing about a 40 HP flyer needs it to be launchable.
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    /**
     * Light level is deliberately not a condition. The End has no sky light and the drayk flies well
     * above the terrain the heightmap places it on, so a darkness check would gate it on whatever block
     * happens to sit underneath. Rarity is handled by the spawner weight in the biome modifier instead.
     */
    public static boolean checkEndDraykSpawnRules(EntityType<EndDraykEntity> type, ServerLevelAccessor level,
                                                  MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    /**
     * Placeholder flight harness for build step 1 - enough movement to verify that the trail spaces the
     * segments evenly while accelerating, decelerating and turning hard. The real move set is a separate
     * pass; CrabAttackType is the closest existing reference for structuring a move set.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        // FlutterGoal, not WaterAvoidingRandomFlyingGoal: the latter idles between picks, and a drayk that
        // hangs motionless never builds a trail to look at. Shares Flag.MOVE with the melee goal, so it
        // only drives the drayk while it has no target.
        this.goalSelector.addGoal(2, new FlutterGoal(this, IDLE_SPEED_MODIFIER, 16.0D, 8.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public void travel(Vec3 travelVector) {
        FlightPhysics.travel(this, travelVector);
        this.limitPathCurvature();
        this.calculateEntityAnimation(true);
    }

    /**
     * Caps how fast the drayk's <em>path</em> can bend.
     *
     * <p>The body is a record of where the head has been, so the shape of the path is the shape of the
     * drayk. A move control that is free to reverse direction in a couple of ticks will happily fly the
     * head back down the line the body is still occupying, and the thing turns inside out. Capping the
     * heading change per tick puts a floor under the turning circle: at this cap and a typical cruise
     * speed the circle comes out a good deal wider than the drayk is long, which is what it takes for
     * the body never to reach itself.
     *
     * <p>Deliberately a cap on the velocity rather than a lower move-control speed - it constrains the
     * shape of the path without making the drayk slower.
     */
    private void limitPathCurvature() {
        Vec3 velocity = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        // Below this it is drifting, not heading anywhere, and the derived angle is mostly noise.
        if (horizontalSpeed < MIN_STEERING_SPEED) return;

        float heading = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI));

        if (!Float.isNaN(this.lastHeading)) {
            float delta = Mth.wrapDegrees(heading - this.lastHeading);
            float capped = Mth.clamp(delta, -MAX_TURN_DEGREES_PER_TICK, MAX_TURN_DEGREES_PER_TICK);

            if (capped != delta) {
                heading = this.lastHeading + capped;
                double radians = heading * (Math.PI / 180.0D);
                this.setDeltaMovement(Math.cos(radians) * horizontalSpeed,
                        velocity.y,
                        Math.sin(radians) * horizontalSpeed);
            }
        }
        this.lastHeading = heading;
    }

    /** Same cap applied to the rendered facing, so the head doesn't snap round ahead of the path. */
    private void limitYawRate() {
        float delta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        float yaw = Mth.wrapDegrees(this.yRotO
                + Mth.clamp(delta, -MAX_TURN_DEGREES_PER_TICK, MAX_TURN_DEGREES_PER_TICK));

        this.setYRot(yaw);
        // The model un-rotates segment offsets by yBodyRot, so it has to track the capped yaw too or
        // the whole body counter-rotates against a head that turned further than it did.
        this.yBodyRot = yaw;
    }

    /**
     * Keeps the drayk off the floor.
     *
     * <p>It has no gravity, so nothing breaks contact once it lands; the move control only accelerates
     * toward a waypoint, and FlutterGoal is perfectly happy to pick one at ground level. Landed, it has
     * no way back up and simply sits there. A small standing lift whenever there is terrain just below
     * keeps it hovering instead.
     */
    private void keepAirborne() {
        if (this.onGround()) this.setOnGround(false);

        for (int depth = 1; depth <= GROUND_CLEARANCE_BLOCKS; depth++) {
            BlockPos probe = BlockPos.containing(this.getX(), this.getY() - depth, this.getZ());
            if (this.level().getBlockState(probe).blocksMotion()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, GROUND_ESCAPE_LIFT, 0.0D));
                return;
            }
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // permanently airborne, never falls
    }

    // --- Multipart ---

    @Override public boolean isMultipartEntity() { return true; }
    @Override public PartEntity<?>[] getParts()  { return this.parts; }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].setId(id + i + 1);
        }
    }

    /** Only the parts are hittable - the parent's own box is a stub. */
    @Override public boolean isPickable() { return false; }

    @Override
    public void tick() {
        super.tick();

        // Server-authoritative: position and yaw both arrive on the client through the tracker, and the
        // client rebuilds the trail from those. Steering on both sides would fight the interpolation.
        if (!this.level().isClientSide()) {
            this.keepAirborne();
            this.limitYawRate();
        }

        this.trail.push(this.position(), this.getYRot(), this.getXRot());
        this.updateSegments();
    }

    /** Runs on both sides: the trail is derived from the head, so client and server agree. */
    private void updateSegments() {
        if (this.trail.isEmpty()) return;

        DraykSegment[] segs = DraykSegment.values();
        for (int i = 0; i < segs.length; i++) {
            if (i == 0) this.poses[0].set(this.position(), this.getYRot(), this.getXRot());
            else        this.trail.sampleBehind(segs[i].trailOffset(), this.poses[i]);

            EndDraykPart part = this.parts[i];
            Vec3 p = this.poses[i].pos;

            // setPos puts the box's BOTTOM at y, but the model pivots each segment on its cube centre and
            // lands that centre on the trail point. Drop the box half a height so the two agree; without
            // this every hitbox floats a half-segment above the geometry it belongs to.
            double y = p.y - segs[i].height() * 0.5D;

            // Parts are never ticked, so nothing else advances their previous-position fields. Snapshot
            // them by hand the way EnderDragon does: partial-tick interpolation reads xOld/yOld/zOld, and
            // leaving them stale makes both the F3+B boxes and the rendered body streak from the origin.
            double ox = this.segmentsPositioned ? part.getX() : p.x;
            double oy = this.segmentsPositioned ? part.getY() : y;
            double oz = this.segmentsPositioned ? part.getZ() : p.z;

            part.setPos(p.x, y, p.z);

            part.xo = ox;
            part.yo = oy;
            part.zo = oz;
            part.xOld = ox;
            part.yOld = oy;
            part.zOld = oz;

            if (!this.segmentsPositioned) this.poses[i].snapPrev();
        }
        this.segmentsPositioned = true;
    }

    public DraykPose poseOf(DraykSegment seg) {
        return this.poses[seg.ordinal()];
    }

    /** Parts route damage here so the head can be the weak point. */
    public boolean hurtPart(EndDraykPart part, DamageSource source, float amount) {
        float scaled = part.segment().kind() == DraykSegment.Kind.HEAD
                ? amount
                : amount * BODY_DAMAGE_MULTIPLIER;
        return super.hurt(source, scaled);
    }

    /** A ten-block snake must not be culled because the head's stub box left the frustum. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(16.0D);
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::predicate));
    }

    private PlayState predicate(AnimationState<EndDraykEntity> state) {
        state.getController().setAnimation(FLY_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
