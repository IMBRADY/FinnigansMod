package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

public class MusicNoteEntity extends ThrowableItemProjectile {

    private LivingEntity target;

    public MusicNoteEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public MusicNoteEntity(Level level, Player shooter) {
        this(ModEntityTypes.MUSIC_NOTE.get(), level);
        this.setOwner(shooter);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.MUSIC_NOTE_ITEM.get();
    }

    @Override
    public void tick() {
        super.tick();

        if (target == null || !target.isAlive()) {
            target = findNearestEnemy();
        }

        if (target != null) {
            Vec3 dir = target.position().add(0, target.getBbHeight() / 2, 0)
                    .subtract(this.position())
                    .normalize();
            setDeltaMovement(dir.scale(0.25)); // speed
        }

        move(MoverType.SELF, getDeltaMovement());

        for (Entity e : level().getEntities(this, getBoundingBox().inflate(0.2))) {
            if (e instanceof LivingEntity living && e != getOwner()) {
                living.hurt(level().damageSources().magic(), 4.0F);
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
                discard();
                break;
            }
        }

        if (tickCount > 60) discard();
    }

    private LivingEntity findNearestEnemy() {
        double range = 16.0;
        AABB box = this.getBoundingBox().inflate(range);
        List<LivingEntity> entities = level().getEntitiesOfClass(
                LivingEntity.class, box, e -> e != getOwner()
        );

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity e : entities) {
            double dist = this.distanceToSqr(e);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }
}