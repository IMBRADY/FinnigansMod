package net.finnigan.tommemod.entity.custom.LanternaHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.effect.EnchainedEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class LanternaChainsEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(LanternaChainsEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation COIL = RawAnimation.begin().thenLoop("animation");
    private static final int DURATION = 160;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    private int age;
    private int bonusStacks;

    public LanternaChainsEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public LanternaChainsEntity(Level level, LivingEntity target, Entity owner) {
        this(ModEntityTypes.LANTERNA_CHAINS.get(), level);
        entityData.set(TARGET_ID, target.getId());
        if (owner != null) ownerId = owner.getUUID();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET_ID, -1);
    }

    public LivingEntity getTarget() {
        Entity target = level().getEntity(entityData.get(TARGET_ID));
        return target instanceof LivingEntity living ? living : null;
    }

    public void addBonusStack() {
        if (level().isClientSide || age >= DURATION) return;
        LivingEntity target = getTarget();
        if (target == null) return;
        bonusStacks++;
        EnchainedEffect.setTriggeredBonusStacks(target, bonusStacks, DURATION - age);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        setPos(target.getX(), target.getY(), target.getZ());
        if (level().isClientSide) return;

        age++;
        ServerLevel server = (ServerLevel) level();
        double width = Math.max(0.35, target.getBbWidth() * 0.55);
        server.sendParticles(ParticleTypes.FLAME,
                target.getX() + (random.nextDouble() - 0.5) * target.getBbWidth(),
                target.getY() + random.nextDouble() * target.getBbHeight(),
                target.getZ() + (random.nextDouble() - 0.5) * target.getBbWidth(),
                3, width, target.getBbHeight() * 0.25, width, 0.015);

        if (age % 10 == 0) {
            Entity owner = ownerId == null ? null : server.getEntity(ownerId);
            float damage = 4.0F + bonusStacks;
            if (owner instanceof Player player) target.hurt(damageSources().playerAttack(player), damage);
            else target.hurt(damageSources().magic(), damage);
        }
        if (age >= DURATION) {
            EnchainedEffect.clear(target);
            discard();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "coil", 0,
                state -> state.setAndContinue(COIL)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            LivingEntity target = getTarget();
            if (target != null) EnchainedEffect.clear(target);
        }
        super.remove(reason);
    }
}
