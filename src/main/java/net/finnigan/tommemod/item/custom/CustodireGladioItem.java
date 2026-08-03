package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.item.custom.CustodireGladioHelpers.ChiefTierResolver;
import net.finnigan.tommemod.item.custom.CustodireGladioHelpers.ShieldWallManager;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Custodire Gladio: left click deals the base sword damage (25, handled automatically by vanilla
 * SwordItem). Right click raises a 3-block-wide shield wall in front of the wielder that eats
 * projectiles from either side and sustains anyone sheltering behind it (see ShieldWallManager).
 * Passive: max health, melee damage, and the wall's own numbers all scale with the wielder's
 * reputation in the village they are Chief of - +20% per tier above Novice (see ChiefTierResolver
 * and CustodireGladioPassiveHandler).
 */
public class CustodireGladioItem extends SwordItem {

    private static final int COOLDOWN_TICKS = 240; // 12 seconds

    /**
     * Where CustodireGladioPassiveHandler parks the wielder's current Chief tier for the tooltip to
     * read. The tier itself can only be worked out server-side (it needs the Chief registry and the
     * player's reputation capability), so it is stamped onto the stack rather than recomputed on the
     * client - the same trick LumapierItem uses for its firing state.
     */
    private static final String TAG_CHIEF_TIER = "ChiefTier";

    public CustodireGladioItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    /** Records the wielder's Chief tier on the stack so its tooltip can show the buff it's granting. */
    public static void stampChiefTier(ItemStack stack, ReputationTier tier) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.getInt(TAG_CHIEF_TIER) == tier.ordinal()) return; // don't dirty the stack every tick
        tag.putInt(TAG_CHIEF_TIER, tier.ordinal());
    }

    private static ReputationTier stampedChiefTier(ItemStack stack) {
        if (!stack.hasTag()) return ReputationTier.NOVICE;
        int ordinal = stack.getOrCreateTag().getInt(TAG_CHIEF_TIER);
        ReputationTier[] tiers = ReputationTier.values();
        return ordinal >= 0 && ordinal < tiers.length ? tiers[ordinal] : ReputationTier.NOVICE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        ReputationTier tier = stampedChiefTier(stack);
        int percent = (int) Math.round(ChiefTierResolver.BONUS_PER_TIER * tier.ordinal() * 100);

        tooltip.add(Component.literal("Chief of a ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(titleCase(tier.name())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" village").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("+" + percent + "% ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("max health, sword damage, shield strength")
                        .withStyle(ChatFormatting.GRAY)));
    }

    private static String titleCase(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof CustodireGladioItem
                || player.getOffhandItem().getItem() instanceof CustodireGladioItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            ShieldWallManager.deploy((ServerLevel) level, player);
            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
        }

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
