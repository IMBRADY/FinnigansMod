package net.finnigan.tommemod.entity.custom.ColletisHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.custom.ColletisItem;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
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

/**
 * Colletis's thorn vine. Direct adaptation of GrappleHookEntity: block hits pull the OWNER toward the
 * hook (grapple-hook, same as Arackopesh, reused unchanged). It used to also drag a hit enemy back
 * toward the owner; that mechanic was removed, so an entity hit now simply stops the vine dead.
 */
public class ColletisVineEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(ColletisVineEntity.class, EntityDataSerializers.BOOLEAN);

    private int ticksAlive = 0;
    private static final int MAX_LIFETIME_TICKS = 60; // 3s flight before giving up

    public ColletisVineEntity(EntityType<? extends ColletisVineEntity> type, Level level) {
        super(type, level);
    }

    public ColletisVineEntity(Level level, Player owner) {
        super(ModEntityTypes.COLLETIS_VINE.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STUCK, false);
    }

    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.STRING; // placeholder icon, unused visually since we custom-render
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        stick(result.getLocation());
    }

    /** Sticks the vine at the given world position, block-grapple style (see {@link #onHitBlock}). */
    private void stick(Vec3 pos) {
        this.setPos(pos.x, pos.y, pos.z);
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * A ThrowableItemProjectile's own swept collision only checks the small hitbox at the vine's tip,
     * so an arc that clears clean over the top of a low wall never registers a hit even though the
     * straight rendered chain (owner's eye -> current tip, see ColletisVineRenderer) visibly passes
     * through it. Every tick before moving further, raycast along that same line and grapple to the
     * first block it crosses, if any - this is what makes "the chain hits a block partway along its
     * length" behave like a real rope/vine instead of a projectile that can fly clean over obstacles.
     */
    private void checkChainBlockedByBlock() {
        Player owner = getOwnerPlayer();
        if (owner == null) return;

        Vec3 start = owner.getEyePosition();
        Vec3 end = this.position();
        if (start.distanceToSqr(end) < 0.04) return; // hasn't flown far enough yet for this to matter

        HitResult hit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            stick(blockHit.getLocation());
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !isStuck()) {
            checkChainBlockedByBlock();
        }

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
            pullOwnerTowardHook(owner);
        } else {
            ticksAlive++;
            if (ticksAlive > MAX_LIFETIME_TICKS) this.discard();
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
