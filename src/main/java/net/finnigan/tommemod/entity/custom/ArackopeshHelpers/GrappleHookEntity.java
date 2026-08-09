package net.finnigan.tommemod.entity.custom.ArackopeshHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.custom.ArackopeshItem;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arackopesh's web line: a straight winch, not a pendulum.
 *
 * The line is entirely governed by whether right click is still held. Held, it keeps paying out until it
 * bites; once anchored, held keeps reeling the player straight along the line. Let go at any point and the
 * hook stops whatever it was doing and flies home, at which point it is gone.
 *
 * Nothing here applies gravity - not to the hook, and not to the player on a taut line. The pull is
 * resolved along the line and everything off-axis is left to the player, who can shove it around hard with
 * the crosshair (see {@link #reel}); that off-axis freedom is the whole feel of the weapon, so it is
 * rebuilt from the horizontal plane every tick rather than being allowed to accumulate a downward sag.
 */
public class GrappleHookEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BOOLEAN);
    /** Synced because the client runs the reel for its own player and has to stop when the line lets go. */
    private static final EntityDataAccessor<Boolean> RETRACTING =
            SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BOOLEAN);

    /** Speed the winch settles at once taut, in blocks per tick. */
    private static final double REEL_SPEED = 1.15;
    /** Ramp onto REEL_SPEED over a few ticks so the line grabs instead of snapping the player's neck. */
    private static final double REEL_ACCELERATION = 0.32;
    /** Close enough to the anchor that pulling harder would only grind the player into it. */
    private static final double ARRIVAL_DISTANCE = 1.6;

    /** Per-tick sideways shove the player gets from where they are looking. */
    private static final double STEER_ACCELERATION = 0.10;
    /** Ceiling on off-line horizontal speed - high on purpose, this is the "adjust it a lot" knob. */
    private static final double MAX_STEER_SPEED = 0.9;
    /** Bleeds off old steering so re-aiming turns the player promptly instead of fighting stale momentum. */
    private static final double STEER_DECAY = 0.90;

    /** How fast the line comes home once released. */
    private static final double RETRACT_SPEED = 3.0;
    /** Near enough to the hand to be considered stowed. */
    private static final double RETRACT_ARRIVAL = 1.5;

    public GrappleHookEntity(EntityType<? extends GrappleHookEntity> type, Level level) {
        super(type, level);
    }

    public GrappleHookEntity(Level level, Player owner) {
        super(ModEntityTypes.GRAPPLE_HOOK.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STUCK, false);
        this.entityData.define(RETRACTING, false);
    }

    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    public boolean isRetracting() {
        return this.entityData.get(RETRACTING);
    }

    /** Stops the line doing anything else and sends it home; it discards itself on arrival. */
    public void startRetract() {
        if (this.level().isClientSide) return;
        this.entityData.set(STUCK, false);
        this.entityData.set(RETRACTING, true);
    }

    @Override
    protected Item getDefaultItem() {
        return net.minecraft.world.item.Items.STRING; // placeholder icon, unused visually since we custom-render
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (isRetracting()) return; // on the way home it passes through everything
        super.onHitBlock(result);
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (isRetracting()) return;
        super.onHitEntity(result);
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * A ThrowableItemProjectile's swept collision only ever tests the small hitbox at the hook's tip, so
     * a line thrown past the top of a low wall never registers a hit even though the straight rendered
     * chain (owner's eye -> current tip, see GrappleHookRenderer) visibly passes clean through it. Each
     * tick before the hook moves on, raycast along that same rendered line and anchor at the first block
     * it crosses - which is what makes the chain behave like a real line rather than a projectile that
     * can fly over obstacles. Same treatment ColletisVineEntity already gets.
     */
    private void checkChainBlockedByBlock() {
        Player owner = getOwnerPlayer();
        if (owner == null) return;

        Vec3 start = owner.getEyePosition();
        Vec3 end = this.position();
        if (start.distanceToSqr(end) < 0.04) return; // hasn't flown far enough yet for this to matter

        HitResult hit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) return;

        // Anchor AT the contact point, not out where the tip already flew to, or the player would be
        // winched to a spot on the far side of the wall the line is snagged on.
        Vec3 contact = blockHit.getLocation();
        this.setPos(contact.x, contact.y, contact.z);
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        // Before super.tick() moves it any further, and never while retracting - on the way home the
        // hook is explicitly allowed to pass through everything.
        if (!level().isClientSide && !isStuck() && !isRetracting()) {
            checkChainBlockedByBlock();
        }

        super.tick();

        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            if (!this.level().isClientSide) this.discard();
            return;
        }

        if (isRetracting()) {
            if (!this.level().isClientSide) retractStep(owner);
            return;
        }

        boolean stillHolding = owner.isUsingItem() && owner.getUseItem().getItem() instanceof ArackopeshItem;
        if (!stillHolding) {
            startRetract();
            return;
        }

        if (isStuck()) {
            // Run on the server for authority and on the owning client for smooth prediction, the same way
            // vanilla firework boosting drives elytra flight from both sides.
            if (!this.level().isClientSide || owner.isLocalPlayer()) reel(owner);
        } else if (!this.level().isClientSide && hasOutrunTheLoadedWorld(owner)) {
            startRetract();
        }
    }

    /**
     * Winch the player along the line. The velocity is rebuilt from scratch every tick out of exactly two
     * parts - travel along the line, and horizontal steering - which is what keeps gravity out of it: any
     * downward drift vanilla added since the last tick is simply not carried forward.
     */
    private void reel(Player owner) {
        Vec3 toAnchor = this.position().subtract(owner.getEyePosition());
        double distance = toAnchor.length();
        if (distance < 1.0E-4) return;
        Vec3 line = toAnchor.scale(1.0 / distance);

        Vec3 velocity = owner.getDeltaMovement();

        // Travel along the line, easing up to speed. Once the player has arrived the winch targets zero and
        // they simply hang on the line, since nothing here is pulling them down.
        double along = velocity.dot(line);
        double target = distance > ARRIVAL_DISTANCE ? REEL_SPEED : 0.0;
        along = approach(along, target, REEL_ACCELERATION);

        // Everything off the line, flattened to the horizontal plane and pushed toward the crosshair.
        Vec3 offLine = velocity.subtract(line.scale(velocity.dot(line)));
        Vec3 steer = new Vec3(offLine.x, 0.0, offLine.z).scale(STEER_DECAY);

        Vec3 look = owner.getLookAngle();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        if (lookFlat.lengthSqr() > 1.0E-6) {
            steer = steer.add(lookFlat.normalize().scale(STEER_ACCELERATION));
        }
        if (steer.length() > MAX_STEER_SPEED) {
            steer = steer.normalize().scale(MAX_STEER_SPEED);
        }

        owner.setDeltaMovement(line.scale(along).add(steer));
        owner.hurtMarked = true;
        owner.fallDistance = 0;
    }

    private void retractStep(Player owner) {
        Vec3 hand = owner.getEyePosition().add(0, -0.2, 0);
        Vec3 toHand = hand.subtract(this.position());
        if (toHand.length() <= RETRACT_ARRIVAL) {
            this.discard();
            return;
        }
        this.setDeltaMovement(toHand.normalize().scale(RETRACT_SPEED));
    }

    /**
     * The line has no design range - it reaches as far as the player can hold the button. It does still have
     * to come home if it outruns the part of the world the server is simulating, because past that boundary
     * it would stop ticking and simply hang in the air forever.
     */
    private boolean hasOutrunTheLoadedWorld(Player owner) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return false;
        double limit = serverLevel.getServer().getPlayerList().getSimulationDistance() * 16.0;
        return this.distanceToSqr(owner) > limit * limit;
    }

    private static double approach(double current, double target, double step) {
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
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
        return 0F; // flies dead straight out, holds position once anchored, and comes straight back
    }
}
