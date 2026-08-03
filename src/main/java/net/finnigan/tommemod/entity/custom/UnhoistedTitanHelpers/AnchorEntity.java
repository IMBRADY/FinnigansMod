package net.finnigan.tommemod.entity.custom.UnhoistedTitanHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.custom.FireKatanaItem;
import net.finnigan.tommemod.item.custom.UnhoistedTitanItem;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

/**
 * Unhoisted Titan's thrown anchor. Unlike Colletis's vine (which is a hold-to-keep-pulling channel),
 * this is fire-and-forget: it flies out, then always returns to the thrower under its own power,
 * whether it hit something or ran out of range. A living entity it hits takes {@link #IMPACT_DAMAGE}
 * and, if it survives, is dragged along behind the returning anchor - the reel itself reuses
 * ColletisVineEntity's pull math unchanged so both weapons feel identical to be caught by.
 */
public class AnchorEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> RETURNING =
            SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.BOOLEAN);

    private static final float IMPACT_DAMAGE = 10.0F;
    private static final int MAX_OUTBOUND_TICKS = 30; // ~1.5s of flight before it turns around on its own
    private static final double RETURN_SPEED = 1.2;
    private static final double CATCH_DISTANCE = 1.8;

    private int outboundTicks = 0;

    // Not synced - only ever resolved server-side via ServerLevel#getEntity(UUID).
    private UUID hookedUUID = null;

    public AnchorEntity(EntityType<? extends AnchorEntity> type, Level level) {
        super(type, level);
    }

    public AnchorEntity(Level level, Player owner) {
        super(ModEntityTypes.ANCHOR.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(RETURNING, false);
    }

    public boolean isReturning() {
        return this.entityData.get(RETURNING);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_INGOT; // placeholder icon, unused visually since we custom-render
    }

    @Override
    protected float getGravity() {
        return isReturning() ? 0F : 0.03F; // slight arc on the way out, dead straight on the way back
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        startReturning();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        // On the way back the anchor is travelling alongside whatever it's dragging, so its collision
        // would otherwise keep re-hitting (and re-hooking) that same target the whole way home.
        if (isReturning()) return;

        if (!level().isClientSide && result.getEntity() instanceof LivingEntity living
                && living != getOwner() && FireKatanaItem.isValidFireTarget(living)) {
            Player owner = getOwnerPlayer();
            living.hurt(owner != null
                    ? owner.damageSources().playerAttack(owner)
                    : level().damageSources().generic(), IMPACT_DAMAGE);
            if (living.isAlive()) {
                this.hookedUUID = living.getUUID();
            }
        }
        startReturning();
    }

    /**
     * A ThrowableItemProjectile's swept collision only tests the anchor's own small hitbox, so a
     * chain thrown over a low wall never registers even though the rendered line visibly passes
     * through it (see AnchorRenderer). Same fix as ColletisVineEntity: raycast the rendered line
     * itself each tick and turn around at the first block it crosses.
     */
    private void checkChainBlockedByBlock() {
        Player owner = getOwnerPlayer();
        if (owner == null) return;

        Vec3 start = owner.getEyePosition();
        Vec3 end = this.position();
        if (start.distanceToSqr(end) < 0.04) return; // hasn't flown far enough yet for this to matter

        HitResult hit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            startReturning();
        }
    }

    private void startReturning() {
        if (isReturning()) return;
        this.entityData.set(RETURNING, true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !isReturning()) {
            checkChainBlockedByBlock();
        }

        super.tick();

        if (level().isClientSide) return;

        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        if (!isReturning()) {
            if (++outboundTicks > MAX_OUTBOUND_TICKS) startReturning();
            return;
        }

        Vec3 toOwner = owner.getEyePosition().subtract(this.position());
        double distance = toOwner.length();
        if (distance < CATCH_DISTANCE) {
            this.discard();
            return;
        }

        this.setDeltaMovement(toOwner.normalize().scale(RETURN_SPEED));
        reelHookedTarget(owner);
    }

    /** Drags whatever the anchor bit into toward the thrower, matching ColletisVineEntity's feel. */
    private void reelHookedTarget(Player owner) {
        if (hookedUUID == null || !(level() instanceof ServerLevel serverLevel)) return;

        Entity resolved = serverLevel.getEntity(hookedUUID);
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            hookedUUID = null;
            return;
        }

        Vec3 toOwner = owner.position().subtract(target.position());
        double distance = toOwner.length();

        if (distance < CATCH_DISTANCE) {
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 1.0, 0.2));
        } else {
            target.setDeltaMovement(toOwner.normalize().scale(Math.min(distance * 0.2, 1.2)).add(0, 0.1, 0));
            target.hurtMarked = true;
            target.fallDistance = 0;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        Player owner = getOwnerPlayer();
        if (owner != null) {
            UnhoistedTitanItem.clearAnchorFor(owner.getUUID());
        }
    }

    private Player getOwnerPlayer() {
        return this.getOwner() instanceof Player player ? player : null;
    }
}
