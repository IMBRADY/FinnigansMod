package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public class BeeNadeEntity extends ThrowableItemProjectile {

    public BeeNadeEntity(EntityType<? extends BeeNadeEntity> type, Level level) {
        super(type, level);
    }

    public BeeNadeEntity(Level level, LivingEntity thrower) {
        super(ModEntityTypes.BEE_NADE.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BEE_NADE.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide()) {
            AABB aoe = this.getBoundingBox().inflate(3.0);
            for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, aoe,
                    e -> e != this.getOwner() && e.isAlive())) {
                target.hurt(this.damageSources().generic(), 2.0F);
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            }

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        this.getX(), this.getY(), this.getZ(), 10, 0.4, 0.3, 0.4, 0.01);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BEE_STING, SoundSource.NEUTRAL, 1.0F, 1.0F);

            this.discard();
        }
    }
}
