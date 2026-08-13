package net.finnigan.tommemod.entity.custom.BallistaHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.BallistaEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The Ballista's bolt.
 *
 * A plain vanilla Arrow could not do this job. Two of its behaviours are actively wrong here:
 *
 * <ul>
 * <li>When a hit is refused - most often because the target is still inside the invulnerability
 *     window from the previous bolt - vanilla reverses the arrow and sends it back the way it came.
 *     A Ballista reloads in eighteen ticks against a twenty-tick window, so that happened constantly,
 *     and with gravity switched off the bolt flew all the way back and shot the Ballista itself.
 *     Here a refused hit simply ends the bolt.</li>
 * <li>Vanilla scales damage by current speed, so a bolt this fast would hit for many times its
 *     rating. Here the damage is flat, and is what it says it is at any range.</li>
 * </ul>
 *
 * It also declines to strike anyone the Ballista is protecting, passing through them rather than
 * stopping - see {@link BallistaEntity#isProtectedFrom}.
 */
public class BallistaBoltEntity extends AbstractArrow {

    private float damage = 8.0F;

    public BallistaBoltEntity(EntityType<? extends BallistaBoltEntity> type, Level level) {
        super(type, level);
    }

    public BallistaBoltEntity(Level level, LivingEntity shooter) {
        super(ModEntityTypes.BALLISTA_BOLT.get(), shooter, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        // Never the weapon that fired it, at any point in the bolt's life - vanilla only guards this
        // until the projectile has cleared its owner, which a bounced bolt has already done.
        if (target == this.getOwner()) return false;
        if (this.getOwner() instanceof BallistaEntity ballista && ballista.isProtectedFrom(target)) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity shooter = this.getOwner();

        boolean hurt = target.hurt(this.damageSources().arrow(this, shooter != null ? shooter : this), damage);
        if (hurt && target instanceof LivingEntity living && shooter instanceof LivingEntity livingShooter) {
            this.doEnchantDamageEffects(livingShooter, living);
        }

        // Spent either way. Vanilla's alternative - reversing course to look for another victim - is
        // the one thing a bolt from a fixed emplacement must never do.
        this.playSound(this.getDefaultHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.discard();
    }
}
