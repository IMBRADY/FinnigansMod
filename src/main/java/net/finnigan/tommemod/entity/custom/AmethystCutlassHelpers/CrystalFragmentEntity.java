package net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class CrystalFragmentEntity extends ThrowableItemProjectile {

    private float damage = 1.0F;

    public CrystalFragmentEntity(EntityType<? extends CrystalFragmentEntity> type, Level level) {
        super(type, level);
    }

    public CrystalFragmentEntity(Level level, LivingEntity owner) {
        super(ModEntityTypes.CRYSTAL_FRAGMENT.get(), owner, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            if (result.getEntity() instanceof LivingEntity living) {
                living.hurt(this.damageSources().thrown(this, this.getOwner()), damage);
            }
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }
}