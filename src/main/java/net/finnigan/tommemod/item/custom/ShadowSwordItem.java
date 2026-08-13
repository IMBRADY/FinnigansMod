package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.ShadowSwordHelpers.ShadowSoulManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Left-click is an ordinary (if heavy) swing; the sword's real weight is in the souls it collects.
 * Kills and a slow passive drip both feed the count, six is the ceiling, and a right-click spends
 * the lot as a staggered volley of exploding souls.
 *
 * The souls themselves live in {@link ShadowSoulManager}, not on this stack - they're tied to
 * holding the sword, not to owning it.
 */
public class ShadowSwordItem extends SwordItem {

    public ShadowSwordItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && !ShadowSoulManager.throwSouls(player)) {
            player.displayClientMessage(
                    Component.literal("No souls to throw.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Gathers a Soul on every kill, and one more every 3 seconds")
                .withStyle(style -> style.withColor(0x4FC3C7)));
        tooltip.add(Component.literal("Holds up to " + ShadowSoulManager.MAX_SOULS + " Souls, granting Resistance while full")
                .withStyle(style -> style.withColor(0x4FC3C7)));
        tooltip.add(Component.literal("Right-click to hurl every Soul forward")
                .withStyle(style -> style.withColor(0x4FC3C7)));
        tooltip.add(Component.literal("Thrown at full, the volley lands twice as fast and a kill returns every Soul")
                .withStyle(style -> style.withColor(0x8E6FD8)));
    }
}
