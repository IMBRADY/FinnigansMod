package net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
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
import java.util.ArrayList;
import java.util.List;

public class AmethystBeamEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(AmethystBeamEntity.class, EntityDataSerializers.FLOAT);

    private static final double MAX_RANGE = 40.0D;
    private static final float BEAM_DAMAGE = 3.0F;
    private static final int DAMAGE_TICK_RATE = 4;
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

    public LivingEntity getOwner() {
        return this.owner;
    }

    // Shared with AmethystBeamRenderer so the visual beam can track the owner's live,
    // render-interpolated eye/look each frame instead of the beam entity's own position/
    // rotation, which only get copied from the owner once per game tick (see tick() below)
    // and were lagging a full tick behind the camera, reading as jitter whenever the player
    // moved the camera or walked.
    public static Vec3 computeOrigin(LivingEntity owner, float partialTick) {
        Vec3 eyePos = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = Vec3.directionFromRotation(0.0F, owner.getYRot())
                    .cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        right = right.normalize();

        boolean rightHand = owner.getMainArm() == HumanoidArm.RIGHT;
        if (owner.getUsedItemHand() == InteractionHand.OFF_HAND) rightHand = !rightHand;
        double side = rightHand ? 0.42D : -0.42D;

        return eyePos.add(look.scale(-0.5D)).add(right.scale(side)).add(0.0D, -0.38D, 0.0D);
    }

    public static Vec3 computeAimPoint(LivingEntity owner, float partialTick, double distance) {
        return owner.getEyePosition(partialTick).add(owner.getViewVector(partialTick).scale(distance));
    }

    /** Samples sources along the rendered beam so its entire length illuminates the world. */
    public List<Vec3> getDynamicLightPositions(float partialTick) {
        LivingEntity beamOwner = getOwner();
        if (beamOwner == null) return List.of(getPosition(partialTick));

        Vec3 start = computeOrigin(beamOwner, partialTick);
        Vec3 end = computeAimPoint(beamOwner, partialTick, getLength());
        double length = start.distanceTo(end);
        int segments = Math.max(1, (int) Math.ceil(length / 8.0D));
        List<Vec3> positions = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            positions.add(start.lerp(end, index / (double) segments));
        }
        return positions;
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

        boolean stillChanneling = owner != null && owner.isAlive()
                && owner.isUsingItem()
                && owner.getUseItem().getItem() == ModItems.AMETHYST_CUTLASS.get();

        if (!stillChanneling) {
            this.discard();
            return;
        }

        Vec3 origin = computeOrigin(owner, 1.0F);
        Vec3 look = owner.getViewVector(1.0F);

        this.setPos(origin.x, origin.y, origin.z);
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());

        Vec3 eye = owner.getEyePosition(1.0F);
        Vec3 crosshairEnd = eye.add(look.scale(MAX_RANGE));
        double aimDistance = MAX_RANGE;

        BlockHitResult blockHit = this.level().clip(new ClipContext(eye, crosshairEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            aimDistance = eye.distanceTo(blockHit.getLocation());
        }
        this.entityData.set(LENGTH, (float) aimDistance);
        Vec3 aimPoint = computeAimPoint(owner, 1.0F, aimDistance);

        if (age % DAMAGE_TICK_RATE == 0) {
            dealDamageAlongBeam(origin, aimPoint);
        }

        age++;
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
