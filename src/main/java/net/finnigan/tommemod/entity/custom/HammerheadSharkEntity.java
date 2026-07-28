package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.HammerheadHelpers.HammerheadWanderGoal;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Aggressive deep-ocean predator: never stops swimming (see HammerheadWanderGoal), chases down
 * players once it spots one, and bites on contact. The head bone independently tracks look
 * direction via GeckoLib (see HammerheadSharkModel) on top of whatever the current swim/chase clip
 * is doing.
 */
public class HammerheadSharkEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HammerheadSharkEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.6D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 30.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, true));
        this.goalSelector.addGoal(2, new HammerheadWanderGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public int getMaxAirSupply() {
        return 4800;
    }

    @Override
    protected int increaseAirSupply(int air) {
        return this.getMaxAirSupply();
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result) {
            this.triggerAnim("attackController", "bite");
        }
        return result;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.DOLPHIN_AMBIENT_WATER;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (recentlyHit) {
            int count = 4 + this.random.nextInt(2); // 4-5
            for (int i = 0; i < count; i++) {
                this.spawnAtLocation(new ItemStack(ModItems.FIN.get()));
            }
        }
    }

    // Deep ocean only, moderately-to-rarely (see biome_modifier for spawn weight).
    public static boolean checkHammerheadSpawnRules(EntityType<HammerheadSharkEntity> type, ServerLevelAccessor level,
                                                     MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() <= level.getSeaLevel() - 12
                && level.getFluidState(pos).is(FluidTags.WATER)
                && level.getBiomeManager().getBiome(pos).is(BiomeTags.IS_DEEP_OCEAN);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "swimController", 0, this::swimPredicate));
        controllers.add(new AnimationController<>(this, "attackController", 0, this::attackPredicate)
                .triggerableAnim("bite", RawAnimation.begin().then("bite", Animation.LoopType.PLAY_ONCE)));
    }

    private PlayState swimPredicate(AnimationState<HammerheadSharkEntity> state) {
        boolean hasTarget = this.getTarget() != null && this.getTarget().isAlive();
        state.getController().setAnimation(hasTarget
                ? RawAnimation.begin().thenLoop("chase")
                : RawAnimation.begin().thenLoop("Swim"));
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<HammerheadSharkEntity> state) {
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
