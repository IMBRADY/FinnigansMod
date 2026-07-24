package net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.lang.reflect.Field;
import java.util.List;

public class AmethystBeamEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(AmethystBeamEntity.class, EntityDataSerializers.FLOAT);

    private static final double MAX_RANGE = 40.0D;
    private static final float BEAM_DAMAGE = 3.0F;
    private static final int DAMAGE_TICK_RATE = 4;
    private BlockPos lastLightPos = null;

    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(AmethystBeamEntity.class, EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LENGTH, (float) MAX_RANGE);
        this.entityData.define(OWNER_ID, -1);
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
        this.entityData.set(OWNER_ID, owner.getId());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (OWNER_ID.equals(key)) {
            int id = this.entityData.get(OWNER_ID);
            if (id != -1 && this.level().getEntity(id) instanceof LivingEntity living) {
                this.owner = living;
            }
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation BEAM_ANIM = RawAnimation.begin().thenLoop("animation"); // match your .animation.json name

    private LivingEntity owner;
    private int age = 0;

    private static final Field INVULNERABLE_TIME_FIELD;
    static {
        Field field = null;
        try {
            field = Entity.class.getDeclaredField("invulnerableTime");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        INVULNERABLE_TIME_FIELD = field;
    }

    private static void clearInvulnerability(LivingEntity target) {
        if (INVULNERABLE_TIME_FIELD != null) {
            try {
                INVULNERABLE_TIME_FIELD.setInt(target, 0);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public AmethystBeamEntity(EntityType<? extends AmethystBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(MAX_RANGE);
    }

    public float getLength() {
        return this.entityData.get(LENGTH);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            updateLight();
        }

        boolean stillChanneling = owner != null && owner.isAlive()
                && owner.isUsingItem()
                && owner.getUseItem().getItem() == ModItems.AMETHYST_CUTLASS.get();

        if (!stillChanneling) {
            this.discard();
            return;
        }

        Vec3 eyePos = owner.getEyePosition(1.0F);
        Vec3 look = owner.getViewVector(1.0F);

        Vec3 origin = eyePos.add(look.scale(0.35D)).add(0, -0.35D, 0); // look.scale(n) - low close it is to player, 2nd number = y offset

        this.setPos(origin.x, origin.y, origin.z);
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());

        Vec3 end = origin.add(look.scale(MAX_RANGE));
        double length = MAX_RANGE;

        BlockHitResult blockHit = this.level().clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            length = origin.distanceTo(blockHit.getLocation());
        }
        this.entityData.set(LENGTH, (float) length);

        if (age % DAMAGE_TICK_RATE == 0) {
            dealDamageAlongBeam(origin, origin.add(look.scale(length)));
        }

        age++;
    }

    private void updateLight() {
        BlockPos currentPos = this.blockPosition();
        if (currentPos.equals(lastLightPos)) return;

        if (lastLightPos != null && this.level().getBlockState(lastLightPos).is(Blocks.LIGHT)) {
            this.level().setBlock(lastLightPos, Blocks.AIR.defaultBlockState(), 3);
        }

        if (this.level().getBlockState(currentPos).isAir()) {
            this.level().setBlock(currentPos,
                    Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
            lastLightPos = currentPos;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && lastLightPos != null) {
            if (this.level().getBlockState(lastLightPos).is(Blocks.LIGHT)) {
                this.level().setBlock(lastLightPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        super.remove(reason);
    }

    private void dealDamageAlongBeam(Vec3 start, Vec3 end) {
        List<Entity> candidates = this.level().getEntities(owner,
                owner.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0D));

        Entity closestHit = null;
        double closestDistSq = start.distanceToSqr(end);

        for (Entity candidate : candidates) {
            if (!candidate.isPickable() || candidate == owner) continue;
            var hit = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (hit.isPresent()) {
                double distSq = start.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closestHit = candidate;
                }
            }
        }

        if (closestHit instanceof LivingEntity target) {
            clearInvulnerability(target);
            target.hurt(owner.damageSources().mobAttack(owner), BEAM_DAMAGE);
        }
    }

    private PlayState predicate(software.bernie.geckolib.core.animation.AnimationState<AmethystBeamEntity> state) {
        state.getController().setAnimation(BEAM_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "beamController", 0, this::predicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
}