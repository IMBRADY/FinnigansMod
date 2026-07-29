package net.finnigan.tommemod.entity.custom;

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

// Thrown daggers arc under gravity like arrows and deal damage on impact. The dagger you threw stays
// in your hand (it just loses durability, like a melee hit) - the flying copy is a visual/damage
// projectile only, so it does not leave anything behind on landing (that would be a free duplicate).
public class DaggerEntity extends ThrowableItemProjectile {

    private float damage = 3.0F;

    public DaggerEntity(EntityType<? extends DaggerEntity> type, Level level) {
        super(type, level);
    }

    public DaggerEntity(Level level, LivingEntity owner, ItemStack daggerStack, float damage) {
        super(ModEntityTypes.DAGGER.get(), owner, level);
        this.setItem(daggerStack);
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.IRON_DAGGER.get(); // fallback only; the thrown stack is always set explicitly
    }

    @Override
    protected float getGravity() {
        return 0.05F; // matches AbstractArrow's fall-off, per the "arrow properties" request
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) return;

        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().thrown(this, owner != null ? owner : this);
        target.hurt(source, this.damage);

        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) return;

        this.discard();
    }
}
