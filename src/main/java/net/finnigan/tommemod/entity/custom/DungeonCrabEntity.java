package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

public class DungeonCrabEntity extends Monster implements GeoEntity {

    private static final double PROXIMITY_REVEAL_DISTANCE_SQR = 5.0 * 5.0;
    private int ticksWithoutTarget = 0;
    private static final int REHIDE_DELAY = 100;
    private static final double MAX_CHASE_DISTANCE_SQR = 20.0 * 20.0;

    private static final EntityDataAccessor<Boolean> DATA_REVEALED =
            SynchedEntityData.defineId(DungeonCrabEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean isAttacking = false;

    public DungeonCrabEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_REVEALED, false);
    }

    public boolean isRevealed() {
        return this.entityData.get(DATA_REVEALED);
    }

    private void reveal() {
        this.entityData.set(DATA_REVEALED, true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Revealed", this.isRevealed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_REVEALED, tag.getBoolean("Revealed"));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null));
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null) {
            this.reveal();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result) {
            this.reveal(); // being hit always reveals it, even before it has a target
        }
        return result;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        this.isAttacking = true;
        boolean result = super.doHurtTarget(target);
        this.isAttacking = false;
        return result;
    }

    // While hidden, it shouldn't wander, look around oddly, or otherwise visually "act alive."
    @Override
    public void aiStep() {
        if (!this.isRevealed() && this.getTarget() == null) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0); // stay put, but still respect gravity
            this.setYRot(this.yRotO);
            this.setYHeadRot(this.yRotO);
        }
        super.aiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (!this.isRevealed()) {
            Player nearestPlayer = this.level().getNearestPlayer(this, Math.sqrt(PROXIMITY_REVEAL_DISTANCE_SQR));
            if (nearestPlayer != null) {
                this.reveal();
            }
            return;
        }

        LivingEntity target = this.getTarget();
        boolean targetInvalid = target == null || !target.isAlive() || this.distanceToSqr(target) > MAX_CHASE_DISTANCE_SQR;

        if (targetInvalid) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            ticksWithoutTarget++;
            if (ticksWithoutTarget >= REHIDE_DELAY) {
                this.entityData.set(DATA_REVEALED, false);
                ticksWithoutTarget = 0;
            }
        } else {
            ticksWithoutTarget = 0;
        }
    }

    public static boolean checkDungeonCrabSpawnRules(EntityType<DungeonCrabEntity> type, ServerLevelAccessor level,
                                                     MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) > 0) return false;

        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return false;

        var structureManager = serverLevel.structureManager();
        var strongholdStructure = serverLevel.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(net.minecraft.world.level.levelgen.structure.BuiltinStructures.STRONGHOLD);

        if (strongholdStructure == null) return false;

        return structureManager.getStructureWithPieceAt(pos, strongholdStructure).isValid();
    }

    private static boolean isStoneBrickVariant(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "stateController", 0, this::statePredicate)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack")));
    }

    private PlayState statePredicate(AnimationState<DungeonCrabEntity> state) {
        if (!this.isRevealed()) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("hide"));
            return PlayState.CONTINUE;
        }

        if (this.isAttacking) {
            return PlayState.CONTINUE;
        }

        state.getController().setAnimation(RawAnimation.begin().thenPlay("open").thenLoop("crawl"));
        return PlayState.CONTINUE;
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}