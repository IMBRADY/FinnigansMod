package net.finnigan.tommemod.entity.custom.LumapierHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Lumapier's individual light-bolt projectile: simple flat-damage-on-hit thrown projectile, same
 * template as DaggerEntity - fast, no gravity, single-target, discards on any impact.
 */
public class LightBoltProjectileEntity extends ThrowableItemProjectile {

    private static final float DAMAGE = 8.0F;

    public LightBoltProjectileEntity(EntityType<? extends LightBoltProjectileEntity> type, Level level) {
        super(type, level);
    }

    public LightBoltProjectileEntity(Level level, LivingEntity owner) {
        super(ModEntityTypes.LIGHT_BOLT_PROJECTILE.get(), owner, level);
        this.setItem(new ItemStack(ModItems.LUMAPIER.get()));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.LUMAPIER.get();
    }

    @Override
    protected float getGravity() {
        return 0.0F; // light bolts fly dead straight, unlike an arcing dagger throw
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) return;

        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().thrown(this, owner != null ? owner : this);
        target.hurt(source, DAMAGE);

        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) return;

        this.discard();
    }
}
