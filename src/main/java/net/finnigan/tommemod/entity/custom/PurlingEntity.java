package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.PurlingHelpers.PurlingBreakChorusGoal;
import net.finnigan.tommemod.entity.custom.PurlingHelpers.PurlingEatChorusFruitGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
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

/**
 * Chorus-eating grazer of the outer End islands. Harmless: it strolls, tears down chorus plants for
 * the fruit and hoovers up loose chorus fruit, and its entire answer to being attacked is to warp
 * away exactly as if it had eaten one.
 *
 * Registered under MobCategory.MONSTER on purpose - that is the pool endermen spawn from, so the
 * brief's "1/15 as common as endermen" is an apples-to-apples weight comparison. It extends
 * PathfinderMob rather than Monster, so it stays passive and (unlike a real monster) is not wiped out
 * on Peaceful difficulty.
 */
public class PurlingEntity extends PathfinderMob implements GeoEntity {

    /** Length of the "teleport" animation (0.5s), in ticks - the warp fires on the last frame. */
    private static final int TELEPORT_ANIM_TICKS = 10;
    /** Chorus fruit teleports up to 8 blocks in each direction. */
    private static final double TELEPORT_RANGE = 24.0;
    private static final int TELEPORT_ATTEMPTS = 16;

    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation BREAK_ANIM = RawAnimation.begin().then("break", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation EAT_ANIM = RawAnimation.begin().then("eat", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation TELEPORT_ANIM = RawAnimation.begin().then("teleport", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int teleportCountdown = -1;

    public PurlingEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Loose fruit outranks standing plants - the brief gives eating priority.
        this.goalSelector.addGoal(1, new PurlingEatChorusFruitGoal(this));
        this.goalSelector.addGoal(2, new PurlingBreakChorusGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public static boolean checkPurlingSpawnRules(EntityType<PurlingEntity> type, ServerLevelAccessor level,
                                                 MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        // End islands are pitch dark, so deliberately no light check - just a solid floor to stand on.
        return level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type)
                && Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
    }

    /**
     * Any hit at all sends it running. The warp is delayed by the length of the "teleport" animation so
     * the animation is actually seen before the mob vanishes.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive() && this.teleportCountdown < 0) {
            this.teleportCountdown = TELEPORT_ANIM_TICKS;
            this.triggerAnim("actionController", "teleport");
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        if (this.teleportCountdown >= 0 && --this.teleportCountdown < 0) {
            teleportLikeChorusFruit();
        }
        super.aiStep();
    }

    /** Straight port of the chorus fruit's own teleport: 16 tries at a random spot within 8 blocks. */
    private void teleportLikeChorusFruit() {
        double fromX = this.getX();
        double fromY = this.getY();
        double fromZ = this.getZ();

        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            double x = fromX + (this.random.nextDouble() - 0.5D) * 2.0D * TELEPORT_RANGE;
            double y = Mth.clamp(fromY + (this.random.nextInt((int) TELEPORT_RANGE * 2) - TELEPORT_RANGE),
                    this.level().getMinBuildHeight(),
                    this.level().getMaxBuildHeight() - 1);
            double z = fromZ + (this.random.nextDouble() - 0.5D) * 2.0D * TELEPORT_RANGE;

            // randomTeleport(.., true) is what broadcasts the portal-particle trail to clients
            if (this.randomTeleport(x, y, z, true)) {
                this.level().gameEvent(GameEvent.TELEPORT, new Vec3(fromX, fromY, fromZ), GameEvent.Context.of(this));
                this.level().playSound(null, fromX, fromY, fromZ,
                        SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                this.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                return;
            }
        }
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotionController", 5, this::locomotionPredicate));
        controllers.add(new AnimationController<>(this, "actionController", 0, state -> PlayState.STOP)
                .triggerableAnim("break", BREAK_ANIM)
                .triggerableAnim("eat", EAT_ANIM)
                .triggerableAnim("teleport", TELEPORT_ANIM));
    }

    private PlayState locomotionPredicate(AnimationState<PurlingEntity> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(WALK_ANIM);
            return PlayState.CONTINUE;
        }
        // No idle animation exists for this model, so hold the rest pose (same as LivingArmorEntity).
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
