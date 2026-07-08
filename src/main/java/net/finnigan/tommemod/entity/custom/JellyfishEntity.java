package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class JellyfishEntity extends WaterAnimal implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int stingCooldown = 0;
    private static final int MAX_DEPTH_BELOW_SURFACE = 3; // how close jellyfish come to surface

    public JellyfishEntity(EntityType<? extends WaterAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new JellyfishMoveControl(this);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new JellyfishRandomSwimGoal(this));
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
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isInWater()) {
            this.moveRelative(0.1F, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "swim_controller", 5, this::swimPredicate));
    }

    private PlayState swimPredicate(AnimationState<JellyfishEntity> state) {
        state.getController().setAnimation(RawAnimation.begin().thenLoop("swim"));
        return PlayState.CONTINUE;
    }

    public static boolean checkSurfaceWaterAnimalSpawnRules(
            EntityType<? extends JellyfishEntity> type, ServerLevelAccessor level,
            MobSpawnType reason, BlockPos pos, RandomSource random) {
        return pos.getY() >= level.getSeaLevel() - 13
                && pos.getY() <= level.getSeaLevel() - 1
                && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private static float rotlerp(float current, float target, float maxChange) {
        float diff = Mth.wrapDegrees(target - current);
        diff = Mth.clamp(diff, -maxChange, maxChange);
        return current + diff;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.stingCooldown > 0) {
            this.stingCooldown--;
        }

        if (!this.level().isClientSide && this.stingCooldown == 0) {
            List<Player> players = this.level().getEntitiesOfClass(
                    Player.class, this.getBoundingBox().inflate(0.2D));

            for (Player player : players) {
                if (!player.isCreative() && !player.isSpectator()) {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 2));
                    this.stingCooldown = 20;
                    break;
                }
            }
        }
    }

    static class JellyfishMoveControl extends MoveControl {
        private final JellyfishEntity jellyfish;

        public JellyfishMoveControl(JellyfishEntity jellyfish) {
            super(jellyfish);
            this.jellyfish = jellyfish;
        }

        @Override
        public void tick() {

            if (this.operation != Operation.MOVE_TO) {
                jellyfish.setDeltaMovement(jellyfish.getDeltaMovement().scale(0.98D));
                return;
            }

            Vec3 delta = new Vec3(
                    this.wantedX - jellyfish.getX(),
                    this.wantedY - jellyfish.getY(),
                    this.wantedZ - jellyfish.getZ());
            double distance = delta.length();

            if (distance < 0.1D) {
                this.operation = Operation.WAIT;
                jellyfish.setDeltaMovement(jellyfish.getDeltaMovement().scale(0.5D));
                return;
            }

            Vec3 direction = delta.scale(1.0D / distance);

            float desiredYRot = (float) (Mth.atan2(direction.z, direction.x) * (180F / Math.PI)) - 90F;
            float desiredXRot = (float) -(Mth.atan2(direction.y,
                    Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180F / Math.PI));

            jellyfish.setYRot(rotlerp(jellyfish.getYRot(), desiredYRot, 4.0F));
            jellyfish.yBodyRot = jellyfish.getYRot();
            jellyfish.setXRot(rotlerp(jellyfish.getXRot(), desiredXRot, 4.0F));

            float yDiff = Mth.abs(Mth.wrapDegrees(desiredYRot - jellyfish.getYRot()));

            double baseSpeed = this.speedModifier * jellyfish.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double horizontalSpeed = baseSpeed * 0.02D;
            double verticalSpeed = baseSpeed * 0.08D;

            double vThrust = direction.y * verticalSpeed;

            double hThrustX = 0, hThrustZ = 0;
            if (yDiff < 15.0F) {
                hThrustX = direction.x * horizontalSpeed;
                hThrustZ = direction.z * horizontalSpeed;
            }

            jellyfish.setDeltaMovement(jellyfish.getDeltaMovement().add(hThrustX, vThrust, hThrustZ));
            }
        }

    /** Periodically picks a random nearby point to swim toward. */
    static class JellyfishRandomSwimGoal extends Goal {
        private final JellyfishEntity jellyfish;

        public JellyfishRandomSwimGoal(JellyfishEntity jellyfish) {
            this.jellyfish = jellyfish;
        }

        @Override
        public boolean canUse() {
            return jellyfish.getRandom().nextInt(10) == 0 || jellyfish.getDeltaMovement().lengthSqr() < 1.0E-5D;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = jellyfish.getRandom();
            double x = jellyfish.getX() + (random.nextFloat() * 2.0F - 1.0F) * 2.0F;
            double y = jellyfish.getY() + (random.nextFloat() * 2.0F - 1.0F) * 8.0F;
            double z = jellyfish.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 2.0F;

            jellyfish.getMoveControl().setWantedPosition(x, y, z, 1.0D);
            double surfaceY = findWaterSurfaceY();
            double maxY = surfaceY - MAX_DEPTH_BELOW_SURFACE;
            if (y > maxY) {
                y = maxY;
            }

            jellyfish.getMoveControl().setWantedPosition(x, y, z, 1.0D);
        }

        private double findWaterSurfaceY() {
            net.minecraft.core.BlockPos.MutableBlockPos pos =
                    new net.minecraft.core.BlockPos.MutableBlockPos(
                            jellyfish.getBlockX(), jellyfish.getBlockY(), jellyfish.getBlockZ());

            int maxScan = 32; // don't scan forever if something's weird
            int scanned = 0;
            while (jellyfish.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER) && scanned < maxScan) {
                pos.move(net.minecraft.core.Direction.UP);
                scanned++;
            }
            return pos.getY();
        }
    }
}