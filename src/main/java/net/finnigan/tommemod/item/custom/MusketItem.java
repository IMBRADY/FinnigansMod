package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class MusketItem extends Item {

    public MusketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

        player.swinging = false;
        player.swingTime = 0;

        ItemStack stack = player.getItemInHand(hand);
        Item bulletItem = ModItems.BULLET.get();

        if (!player.getInventory().contains(new ItemStack(bulletItem))) {
            player.playSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.0F, 1.0F);
            return InteractionResultHolder.consume(stack); // no ammo, don't shoot
        }

        if (!level.isClientSide()) {
            shootHitscan(level, player);
        }

        player.getCooldowns().addCooldown(this, 25); // reload speed

        return InteractionResultHolder.consume(stack);
    }

    private void shootHitscan(Level level, Player player) {

        Item bulletItem = ModItems.BULLET.get();
        double range = 50.0;

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(range));

        ClipContext context = new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );

        BlockHitResult blockHit = level.clip(context);

        Vec3 hitPos = blockHit.getLocation();

        AABB box = new AABB(start, hitPos).inflate(1.0);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level,
                player,
                start,
                hitPos,
                box,
                e -> e instanceof LivingEntity && e != player
        );

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS,
                3.0F,
                0.3F
        );

        // Recoil
        player.setDeltaMovement(
                player.getDeltaMovement().add(
                        -look.x * 0.25,
                        0.08,
                        -look.z * 0.25
                )
        );
        player.hurtMarked = true;

        // Particle Effect
        Vec3 smokePos = start.add(look.scale(0.8));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    smokePos.x, smokePos.y, smokePos.z,
                    8,
                    0.02, 0.02, 0.02,
                    0.01
            );
        }

        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            target.hurt(level.damageSources().playerAttack(player), 6.0F);
        }

        // Consume Bullet
        player.getInventory().clearOrCountMatchingItems(
                itemStack -> itemStack.is(bulletItem),
                1,
                player.inventoryMenu.getCraftSlots()
        );
    }
}