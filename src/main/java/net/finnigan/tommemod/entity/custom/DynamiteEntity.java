package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class DynamiteEntity extends ThrowableItemProjectile {

    public DynamiteEntity(EntityType<? extends DynamiteEntity> type, Level level) {
        super(type, level);
    }

    public DynamiteEntity(Level level, LivingEntity thrower) {
        super(ModEntityTypes.DYNAMITE.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DYNAMITE.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide()) {

            this.level().explode(
                    this,              // source entity
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    1.0F,              // blast radius
                    Level.ExplosionInteraction.TNT
            );

            this.discard();
        }
    }
}