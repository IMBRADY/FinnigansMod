package net.finnigan.tommemod.entity.custom.ArackopeshHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.custom.ArackopeshItem;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GrappleHookEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BOOLEAN);

    private int ownerId = -1;
    private int ticksAlive = 0;
    private static final int MAX_LIFETIME_TICKS = 60; // 3s flight before giving up

    public GrappleHookEntity(EntityType<? extends GrappleHookEntity> type, Level level) {
        super(type, level);
    }

    public GrappleHookEntity(Level level, Player owner) {
        super(ModEntityTypes.GRAPPLE_HOOK.get(), owner, level);
        this.ownerId = owner.getId();
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
        return net.minecraft.world.item.Items.STRING; // placeholder icon, unused visually since we custom-render
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();

        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        boolean stillHolding = owner.isUsingItem() && owner.getUseItem().getItem() instanceof ArackopeshItem;
        if (!stillHolding) {
            this.discard(); // released -> detach immediately
            return;
        }

        if (isStuck()) {
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
        } else {
            ticksAlive++;
            if (ticksAlive > MAX_LIFETIME_TICKS) this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (getOwnerPlayer() != null) {
            ArackopeshItem.clearHookFor(getOwnerPlayer().getUUID());
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