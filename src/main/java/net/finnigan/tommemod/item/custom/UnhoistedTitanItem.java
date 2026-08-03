package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.UnhoistedTitanHelpers.AnchorEntity;
import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.finnigan.tommemod.particle.ModParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unhoisted Titan: left click deals the base sword damage (25, handled automatically by vanilla
 * SwordItem). Right click throws a chained anchor that damages what it hits for 10 and reels the
 * survivor back in (see AnchorEntity). Crouch + right click instead sets off a water blast in a
 * radius, dealing 20 and launching everything caught in it into the air.
 * Passive: full knockback resistance, bonus armor scaled to how many enemies are crowding the
 * wielder, and a permanent swim speed boost (see UnhoistedTitanPassiveHandler).
 */
public class UnhoistedTitanItem extends SwordItem {

    private static final Map<UUID, Integer> ACTIVE_ANCHORS = new HashMap<>();

    private static final int ANCHOR_COOLDOWN_TICKS = 10;  // 0.5s
    private static final int BLAST_COOLDOWN_TICKS = 120;  // 6s

    private static final float BLAST_DAMAGE = 20.0F;
    private static final double BLAST_LAUNCH_STRENGTH = 1.1;
    private static final double BLAST_VERTICAL_HALF_HEIGHT = 3.0;

    public UnhoistedTitanItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof UnhoistedTitanItem
                || player.getOffhandItem().getItem() instanceof UnhoistedTitanItem;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            return performWaterBlast(context.getLevel(), player, context.getHand()).getResult();
        }
        return throwAnchor(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return performWaterBlast(level, player, hand);
        }
        return throwAnchor(level, player, hand);
    }

    private InteractionResultHolder<ItemStack> throwAnchor(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this) || ACTIVE_ANCHORS.containsKey(player.getUUID())) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            AnchorEntity anchor = new AnchorEntity(level, player);
            Vec3 look = player.getLookAngle();
            anchor.setPos(player.getX() + look.x, player.getEyeY() - 0.2, player.getZ() + look.z);
            anchor.shoot(look.x, look.y, look.z, 2.2F, 0.5F);
            level.addFreshEntity(anchor);
            ACTIVE_ANCHORS.put(player.getUUID(), anchor.getId());

            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, ANCHOR_COOLDOWN_TICKS));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
        }

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void clearAnchorFor(UUID ownerUUID) {
        ACTIVE_ANCHORS.remove(ownerUUID);
    }

    private InteractionResultHolder<ItemStack> performWaterBlast(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        double radius = ModConfig.TITAN_BLAST_RADIUS_BLOCKS.get();

        AABB box = new AABB(player.getX() - radius, player.getY() - BLAST_VERTICAL_HALF_HEIGHT, player.getZ() - radius,
                player.getX() + radius, player.getY() + BLAST_VERTICAL_HALF_HEIGHT, player.getZ() + radius);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                FireKatanaItem::isValidFireTarget);

        for (LivingEntity target : targets) {
            if (target.distanceTo(player) > radius) continue;
            target.hurt(player.damageSources().playerAttack(player), BLAST_DAMAGE);

            Vec3 velocity = target.getDeltaMovement();
            target.setDeltaMovement(velocity.x, BLAST_LAUNCH_STRENGTH, velocity.z);
            target.hurtMarked = true;
        }

        spawnBlastParticles(serverLevel, player, radius);

        player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, BLAST_COOLDOWN_TICKS));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.4F, 0.7F);

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Fills the blast area with the same wave/foam sprites Aquatana already uses for water. */
    private void spawnBlastParticles(ServerLevel level, Player player, double radius) {
        int points = (int) Math.round(radius * 14);
        for (int i = 0; i < points; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * radius; // uniform over the disc
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;

            SimpleParticleType particle = (i % 2 == 0)
                    ? ModParticleTypes.WAVE_1.get()
                    : ModParticleTypes.FOAM_1.get();

            level.sendParticles(particle, x, player.getY() + 0.2, z, 1, 0.1, 0.2, 0.1, 0.02);
        }
    }
}
