package net.finnigan.tommemod.entity.custom.ColletisHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.custom.ColletisItem;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Colletis's thorn vine. Direct adaptation of GrappleHookEntity, differentiated so entity hits pull the
 * TARGET toward the owner (grapple-the-enemy) while block hits pull the OWNER toward the hook (grapple-hook,
 * same as Arackopesh, reused unchanged).
 */
public class ColletisVineEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(ColletisVineEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HIT_ENTITY =
            SynchedEntityData.defineId(ColletisVineEntity.class, EntityDataSerializers.BOOLEAN);

    private int ticksAlive = 0;
    private static final int MAX_LIFETIME_TICKS = 60; // 3s flight before giving up

    // Not synced - only ever resolved server-side via ServerLevel#getEntity(UUID).
    private UUID targetUUID = null;

    public ColletisVineEntity(EntityType<? extends ColletisVineEntity> type, Level level) {
        super(type, level);
    }

    public ColletisVineEntity(Level level, Player owner) {
        super(ModEntityTypes.COLLETIS_VINE.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STUCK, false);
        this.entityData.define(HIT_ENTITY, false);
    }

    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    public boolean isHitEntity() {
        return this.entityData.get(HIT_ENTITY);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.STRING; // placeholder icon, unused visually since we custom-render
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.entityData.set(STUCK, true);
        this.entityData.set(HIT_ENTITY, false);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (result.getEntity() instanceof LivingEntity living && living != getOwner()) {
            this.targetUUID = living.getUUID();
            this.entityData.set(HIT_ENTITY, true);
        }
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();

        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        boolean stillHolding = owner.isUsingItem() && owner.getUseItem().getItem() instanceof ColletisItem;
        if (!stillHolding) {
            this.discard(); // released -> retract/detach immediately, which also stops any active pull
            return;
        }

        if (isStuck()) {
            if (isHitEntity()) {
                pullTargetTowardOwner(owner);
            } else {
                pullOwnerTowardHook(owner);
            }
        } else {
            ticksAlive++;
            if (ticksAlive > MAX_LIFETIME_TICKS) this.discard();
        }
    }

    /** Grapple-the-enemy branch: the hit LivingEntity is dragged toward the owner. */
    private void pullTargetTowardOwner(Player owner) {
        if (!(level() instanceof ServerLevel serverLevel) || targetUUID == null) return;

        Entity resolved = serverLevel.getEntity(targetUUID);
        if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
            this.discard();
            return;
        }

        Vec3 toOwner = owner.position().subtract(target.position());
        double distance = toOwner.length();

        if (distance < 1.8) {
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 1.0, 0.2));
        } else {
            Vec3 pull = toOwner.normalize().scale(Math.min(distance * 0.2, 1.2)).add(0, 0.1, 0);
            target.setDeltaMovement(pull);
            target.hurtMarked = true;
            target.fallDistance = 0;
        }
    }

    /** Grapple-hook branch: reuses Arackopesh's GrappleHookEntity logic unchanged. */
    private void pullOwnerTowardHook(Player owner) {
        Vec3 toHook = this.position().subtract(owner.position());
        double distance = toHook.length();

        if (distance < 1.8) {
            owner.setDeltaMovement(owner.getDeltaMovement().multiply(0.2, 1.0, 0.2)); // gentle hover, no more shoving
        } else {
            Vec3 pull = toHook.normalize().scale(Math.min(distance * 0.2, 1.2)).add(0, 0.1, 0);
            owner.setDeltaMovement(pull);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (getOwnerPlayer() != null) {
            ColletisItem.clearVineFor(getOwnerPlayer().getUUID());
        }
    }

    private Player getOwnerPlayer() {
        if (this.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    @Override
    protected float getGravity() {
        return isStuck() ? 0F : 0.03F; // creates arc when flying
    }
}
