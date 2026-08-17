package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Tall wading bird of rivers and lakeshores. Unlike the falcon it is a walker first: it strolls the
 * shoreline, stops to drink whenever it happens to be standing beside water, and only occasionally
 * takes a short flight.
 */
public class HeronEntity extends Animal implements GeoEntity {

    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(HeronEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DRINKING =
            SynchedEntityData.defineId(HeronEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HeronEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FLYING_SPEED, 0.7D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_DRINKING, false);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_FLYING, flying);
    }

    public boolean isDrinking() {
        return this.entityData.get(DATA_DRINKING);
    }

    public void setDrinking(boolean drinking) {
        this.entityData.set(DATA_DRINKING, drinking);
    }

    @Override
    public boolean isNoGravity() {
        return this.isFlying() || super.isNoGravity();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // lands under its own power
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HeronDrinkGoal(this));
        this.goalSelector.addGoal(2, new HeronFlyGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /**
     * "Near rivers and lakes" - the biome modifier offers rivers, swamps and the ordinary lake-bearing
     * land biomes, and this narrows it to spots actually within a few blocks of water, which is what
     * makes it a lakeshore mob rather than a plains mob.
     */
    public static boolean checkHeronSpawnRules(EntityType<HeronEntity> type, ServerLevelAccessor level,
                                               MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getRawBrightness(pos, 0) > 8
                && !level.getBiome(pos).is(BiomeTags.IS_OCEAN)
                && level.getBiome(pos).is(BiomeTags.IS_OVERWORLD)
                && hasWaterWithin(level, pos, 5);
    }

    /**
     * During world generation the level is a WorldGenRegion that only permits access to the chunk being
     * generated, so probing a block outside it throws and takes the chunk down with it. Skipping columns
     * whose chunk is unavailable narrows the search near a chunk border instead of crashing.
     */
    private static boolean hasWaterWithin(ServerLevelAccessor level, BlockPos pos, int radius) {
        int minY = Math.max(pos.getY() - 2, level.getMinBuildHeight());
        int maxY = Math.min(pos.getY() + 1, level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();

        for (int x = pos.getX() - radius; x <= pos.getX() + radius; x++) {
            for (int z = pos.getZ() - radius; z <= pos.getZ() + radius; z++) {
                candidate.set(x, pos.getY(), z);
                if (!level.hasChunkAt(candidate)) continue;

                for (int y = minY; y <= maxY; y++) {
                    candidate.set(x, y, z);
                    if (level.getFluidState(candidate).is(FluidTags.WATER)) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Flying", this.isFlying());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setFlying(tag.getBoolean("Flying"));
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 3, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<HeronEntity> state) {
        if (this.isFlying()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("fly"));
        } else if (this.isDrinking()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("drink"));
        } else if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ==========================================================
    // Stops beside water, turns to face it, and holds the drink pose for one full "drink" cycle.
    // ==========================================================
    private static class HeronDrinkGoal extends Goal {

        /** Length of the "drink" animation (1.5s), in ticks. */
        private static final int DRINK_TICKS = 30;
        private static final int COOLDOWN_TICKS = 200;

        private final HeronEntity heron;
        private BlockPos water;
        private int drinkTicks;
        private int cooldown;

        HeronDrinkGoal(HeronEntity heron) {
            this.heron = heron;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            if (heron.isFlying() || !heron.onGround()) return false;
            if (heron.getRandom().nextInt(40) != 0) return false;
            water = findAdjacentWater();
            return water != null;
        }

        @Override
        public boolean canContinueToUse() {
            return drinkTicks > 0;
        }

        @Override
        public void start() {
            drinkTicks = DRINK_TICKS;
            heron.getNavigation().stop();
            heron.setDrinking(true);
            faceWater();
        }

        @Override
        public void stop() {
            heron.setDrinking(false);
            water = null;
            cooldown = COOLDOWN_TICKS;
        }

        @Override
        public void tick() {
            drinkTicks--;
            faceWater();
        }

        private void faceWater() {
            if (water == null) return;
            heron.getLookControl().setLookAt(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5);
            double dx = (water.getX() + 0.5) - heron.getX();
            double dz = (water.getZ() + 0.5) - heron.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            heron.setYRot(yaw);
            heron.yBodyRot = yaw;
        }

        /** Water it could actually reach with its beak: directly beside it, at foot level or just below. */
        @Nullable
        private BlockPos findAdjacentWater() {
            BlockPos feet = heron.blockPosition();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (BlockPos candidate : new BlockPos[]{feet.relative(direction), feet.below().relative(direction)}) {
                    if (heron.level().getFluidState(candidate).is(FluidTags.WATER)) return candidate;
                }
            }
            return null;
        }
    }

    // ==========================================================
    // Occasional short hop-flights between stretches of shoreline. Same raw-velocity flight with a
    // forward clip check that BirdieEntity uses, so it steers around terrain rather than into it.
    // ==========================================================
    private static class HeronFlyGoal extends Goal {

        private static final double FLIGHT_SPEED = 0.16;

        private final HeronEntity heron;
        private double targetX, targetY, targetZ;
        private int flightTimer;
        private int groundedCooldown = 100;

        HeronFlyGoal(HeronEntity heron) {
            this.heron = heron;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (heron.isDrinking()) return false;
            if (heron.isFlying()) return true;
            if (groundedCooldown-- > 0) return false;
            return heron.getRandom().nextInt(200) == 0; // mostly walks
        }

        @Override
        public boolean canContinueToUse() {
            return heron.isFlying() && flightTimer > 0;
        }

        @Override
        public void start() {
            heron.setFlying(true);
            heron.getNavigation().stop();
            pickNewTarget();
            flightTimer = 100 + heron.getRandom().nextInt(120); // 5-11s
        }

        @Override
        public void stop() {
            heron.setFlying(false);
            heron.setDeltaMovement(heron.getDeltaMovement().x, 0, heron.getDeltaMovement().z);
            groundedCooldown = 200 + heron.getRandom().nextInt(200);
        }

        @Override
        public void tick() {
            flightTimer--;

            Vec3 pos = heron.position();
            Vec3 diff = new Vec3(targetX, targetY, targetZ).subtract(pos);

            if (diff.lengthSqr() < 1.0) {
                pickNewTarget();
            } else {
                Vec3 motion = diff.normalize().scale(FLIGHT_SPEED);
                if (isPathBlocked(pos, pos.add(motion.scale(4.0)))) {
                    pickNewTarget();
                    return;
                }
                heron.setDeltaMovement(motion);
                heron.move(MoverType.SELF, heron.getDeltaMovement());
                heron.getLookControl().setLookAt(targetX, targetY, targetZ);
                faceMovementDirection(motion);
            }

            if (flightTimer <= 0) {
                heron.setFlying(false);
            }
        }

        private void pickNewTarget() {
            Vec3 pos = heron.position();
            double angle = heron.getRandom().nextDouble() * Math.PI * 2;
            double dist = 5.0 + heron.getRandom().nextDouble() * 8.0;
            targetX = pos.x + Math.cos(angle) * dist;
            targetY = pos.y + 1.0 + heron.getRandom().nextDouble() * 3.0;
            targetZ = pos.z + Math.sin(angle) * dist;
        }

        private boolean isPathBlocked(Vec3 from, Vec3 to) {
            ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, heron);
            return heron.level().clip(context).getType() == HitResult.Type.BLOCK;
        }

        private void faceMovementDirection(Vec3 motion) {
            if (motion.horizontalDistanceSqr() < 1.0E-5) return;
            float targetYaw = (float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI));
            float newYaw = Mth.rotateIfNecessary(heron.getYRot(), targetYaw, 15.0F);
            heron.setYRot(newYaw);
            heron.yBodyRot = newYaw;
            heron.setYHeadRot(newYaw);
        }
    }
}
