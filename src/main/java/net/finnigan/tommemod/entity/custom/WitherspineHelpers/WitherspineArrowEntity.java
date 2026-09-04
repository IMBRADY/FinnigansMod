package net.finnigan.tommemod.entity.custom.WitherspineHelpers;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class WitherspineArrowEntity extends AbstractArrow {
    private static final double TRACKING_RANGE = 15.0D;
    private static final double HOMING_STRENGTH = 0.20D;

    public WitherspineArrowEntity(EntityType<? extends WitherspineArrowEntity> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
    }

    public WitherspineArrowEntity(Level level, LivingEntity shooter) {
        super(ModEntityTypes.WITHERSPINE_ARROW.get(), shooter, level);
        pickup = Pickup.DISALLOWED;
        setBaseDamage(6.0D);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !inGround) {
            LivingEntity target = findNearestTarget();
            if (target != null) {
                Vec3 desired = target.getEyePosition().subtract(position()).normalize();
                double speed = Math.max(1.25D, getDeltaMovement().length());
                Vec3 steered = getDeltaMovement().normalize().scale(1.0D - HOMING_STRENGTH)
                        .add(desired.scale(HOMING_STRENGTH)).normalize().scale(speed);
                setDeltaMovement(steered);
            }
        }
        super.tick();
    }

    private LivingEntity findNearestTarget() {
        Entity owner = getOwner();
        return level().getEntitiesOfClass(LivingEntity.class,
                        (owner != null ? owner.getBoundingBox() : getBoundingBox()).inflate(TRACKING_RANGE),
                        entity -> entity.isAlive() && entity != owner && !entity.isSpectator())
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}
