package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.ai.FlightPhysics;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ServerLevelAccessor;;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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

public class BirdieEntity extends Animal implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(BirdieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(BirdieEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOPPING =
            SynchedEntityData.defineId(BirdieEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BirdieEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public enum Variant {
        BLUE, BROWN, RED;

        public static Variant byId(int id) {
            Variant[] values = values();
            return values[id % values.length];
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.8);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, 0);
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_HOPPING, false);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag tag) {
        this.setVariant(Variant.byId(this.random.nextInt(3)));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
    }

    public Variant getVariant() {
        return Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(Variant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_FLYING, flying);
    }

    public boolean isHopping() {
        return this.entityData.get(DATA_HOPPING);
    }

    public void setHopping(boolean hopping) {
        this.entityData.set(DATA_HOPPING, hopping);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setVariant(Variant.byId(tag.getInt("Variant")));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new BirdieFlyGoal(this));
        this.goalSelector.addGoal(2, new BirdieHopGoal(this, 1.0));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isNoGravity() {
        return this.isFlying() || super.isNoGravity();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // it comes down under its own power
    }

    public static boolean checkBirdieSpawnRules(EntityType<BirdieEntity> type, ServerLevelAccessor level,
                                                MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getRawBrightness(pos, 0) > 8
                && level.getBiome(pos).is(BiomeTags.IS_OVERWORLD);
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
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit) {
            this.spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.FEATHER, 1 + this.random.nextInt(2)));
            this.spawnAtLocation(new ItemStack(ModItems.BIRD_MEAT.get()));
        }
    }

    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 0, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<BirdieEntity> state) {
        if (this.isFlying()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("fly"));
        } else if (this.isHopping()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("hop"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ==========================================================
    // Custom hop-movement goal: discrete forward+up impulses,
    // each burst lasting ~0.125s (2-3 ticks), matching the "hop" animation length.
    // ==========================================================
    private static class BirdieHopGoal extends Goal {
        private final BirdieEntity birdie;
        private final double speedModifier;
        private double targetX, targetZ;
        private int hopBurstTicks = 0;
        private boolean hasHopped = false;

        BirdieHopGoal(BirdieEntity birdie, double speedModifier) {
            this.birdie = birdie;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (birdie.isFlying() || !birdie.onGround()) return false;
            return birdie.random.nextInt(60) == 0; // occasional, not constant
        }

        @Override
        public boolean canContinueToUse() {
            return !hasHopped;
        }

        @Override
        public void start() {
            Vec3 pos = birdie.position();
            double angle = birdie.random.nextDouble() * Math.PI * 2;
            targetX = pos.x + Math.cos(angle) * 1.5;
            targetZ = pos.z + Math.sin(angle) * 1.5;
            hasHopped = false;
            hopBurstTicks = 0;
        }

        private void faceMovementDirection(Vec3 motion) {
            if (motion.horizontalDistanceSqr() < 1.0E-5) return;
            float targetYaw = (float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI));
            float currentYaw = birdie.getYRot();
            float newYaw = Mth.rotateIfNecessary(currentYaw, targetYaw, 15.0F);
            birdie.setYRot(newYaw);
            birdie.yBodyRot = newYaw;
            birdie.setYHeadRot(newYaw);
        }

        @Override
        public void tick() {
            Vec3 pos = birdie.position();

            if (hopBurstTicks > 0) {
                hopBurstTicks--;
                birdie.setHopping(true);
                Vec3 dir = new Vec3(targetX - pos.x, 0, targetZ - pos.z).normalize();
                birdie.move(MoverType.SELF, new Vec3(dir.x * 0.14 * speedModifier, 0.0, dir.z * 0.14 * speedModifier));
                birdie.getLookControl().setLookAt(targetX, pos.y, targetZ);
                faceMovementDirection(dir);

                if (hopBurstTicks == 0) {
                    hasHopped = true;
                }
            } else if (birdie.onGround() && !hasHopped) {
                birdie.setDeltaMovement(birdie.getDeltaMovement().x, 0.32, birdie.getDeltaMovement().z);
                hopBurstTicks = 3; // single ~0.15s burst, matching your hop animation length
            }
        }

        @Override
        public void stop() {
            birdie.setHopping(false);
        }
    }

    // ==========================================================
    // Custom flight goal: occasionally takes off, flies to a random
    // nearby point above ground, then glides back down and lands.
    // ==========================================================
    private static class BirdieFlyGoal extends Goal {
        private static final double FLIGHT_SPEED = 0.18;
        private static final double DESCENT_SPEED = 0.12;

        private final BirdieEntity birdie;
        private double targetX, targetY, targetZ;
        private int flightTimer = 0;
        private int groundedCooldown = 20;
        private boolean landing = false;

        BirdieFlyGoal(BirdieEntity birdie) {
            this.birdie = birdie;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (birdie.isFlying()) return true;
            if (groundedCooldown-- > 0) return false;
            return birdie.random.nextInt(15) == 0; // takes off frequently
        }

        // Runs until the bird is actually back down. The flight timer only starts the descent, it no
        // longer ends the goal - ending it in mid-air is what used to switch the wings off and drop it.
        @Override
        public boolean canContinueToUse() {
            return birdie.isFlying();
        }

        @Override
        public void start() {
            birdie.setFlying(true);
            landing = false;
            pickNewTarget();
            flightTimer = 400 + birdie.random.nextInt(400); // long flights, 20-40s
        }

        @Override
        public void tick() {
            if (landing) {
                if (FlightPhysics.descend(birdie, DESCENT_SPEED)) {
                    birdie.setFlying(false);
                    groundedCooldown = 20 + birdie.random.nextInt(40);
                }
                return;
            }

            flightTimer--;
            Vec3 pos = birdie.position();
            Vec3 target = new Vec3(targetX, targetY, targetZ);
            Vec3 diff = target.subtract(pos);

            if (diff.lengthSqr() < 1.0) {
                pickNewTarget();
            } else {
                Vec3 motion = diff.normalize().scale(FLIGHT_SPEED);
                Vec3 nextPos = pos.add(motion.scale(4.0)); // look a little ahead, not just one tick's worth

                if (isPathBlocked(pos, nextPos)) {
                    pickNewTarget();
                    return;
                }

                // Set the velocity only - the birdie's own travel() does the move. Doing both here
                // stepped it twice per tick, so it actually flew at double this speed.
                birdie.setDeltaMovement(motion);
                birdie.getLookControl().setLookAt(targetX, targetY, targetZ);
                faceMovementDirection(motion);
            }

            if (flightTimer <= 0) {
                landing = true;
            }
        }

        private boolean isPathBlocked(Vec3 from, Vec3 to) {
            ClipContext clipContext = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, birdie);
            HitResult hit = birdie.level().clip(clipContext);
            return hit.getType() == HitResult.Type.BLOCK;
        }

        private void faceMovementDirection(Vec3 motion) {
            if (motion.horizontalDistanceSqr() < 1.0E-5) return;
            float targetYaw = (float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI));
            float currentYaw = birdie.getYRot();
            float newYaw = Mth.rotateIfNecessary(currentYaw, targetYaw, 15.0F); // turn up to 15°/tick toward target
            birdie.setYRot(newYaw);
            birdie.yBodyRot = newYaw;
            birdie.setYHeadRot(newYaw);
        }

        @Override
        public void stop() {
            // Only ever clear the flag on the ground; leaving it set in mid-air means canUse() picks
            // the goal straight back up and the bird keeps flying instead of dropping.
            if (birdie.onGround() || birdie.isInWater()) {
                birdie.setFlying(false);
                birdie.setDeltaMovement(birdie.getDeltaMovement().x, 0, birdie.getDeltaMovement().z);
            }
            landing = false;
        }

        private void pickNewTarget() {
            Vec3 pos = birdie.position();
            double angle = birdie.random.nextDouble() * Math.PI * 2;
            double dist = 4.0 + birdie.random.nextDouble() * 6.0;
            targetX = pos.x + Math.cos(angle) * dist;
            targetY = pos.y + 1.0 + birdie.random.nextDouble() * 3.0; // lean upward, away from obstacles
            targetZ = pos.z + Math.sin(angle) * dist;
        }
    }
}