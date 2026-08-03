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
import net.minecraft.world.phys.Vec3;

/**
 * Arackopesh's web line, tuned to swing like Spider-Man rather than winch like a fishing rod. The hook
 * itself flies fast and dead straight (no gravity), and the reel does not drag the player onto the
 * anchor point: it lifts them off hard once, then hands them back to a heavier-than-normal gravity, so
 * they arc through a parabola under the anchor instead of being parked at it. Letting go mid-arc is
 * the point - see {@link #launchOnRelease}.
 */
public class GrappleHookEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BOOLEAN);

    /** Distance the line can reach before it gives up. Bounded by distance rather than by a tick count
     * because the hook now flies several blocks per tick - the old 60-tick budget would let it cross
     * hundreds of blocks. */
    private static final double MAX_FLIGHT_DISTANCE = 32.0;

    private Vec3 launchPos = null;

    /** One-off upward/inward kick applied on the tick the line goes taut. */
    private static final double INITIAL_PULL_STRENGTH = 1.35;
    /** Extra downward acceleration during the swing - what bends the pull into an arc. */
    private static final double SWING_GRAVITY = 0.075;
    /** Gentle inward tug, so the swing tracks toward the anchor instead of flying off tangentially. */
    private static final double REEL_STRENGTH = 0.055;
    /** Below this the player has arrived; the line stops pulling rather than shoving them around. */
    private static final double ARRIVAL_DISTANCE = 2.5;

    /** Speed of the boost given when the player lets go of a taut line. */
    private static final double RELEASE_BOOST = 0.95;
    /** How much of the swing's own momentum is folded into that boost's direction. */
    private static final double RELEASE_MOMENTUM_BLEND = 0.55;

    private boolean initialPullApplied = false;

    public GrappleHookEntity(EntityType<? extends GrappleHookEntity> type, Level level) {
        super(type, level);
    }

    public GrappleHookEntity(Level level, Player owner) {
        super(ModEntityTypes.GRAPPLE_HOOK.get(), owner, level);
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
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
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

        boolean stillHolding = owner.isUsingItem() && owner.getUseItem().getItem() instanceof ArackopeshItem;
        if (!stillHolding) {
            this.discard(); // released -> detach immediately
            return;
        }

        if (isStuck()) {
            swing(owner);
        } else {
            if (launchPos == null) launchPos = this.position();
            if (this.position().distanceTo(launchPos) > MAX_FLIGHT_DISTANCE) this.discard();
        }
    }

    /**
     * One hard yank to get the player airborne, then a light inward tug fighting a heavier-than-normal
     * gravity. The player is never snapped to the anchor - they fall through an arc beneath it, which
     * is what makes the release boost's direction meaningful.
     */
    private void swing(Player owner) {
        Vec3 toHook = this.position().subtract(owner.position());
        double distance = toHook.length();

        if (!initialPullApplied) {
            initialPullApplied = true;
            owner.setDeltaMovement(toHook.normalize().scale(INITIAL_PULL_STRENGTH));
            owner.hurtMarked = true;
            owner.fallDistance = 0;
            return;
        }

        Vec3 velocity = owner.getDeltaMovement();
        if (distance > ARRIVAL_DISTANCE) {
            velocity = velocity.add(toHook.normalize().scale(REEL_STRENGTH));
        }
        velocity = velocity.subtract(0, SWING_GRAVITY, 0);

        owner.setDeltaMovement(velocity);
        owner.hurtMarked = true;
        owner.fallDistance = 0;
    }

    /**
     * Called when the player lets go of a taut line: sends them off along their facing, blended with
     * wherever the swing was already carrying them. Release at the bottom of the arc (moving level)
     * and they shoot forward; release on the way up and they carry that climb with them.
     */
    public void launchOnRelease(Player owner) {
        if (!isStuck() || !initialPullApplied) return;

        Vec3 facing = owner.getLookAngle().normalize();
        Vec3 momentum = owner.getDeltaMovement();

        Vec3 direction = facing.add(momentum.scale(RELEASE_MOMENTUM_BLEND));
        if (direction.lengthSqr() < 1.0E-6) direction = facing;

        owner.setDeltaMovement(direction.normalize().scale(RELEASE_BOOST));
        owner.hurtMarked = true;
        owner.fallDistance = 0;
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
        return 0F; // flies dead straight out, and holds position once anchored
    }
}
