package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.finnigan.tommemod.item.custom.FireKatanaHelpers.FireZoneManager;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.finnigan.tommemod.particle.ModParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class FireKatanaItem extends SwordItem {

    private static final double RING_RADIUS = 5;
    private static final float INITIAL_DAMAGE = 40.0F;
    private static final int BURN_SECONDS = 6;
    private static final int COOLDOWN_TICKS = 100; // 5 seconds

    @SuppressWarnings("unchecked")
    private static final RegistryObjectHolder[] RING_PARTICLES = {
            new RegistryObjectHolder(ModParticleTypes.FIRE_LARGE_1),
            new RegistryObjectHolder(ModParticleTypes.FIRE_LARGE_2),
            new RegistryObjectHolder(ModParticleTypes.FIRE_SMALL_1),
            new RegistryObjectHolder(ModParticleTypes.FIRE_SMALL_2)
    };

    // small internal wrapper so we can hold a list of RegistryObject<SimpleParticleType> generically
    private record RegistryObjectHolder(net.minecraftforge.registries.RegistryObject<SimpleParticleType> ref) {}

    public FireKatanaItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    public static boolean isValidFireTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof Player) return false;
        if (entity instanceof Wolf) return false;
        if (entity instanceof Villager) return false;
        if (entity.getTags().contains(SoulSummoner.SOUL_ALLY_TAG)) return false;
        return true;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            performFireRing((ServerLevel) level, player);
            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
        }

        return InteractionResultHolder.success(stack);
    }

    private void performFireRing(ServerLevel level, Player player) {
        AABB box = new AABB(player.getX() - RING_RADIUS, player.getY() - 2, player.getZ() - RING_RADIUS,
                player.getX() + RING_RADIUS, player.getY() + 2, player.getZ() + RING_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, FireKatanaItem::isValidFireTarget);

        for (LivingEntity target : targets) {
            if (target.distanceTo(player) > RING_RADIUS) continue;
            DamageSource source = player.damageSources().playerAttack(player);
            target.hurt(source, INITIAL_DAMAGE);
            target.setSecondsOnFire(BURN_SECONDS);
        }

        // Register a lingering zone: fills with particles + doubles burn damage for its duration
        FireZoneManager.addZone(level, player.getX(), player.getY(), player.getZ(), RING_RADIUS, player);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}