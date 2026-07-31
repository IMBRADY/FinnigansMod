package net.finnigan.tommemod.event.ColletisEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.ColletisItem;
import net.finnigan.tommemod.particle.ModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Colletis's two non-vine passives:
 *  - Passive 1 (crouch+click, instant): spore-particle burst, Regeneration I to self + nearby players,
 *    and accelerated decay of the caster's own negative status effects. Triggered from ColletisItem.
 *  - Passive 2 (continuous while crouching + holding, near crops): throttled crop growth boost - crops
 *    gain growth progress gradually, not an instant jump to max age.
 * Vanilla has no "shrink remaining duration" API for status effects, so accelerated decay is implemented
 * as remove-then-readd with a shorter duration; this will visibly flicker the HUD icon, an accepted tradeoff.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ColletisPassiveHandler {

    private static final double REGEN_RADIUS = 6.0;
    private static final int REGEN_DURATION_TICKS = 100; // 5s, Regeneration I

    private static final int CROP_RADIUS = 3;
    private static final int CROP_GROWTH_INTERVAL_TICKS = 20; // throttle: at most one roll per crop per second
    private static final float CROP_GROWTH_CHANCE = 0.5F;
    private static final Random RANDOM = new Random();

    public static void performSporeBurst(ServerLevel level, Player player) {
        accelerateDebuffDecay(player);

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION_TICKS, 0, false, true, true));
        for (Player nearby : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(REGEN_RADIUS))) {
            if (nearby == player) continue;
            nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION_TICKS, 0, false, true, true));
        }

        spawnSporeBurstParticles(level, player);
    }

    private static void accelerateDebuffDecay(Player player) {
        List<MobEffectInstance> active = new ArrayList<>(player.getActiveEffects());
        for (MobEffectInstance instance : active) {
            if (instance.getEffect().getCategory() != MobEffectCategory.HARMFUL) continue;

            int newDuration = instance.getDuration() / 2;
            player.removeEffect(instance.getEffect());
            if (newDuration > 0) {
                player.addEffect(new MobEffectInstance(instance.getEffect(), newDuration, instance.getAmplifier(),
                        instance.isAmbient(), instance.isVisible(), instance.showIcon()));
            }
        }
    }

    private static void spawnSporeBurstParticles(ServerLevel level, Player player) {
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        level.sendParticles(ModParticleTypes.COLLETIS_GLOWSPORE.get(), x, y, z, 20, 0.6, 0.6, 0.6, 0.02);
        level.sendParticles(ModParticleTypes.COLLETIS_GLOWSPORE_E.get(), x, y, z, 8, 0.6, 0.6, 0.6, 0.02);

        SimpleParticleType[] leafTypes = {
                ModParticleTypes.COLLETIS_LEAF_1.get(), ModParticleTypes.COLLETIS_LEAF_2.get(),
                ModParticleTypes.COLLETIS_LEAF_3.get(), ModParticleTypes.COLLETIS_LEAF_4.get(),
                ModParticleTypes.COLLETIS_LEAF_5.get()
        };
        for (SimpleParticleType leaf : leafTypes) {
            level.sendParticles(leaf, x, y, z, 2, 0.5, 0.5, 0.5, 0.03);
        }

        SimpleParticleType[] twigTypes = {
                ModParticleTypes.COLLETIS_TWIG_1.get(), ModParticleTypes.COLLETIS_TWIG_2.get(),
                ModParticleTypes.COLLETIS_TWIG_3.get()
        };
        for (SimpleParticleType twig : twigTypes) {
            level.sendParticles(twig, x, y, z, 2, 0.5, 0.5, 0.5, 0.03);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!ColletisItem.isHeldBy(player)) return;
        if (!player.isCrouching()) return;
        if (player.tickCount % CROP_GROWTH_INTERVAL_TICKS != 0) return;

        BlockPos center = player.blockPosition();
        for (int dx = -CROP_RADIUS; dx <= CROP_RADIUS; dx++) {
            for (int dz = -CROP_RADIUS; dz <= CROP_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.getBlock() instanceof CropBlock crop) {
                        int age = crop.getAge(state);
                        int maxAge = crop.getMaxAge();
                        if (age < maxAge && RANDOM.nextFloat() < CROP_GROWTH_CHANCE) {
                            serverLevel.setBlockAndUpdate(pos, crop.getStateForAge(age + 1));
                        }
                    }
                }
            }
        }
    }
}
