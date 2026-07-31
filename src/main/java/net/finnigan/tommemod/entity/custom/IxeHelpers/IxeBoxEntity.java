package net.finnigan.tommemod.entity.custom.IxeHelpers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/** Purely cosmetic: the ice-box shell that appears around an entity while Frozen is active, following it each tick. */
public class IxeBoxEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID targetUUID;
    private int remainingTicks;

    public IxeBoxEntity(EntityType<? extends IxeBoxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void followTarget(LivingEntity target, int durationTicks) {
        this.targetUUID = target.getUUID();
        this.remainingTicks = durationTicks;
    }

    @Override
    protected void defineSynchedData() {
        // no synced fields needed; position is pushed directly each server tick
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) return;

        remainingTicks--;
        if (remainingTicks <= 0 || targetUUID == null) {
            discard();
            return;
        }

        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        Entity target = serverLevel.getEntity(targetUUID);
        if (!(target instanceof LivingEntity living) || !living.isAlive() || !living.hasEffect(net.finnigan.tommemod.effect.ModMobEffects.FROZEN.get())) {
            discard();
            return;
        }

        this.setPos(living.getX(), living.getY(), living.getZ());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Target")) this.targetUUID = tag.getUUID("Target");
        this.remainingTicks = tag.getInt("RemainingTicks");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (targetUUID != null) tag.putUUID("Target", targetUUID);
        tag.putInt("RemainingTicks", remainingTicks);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // static prop, no animation controllers needed
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
