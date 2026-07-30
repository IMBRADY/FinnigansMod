package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.RivalryFlagManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

// Creative-mode toy. Right-click a mob to add it to Team 1, left-click a mob to add it to Team 2
// (without damaging it), and shift+right-click to make the two teams fight each other and clear both.
public class RivalryFlagItem extends Item {

    public RivalryFlagItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.CONSUME;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;

        if (player.isShiftKeyDown()) {
            RivalryFlagManager.triggerRivalry(serverPlayer, stack);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.2F);
            return InteractionResult.CONSUME;
        }

        RivalryFlagManager.addToTeam1(stack, target);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.5F);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RivalryFlagManager.triggerRivalry(serverPlayer, stack);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.2F);
            } else if (target instanceof LivingEntity living) {
                RivalryFlagManager.addToTeam2(stack, living);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 0.7F);
            }
        }
        return true; // always cancel the normal attack - left-clicking with the flag must never damage the target
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            RivalryFlagManager.triggerRivalry(serverPlayer, stack);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        addTeamLore(tooltip, "Team 1:", RivalryFlagManager.getTeam1(stack), ChatFormatting.BLUE);
        addTeamLore(tooltip, "Team 2:", RivalryFlagManager.getTeam2(stack), ChatFormatting.RED);
    }

    private static void addTeamLore(List<Component> tooltip, String header, ListTag entries, ChatFormatting color) {
        if (entries.isEmpty()) return;
        tooltip.add(Component.literal(header).withStyle(color, ChatFormatting.BOLD));
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            tooltip.add(Component.literal(" - " + RivalryFlagManager.entryName(entry)).withStyle(color));
        }
    }
}
