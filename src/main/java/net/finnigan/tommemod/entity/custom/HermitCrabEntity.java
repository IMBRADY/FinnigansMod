package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Optional;

public class HermitCrabEntity extends TamableAnimal implements GeoEntity {

    private static final EntityDataAccessor<Boolean> DATA_RIDING_SHOULDER =
            SynchedEntityData.defineId(HermitCrabEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_FOUND_CHEST_POS =
            SynchedEntityData.defineId(HermitCrabEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HermitCrabEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F); // hops up 1-block ledges without needing to jump
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_RIDING_SHOULDER, false);
        this.entityData.define(DATA_FOUND_CHEST_POS, Optional.empty());
    }

    public boolean isRidingShoulder() {
        return this.entityData.get(DATA_RIDING_SHOULDER);
    }

    public void setRidingShoulder(boolean riding) {
        this.entityData.set(DATA_RIDING_SHOULDER, riding);
    }

    @Nullable
    BlockPos getFoundChestPos() {
        return this.entityData.get(DATA_FOUND_CHEST_POS).orElse(null);
    }

    void setFoundChestPos(@Nullable BlockPos pos) {
        this.entityData.set(DATA_FOUND_CHEST_POS, Optional.ofNullable(pos));
    }

    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("crawl");
    private static final RawAnimation FIND_ANIM = RawAnimation.begin().thenLoop("find");
    private static final RawAnimation SIT_ANIM = RawAnimation.begin().thenPlayAndHold("sit");

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new TreasureSeekGoal(this));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    // Lateral shoulder offset in blocks; larger values sit the crab further to the owner's right, out of the crosshair.
    private static final double SHOULDER_SIDE_OFFSET = 0.45;

    @Override
    public void tick() {
        boolean wasFirstTick = this.firstTick;
        super.tick();
        if (wasFirstTick && !this.level().isClientSide && this.isNoGravity() && !this.isRidingShoulder()) {
            // after relaunch, crab does not glitch in air
            this.landAfterReload();
        }
        if (this.isRidingShoulder()) {
            this.tickShoulderRide();
        }
    }

    private void landAfterReload() {
        BlockPos landPos = this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.blockPosition());
        this.dismountShoulder(landPos);
        this.setOrderedToSit(true);
        this.setInSittingPose(true);
    }

    private void tickShoulderRide() {
        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            this.dismountShoulder(this.blockPosition());
            return;
        }

        float yawRad = (float) Math.toRadians(owner.getYRot());
        double sideX = -Math.cos(yawRad) * SHOULDER_SIDE_OFFSET;
        double sideZ = -Math.sin(yawRad) * SHOULDER_SIDE_OFFSET;
        Vec3 target = owner.position().add(sideX, owner.getBbHeight() - 0.55, sideZ);

        this.setPos(target.x, target.y, target.z);
        this.setYRot(owner.getYRot());
        this.yBodyRot = owner.getYRot();
        this.setYHeadRot(owner.getYRot());
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0;
    }

    private void mountShoulder(Player player) {
        this.setRidingShoulder(true);
        this.setOrderedToSit(false);
        this.setFoundChestPos(null);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
    }

    public void dismountShoulder(BlockPos landPos) {
        this.setRidingShoulder(false);
        this.setNoGravity(false);
        this.noPhysics = false;
        this.setPos(landPos.getX() + 0.5, landPos.getY(), landPos.getZ() + 0.5);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame() && stack.is(Items.SALMON)) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!this.level().isClientSide) {
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.setOrderedToSit(false);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isTame() && this.isOwnedBy(player)) {
            if (hand == InteractionHand.MAIN_HAND && !this.level().isClientSide) {
                if (player.isShiftKeyDown()) {
                    if (this.isRidingShoulder()) {
                        this.dismountShoulder(player.blockPosition());
                    } else {
                        this.mountShoulder(player);
                    }
                } else if (!this.isRidingShoulder()) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    public static boolean checkHermitCrabSpawnRules(EntityType<HermitCrabEntity> type, ServerLevelAccessor level,
                                                     MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRidingShoulder() && super.isPickable();
    }

    @Override
    protected void pushEntities() {
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
            this.spawnAtLocation(new ItemStack(ModItems.CRAB_LEGS.get(), 1 + this.random.nextInt(2)));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 0, this::statePredicate));
    }

    private PlayState statePredicate(AnimationState<HermitCrabEntity> state) {
        boolean moving = !this.isRidingShoulder() && Math.abs(this.walkDist - this.walkDistO) > 0.0005F;

        // Priority 1: Find treasure
        if (this.getFoundChestPos() != null) {
            state.getController().setAnimation(FIND_ANIM);
            return PlayState.CONTINUE;
        }

        // Priority 2: Sit when ordered
        if (this.isInSittingPose() || this.isRidingShoulder()) {
            state.getController().setAnimation(SIT_ANIM);
            return PlayState.CONTINUE;
        }

        // Priority 2: Walk
        if (state.isMoving() && !this.isRidingShoulder()) {
            state.getController().setAnimation(WALK_ANIM);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
    private static class TreasureSeekGoal extends Goal {
        private static final double LEASH_RADIUS = 24.0;
        private static final double CHEST_SEARCH_TRIGGER_RADIUS_SQR = 5.0 * 5.0;

        private final HermitCrabEntity crab;
        private int repathTimer = 0;
        private int chestSearchTimer = 0;

        TreasureSeekGoal(HermitCrabEntity crab) {
            this.crab = crab;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!crab.isTame() || crab.isOrderedToSit() || crab.isRidingShoulder()) return false;
            LivingEntity owner = crab.getOwner();
            return owner instanceof Player player && findTreasureMarker(player, crab.level()) != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            crab.setFoundChestPos(null);
            repathTimer = 0;
            chestSearchTimer = 0;
        }
        @Override
        public void stop() {
            crab.setFoundChestPos(null);
            crab.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player owner = (Player) crab.getOwner();
            if (owner == null) return;

            Vec3 marker = findTreasureMarker(owner, crab.level());
            if (marker == null) return;

            BlockPos chestPos = crab.getFoundChestPos();
            if (chestPos != null) {
                crab.getLookControl().setLookAt(chestPos.getX() + 0.5, chestPos.getY() + 1.0, chestPos.getZ() + 0.5);
                if (--repathTimer <= 0) {
                    repathTimer = 20;
                    crab.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 1.0);
                }
                return;
            }

            Vec3 ownerPos = owner.position();
            Vec3 toMarker = new Vec3(marker.x - ownerPos.x, 0, marker.z - ownerPos.z);
            Vec3 target = toMarker.lengthSqr() > LEASH_RADIUS * LEASH_RADIUS
                    ? ownerPos.add(toMarker.normalize().scale(LEASH_RADIUS))
                    : new Vec3(marker.x, ownerPos.y, marker.z);

            if (--repathTimer <= 0) {
                repathTimer = 20;
                crab.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            }

            double distToMarkerXZSqr = crab.position().distanceToSqr(marker.x, crab.getY(), marker.z);
            if (distToMarkerXZSqr < CHEST_SEARCH_TRIGGER_RADIUS_SQR && --chestSearchTimer <= 0) {
                chestSearchTimer = 100;
                crab.setFoundChestPos(locateChest(crab.level(), Mth.floor(marker.x), Mth.floor(marker.z)));
            }
        }

        @Nullable
        private static Vec3 findTreasureMarker(Player owner, Level level) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = owner.getItemInHand(hand);
                if (!(stack.getItem() instanceof MapItem)) continue;

                MapItemSavedData data = MapItem.getSavedData(stack, level);
                if (data == null || data.dimension != level.dimension()) continue;

                for (MapDecoration deco : data.getDecorations()) {
                    if (deco.getType() == MapDecoration.Type.RED_X) {
                        double blocksPerPixel = (1 << data.scale) / 2.0;
                        double worldX = data.centerX + deco.getX() * blocksPerPixel;
                        double worldZ = data.centerZ + deco.getY() * blocksPerPixel;
                        return new Vec3(worldX, 0, worldZ);
                    }
                }
            }
            return null;
        }

        @Nullable
        private static BlockPos locateChest(Level level, int centerX, int centerZ) {
            int topY = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ), level.getMaxBuildHeight() - 1);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int y = topY; y >= level.getMinBuildHeight(); y--) {
                        pos.set(centerX + dx, y, centerZ + dz);
                        if (level.getBlockState(pos).is(Blocks.CHEST)) {
                            return pos.immutable();
                        }
                    }
                }
            }
            return null;
        }
    }
}
