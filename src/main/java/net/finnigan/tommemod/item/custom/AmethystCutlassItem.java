package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers.AmethystBeamEntity;
import net.finnigan.tommemod.particle.ModParticleTypes;
import net.finnigan.tommemod.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class AmethystCutlassItem extends SwordItem {

    private static final int BEAM_ACTIVE_TICKS = 60;   // 3 second total duration
    private static final int BEAM_TICK_RATE = 10;      // damage tick every 0.5s
    private static final double BEAM_RANGE = 20.0D;
    private static final float BEAM_DAMAGE = 4.0F;
    private static final int SOUND_LENGTH_TICKS = 50;

    public AmethystCutlassItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // same pattern as bow/shield — effectively "hold indefinitely"
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        System.out.println("[tommemod] AmethystCutlassItem.use() called, client=" + level.isClientSide);
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            AmethystBeamEntity beam = new AmethystBeamEntity(ModEntityTypes.AMETHYST_BEAM.get(), level);
            beam.setOwner(player);
            beam.setPos(player.getEyePosition(1.0F).x, player.getEyePosition(1.0F).y, player.getEyePosition(1.0F).z);
            level.addFreshEntity(beam);
            System.out.println("[tommemod] beam spawned: " + beam.getUUID());
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    private void fireBeam(Level level, LivingEntity user) {
        if (level.isClientSide) return;

        Vec3 start = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        Vec3 end = start.add(look.scale(BEAM_RANGE));

        List<Entity> candidates = level.getEntities(user, user.getBoundingBox().expandTowards(look.scale(BEAM_RANGE)).inflate(1.0D));
        Entity hitEntity = null;
        double closestDistSq = BEAM_RANGE * BEAM_RANGE;

        for (Entity candidate : candidates) {
            if (!candidate.isPickable() || candidate == user) continue;
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (hit.isPresent()) {
                double distSq = start.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    hitEntity = candidate;
                }
            }
        }

        if (hitEntity instanceof LivingEntity target) {
            target.hurt(user.damageSources().mobAttack(user), BEAM_DAMAGE);
        }
    }
}