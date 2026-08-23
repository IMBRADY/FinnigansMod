package net.finnigan.tommemod.entity.custom.EndDrayk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;

/**
 * One collision box of the drayk's body. Parts are never network-spawned and hold no state of their own -
 * every position is derived from the parent's trail, so both sides compute them identically.
 */
public class EndDraykPart extends PartEntity<EndDraykEntity> {
    private final DraykSegment segment;
    private final EntityDimensions size;

    public EndDraykPart(EndDraykEntity parent, DraykSegment segment) {
        super(parent);
        this.segment = segment;
        this.size = EntityDimensions.scalable(segment.width(), segment.height());
        this.refreshDimensions();
    }

    public DraykSegment segment() { return this.segment; }

    @Override public EntityDimensions getDimensions(Pose pose) { return this.size; }

    /** Still hittable: arrows and swings land on the segment they touch, and route to the parent. */
    @Override public boolean isPickable() { return true; }

    /**
     * Deliberately <em>not</em> solid. Returning true here would feed {@code Level#getEntities}, which
     * {@code getEntityCollisions} builds collision shapes from, and the body would become something a
     * player can be blocked by and stand on. That was tried and dropped: a flying snake that can pin you
     * against terrain or park a platform mid-air is more nuisance than threat. The drayk pushes players
     * around by hitting them instead - see the attack knockback on {@link EndDraykEntity}.
     */
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }

    /** So targeting and "is this me?" checks treat a part as the drayk. */
    @Override public boolean is(Entity other) { return this == other || this.getParent() == other; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.isInvulnerableTo(source) && this.getParent().hurtPart(this, source, amount);
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
