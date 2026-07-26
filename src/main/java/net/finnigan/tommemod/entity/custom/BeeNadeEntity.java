package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.util.Comparator;
import java.util.List;

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
            List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(6.0), e -> e != this.getOwner() && e.isAlive());
            LivingEntity target = nearby.stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(this)))
                    .orElse(null);

            int count = 2 + this.random.nextInt(2);
            for (int i = 0; i < count; i++) {
                Bee bee = EntityType.BEE.create(this.level());
                if (bee == null) continue;

                bee.moveTo(this.getX(), this.getY(), this.getZ(), this.random.nextFloat() * 360.0F, 0.0F);
                if (target != null) {
                    bee.setPersistentAngerTarget(target.getUUID());
                    bee.startPersistentAngerTimer();
                    bee.setTarget(target);
                }
                this.level().addFreshEntity(bee);
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
