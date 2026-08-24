package net.finnigan.tommemod.entity.custom.LanternaHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.GrapplingHookSupport;
import net.finnigan.tommemod.effect.EnchainedEffect;
import net.finnigan.tommemod.item.custom.LanternaItem;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class LanternaChainEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(LanternaChainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETRACTING =
            SynchedEntityData.defineId(LanternaChainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ARRIVED =
            SynchedEntityData.defineId(LanternaChainEntity.class, EntityDataSerializers.BOOLEAN);
    public static final GrapplingHookSupport.Settings GRAPPLE_SETTINGS = new GrapplingHookSupport.Settings(
            new net.minecraft.resources.ResourceLocation(TommeMod.MOD_ID, "textures/entity/lanternchain.png"),
            new net.minecraft.resources.ResourceLocation(TommeMod.MOD_ID, "textures/entity/lanternprojectile.png"),
            3.0, 64.0, 0.9, 0.08, 0.16, 5.0, 1.5,
            0.11, 1.75, 0.995, 0.5F, 0.08F, 0.5F, -90.0F);
    private static final int LIGHT_LINGER_TICKS = 6;
    private UUID shotId = UUID.randomUUID();
    private int arrivalTicks;
    private boolean stateCleared;
    private final GrapplingHookSupport.State grappleState = new GrapplingHookSupport.State();

    public LanternaChainEntity(EntityType<? extends LanternaChainEntity> type, Level level) {
        super(type, level);
    }

    public LanternaChainEntity(Level level, Player owner, UUID shotId) {
        super(ModEntityTypes.LANTERNA_CHAIN.get(), owner, level);
        this.shotId = shotId;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STUCK, false);
        entityData.define(RETRACTING, false);
        entityData.define(ARRIVED, false);
    }

    public boolean isStuck() {
        return entityData.get(STUCK);
    }

    public boolean isRetracting() {
        return entityData.get(RETRACTING);
    }

    public boolean hasArrived() {
        return entityData.get(ARRIVED);
    }

    public int getDynamicLightLevel() {
        return hasArrived() ? Math.max(0, 15 - arrivalTicks * 3) : 15;
    }

    public void startRetract() {
        if (level().isClientSide || isRetracting() || hasArrived()) return;
        entityData.set(STUCK, false);
        entityData.set(RETRACTING, true);
        setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.CHAIN;
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (isRetracting() || hasArrived()) return;
        super.onHitBlock(hit);
        stick(hit.getLocation());
    }

    private void stick(Vec3 position) {
        setPos(position.x, position.y, position.z);
        entityData.set(STUCK, true);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (isRetracting() || hasArrived()) return;
        super.onHitEntity(hit);
        if (!level().isClientSide && hit.getEntity() instanceof LivingEntity target && target != getOwner()) {
            LanternaChainsEntity activeChains = level().getEntitiesOfClass(
                    LanternaChainsEntity.class, target.getBoundingBox().inflate(2.0),
                    chains -> chains.getTarget() == target).stream().findFirst().orElse(null);
            if (activeChains != null) {
                activeChains.addBonusStack();
            } else if (EnchainedEffect.addStack(target)) {
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 160, 0));
                LanternaChainsEntity chains = new LanternaChainsEntity(level(), target, getOwner());
                chains.moveTo(target.getX(), target.getY(), target.getZ());
                level().addFreshEntity(chains);
            }
            Player owner = ownerPlayer();
            if (owner != null) {
                LanternaItem.requireFreshClick(owner, shotId);
                owner.stopUsingItem();
            }
            startRetract();
        }
    }

    private void checkChainBlockedByBlock() {
        Player owner = ownerPlayer();
        if (owner == null) return;
        HitResult hit = level().clip(new ClipContext(owner.getEyePosition(), position(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            stick(blockHit.getLocation());
        }
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !isStuck() && !isRetracting() && !hasArrived()) checkChainBlockedByBlock();
        super.tick();

        Player owner = ownerPlayer();
        if (owner == null || !owner.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        if (hasArrived()) {
            setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
            setDeltaMovement(Vec3.ZERO);
            if (++arrivalTicks >= LIGHT_LINGER_TICKS && !level().isClientSide) discard();
            return;
        }

        if (isRetracting()) {
            if (!level().isClientSide) retractStep(owner);
            return;
        }

        if (!(owner.isUsingItem() && owner.getUseItem().getItem() instanceof LanternaItem)) {
            startRetract();
            return;
        }

        if (isStuck()) {
            pullOwner(owner);
        } else if (!level().isClientSide && distanceToSqr(owner) >=
                GRAPPLE_SETTINGS.maxDistance() * GRAPPLE_SETTINGS.maxDistance()) {
            startRetract();
        }
    }

    private void retractStep(Player owner) {
        Vec3 hand = owner.getEyePosition().add(0, -0.2, 0);
        Vec3 toHand = hand.subtract(position());
        if (toHand.length() <= GRAPPLE_SETTINGS.flightSpeed()) {
            setPos(hand.x, hand.y, hand.z);
            setDeltaMovement(Vec3.ZERO);
            entityData.set(RETRACTING, false);
            entityData.set(ARRIVED, true);
            clearWeaponState(owner);
            return;
        }
        setDeltaMovement(toHand.normalize().scale(GRAPPLE_SETTINGS.flightSpeed()));
    }

    private void pullOwner(Player owner) {
        float input = level().isClientSide ? owner.xxa : GrapplingHookSupport.getSwingInput(owner);
        GrapplingHookSupport.pullPlayer(this, owner, grappleState, GRAPPLE_SETTINGS, input);
        GrapplingHookSupport.grappleShorten(this, owner, grappleState, owner.isShiftKeyDown());
    }

    @Override
    public void remove(RemovalReason reason) {
        Player owner = ownerPlayer();
        super.remove(reason);
        if (!level().isClientSide && owner != null) clearWeaponState(owner);
    }

    private void clearWeaponState(Player owner) {
        if (stateCleared) return;
        stateCleared = true;
        LanternaItem.clearChain(owner, shotId, getId());
    }

    private Player ownerPlayer() {
        return getOwner() instanceof Player player ? player : null;
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }
}
