package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.MusicNoteEntity;
import net.finnigan.tommemod.entity.custom.NoteSpawnScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HarmonyItem extends SwordItem {

    public HarmonyItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

        ItemStack stack = player.getItemInHand(hand);

        // bail out early if on cooldown, BEFORE spawning anything
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        // ignore off-hand call to prevent double-trigger
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            spawnMusicNotes(level, player);
        }

        player.getCooldowns().addCooldown(this, 60);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void spawnMusicNotes(Level level, Player player) {

        if (!level.isClientSide) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        player.getX(), player.getEyeY(), player.getZ(),
                        20,
                        1.5, 1.0, 1.5,
                        0.05);
            }

            Vec3 look = player.getLookAngle();
            int delayBetween = 4; // n ticks between each spawn (4 ticks = 0.2s)

            for (int i = 0; i < 4; i++) {
                NoteSpawnScheduler.schedule(level, player, look, i * delayBetween);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }
}