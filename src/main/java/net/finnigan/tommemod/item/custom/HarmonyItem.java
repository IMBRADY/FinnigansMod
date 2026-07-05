package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.MusicNoteEntity;
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            spawnMusicNotes(level, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        player.getCooldowns().addCooldown(this, 60); // Cooldown

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void spawnMusicNotes(Level level, Player player) {


        if (!level.isClientSide) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        player.getX(), player.getEyeY(), player.getZ(),
                        20,          // particle count
                        1.5, 1.0, 1.5, // spread (x/y/z)
                        0.05);       // speed
            }
                for (int i = 0; i < 4; i++) {

                    MusicNoteEntity note = new MusicNoteEntity(level, player);

                    Vec3 look = player.getLookAngle();

                    // Spread
                    Vec3 velocity = look.add(
                            (level.random.nextDouble() - 0.5) * 5,
                            (level.random.nextDouble() - 0.5) * 5,
                            (level.random.nextDouble() - 0.5) * 5
                    ).normalize().scale(0.8);

                    note.setPos(player.getEyePosition());
                    note.setDeltaMovement(velocity);

                    level.addFreshEntity(note);
                }
            }
        }
}