package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CapybaraEntity extends Animal implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final TagKey<Item> CAPYBARA_FOOD = ItemTags.create(new ResourceLocation(TommeMod.MOD_ID, "capybara_food"));
    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation EAT_ANIM = RawAnimation.begin().thenLoop("eat");
    private static final RawAnimation SIT_ANIM = RawAnimation.begin().thenPlay("sit");

    private PanicGoal panicGoal;

    private static final EntityDataAccessor<Integer> IDLE_STATE =
            SynchedEntityData.defineId(CapybaraEntity.class, EntityDataSerializers.INT);

    private int actionTimer = 0;

    public CapybaraEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 5;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IDLE_STATE, 0);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            if (this.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
                this.entityData.set(IDLE_STATE, 0);
                this.actionTimer = 0;
            } else {
                if (this.actionTimer > 0) {
                    this.actionTimer--;
                    if (this.actionTimer <= 0) {
                        this.entityData.set(IDLE_STATE, 0);
                    }
                } else {
                    if (this.random.nextFloat() < 0.005F) {
                        int choice = this.random.nextBoolean() ? 1 : 2;
                        this.entityData.set(IDLE_STATE, choice);
                        this.actionTimer = this.random.nextInt(40) + 60;
                    }
                }
            }
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.panicGoal = new PanicGoal(this, 1.5D);
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D)); // Enables the breeding AI behavior
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D, Ingredient.of(CAPYBARA_FOOD), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    // --- Breeding Logic ---

    @Override
    public boolean isFood(ItemStack stack) {
        // Defines Melon Slices as the item that triggers Love Mode / Breeding
        return stack.is(CAPYBARA_FOOD);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob ageableMob) {
        // Spawns a brand new baby capybara when breeding completes
        return ModEntityTypes.CAPYBARA.get().create(level);
    }

    // ----------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("IdleState", this.entityData.get(IDLE_STATE));
        tag.putInt("ActionTimer", this.actionTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(IDLE_STATE, tag.getInt("IdleState"));
        this.actionTimer = tag.getInt("ActionTimer");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::predicate));
    }

    private <E extends CapybaraEntity> PlayState predicate(AnimationState<E> state) {
        // Priority 1: Check if the capybara is actively fleeing from damage/danger
        if (this.panicGoal != null && this.panicGoal.isRunning()) {
            state.getController().setAnimation(RUN_ANIM);
            return PlayState.CONTINUE;
        }

        // Priority 2: General navigation / following player
        if (state.isMoving()) {
            state.getController().setAnimation(WALK_ANIM);
            return PlayState.CONTINUE;
        }

        // Priority 3: Idle routines
        int currentState = this.entityData.get(IDLE_STATE);
        if (currentState == 1) {
            state.getController().setAnimation(EAT_ANIM);
            return PlayState.CONTINUE;
        } else if (currentState == 2) {
            state.getController().setAnimation(SIT_ANIM);
            return PlayState.CONTINUE;
        }

        return PlayState.STOP;
    }

    public static boolean checkCapybaraSpawnRules(EntityType<CapybaraEntity> type, LevelAccessor level,
                                                  MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getBiome(pos).is(BiomeTags.IS_JUNGLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}