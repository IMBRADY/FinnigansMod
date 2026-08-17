package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.effect.ModMobEffects;
import net.finnigan.tommemod.skill.bonus.HeldItemStats;
import net.finnigan.tommemod.skill.bonus.SkillBonusSources;
import net.finnigan.tommemod.skill.effect.ValueFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Weapon and tool tooltips that account for what the trees have done to them.
 *
 * The complaint this answers is a real one: a sword says 7 damage, the player has bought four nodes
 * that raise melee damage, and nothing anywhere tells them what the swing is now worth. Vanilla's
 * number is the item's own, because vanilla's number comes from attributes and none of these bonuses
 * are attributes - they are multipliers applied at the instant of the hit.
 *
 * So the printed line is rewritten with the finished figure, and the breakdown behind it is put a
 * CTRL press away rather than in everyone's face: the summary is for the player deciding which sword
 * to carry, the detail is for the one deciding which node to buy next.
 *
 * Client-side only and entirely a read: both the skill handler and the tree definitions are synced,
 * so every number here is worked out from the same data the server will use when the blow lands.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ItemStatTooltips {

    /** Matches vanilla's own attribute formatting, so a rewritten line looks untouched. */
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#.##");

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return;

        ItemStack stack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();

        boolean anyDetail = appendAttackDamage(player, stack, tooltip);
        anyDetail |= appendMiningSpeed(player, stack, tooltip);

        if (anyDetail && !Screen.hasControlDown()) {
            tooltip.add(Component.literal("Hold CTRL for details")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    // ---- Damage ----

    /** @return whether this item has anything worth a detail panel. */
    private static boolean appendAttackDamage(Player player, ItemStack stack, List<Component> tooltip) {
        double base = HeldItemStats.baseAttackDamage(player, stack);
        if (base <= 0.0) return false;

        // Only ever correct a figure the player can already see. An item that grants attack damage
        // without vanilla printing the usual line has nothing here to rewrite, and inventing a second
        // damage number for it would be worse than leaving it alone.
        int damageLine = indexOfAttackDamageLine(tooltip);
        if (damageLine < 0) return false;

        List<SkillBonusSources.Source> counted =
                SkillBonusSources.of(player, HeldItemStats.countedAttackKeys(stack));
        List<SkillBonusSources.Source> situational =
                SkillBonusSources.of(player, HeldItemStats.situationalAttackKeys(stack));

        double bonus = counted.stream().mapToDouble(SkillBonusSources.Source::amount).sum();
        boolean wellFed = player.hasEffect(ModMobEffects.WELL_FED.get());

        // Well Fed is a multiplier of its own rather than a share of the sum, exactly as the handler
        // applies it - folded in the same order here so the two agree to the decimal.
        double total = base * (1.0 + bonus);
        if (wellFed) total *= 1.0 + HeldItemStats.WELL_FED_BONUS;

        if (counted.isEmpty() && situational.isEmpty() && !wellFed) return false;

        rewriteAttackDamageLine(tooltip, damageLine, total);

        if (!Screen.hasControlDown()) return true;

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Attack damage").withStyle(ChatFormatting.GOLD));
        tooltip.add(line(DAMAGE_FORMAT.format(base) + " base", ChatFormatting.GRAY));

        for (SkillBonusSources.Source source : counted) {
            tooltip.add(sourceLine(source, ChatFormatting.GREEN));
        }
        if (wellFed) {
            tooltip.add(line(ValueFormat.PERCENT.format(HeldItemStats.WELL_FED_BONUS)
                    + "  Well Fed", ChatFormatting.GREEN));
        }
        tooltip.add(line(DAMAGE_FORMAT.format(total) + " per hit", ChatFormatting.WHITE));

        appendSituational(tooltip, situational);
        return true;
    }

    /**
     * Swaps the number in vanilla's "N Attack Damage" line for the finished one.
     *
     * Rewritten in place rather than added underneath, because two damage figures on one tooltip is
     * worse than either alone - the player has to work out which of them is the real one.
     */
    private static void rewriteAttackDamageLine(List<Component> tooltip, int index, double total) {
        tooltip.set(index, Component.literal(" ").append(Component.translatable(
                        "attribute.modifier.equals.0",
                        DAMAGE_FORMAT.format(total),
                        Component.translatable(Attributes.ATTACK_DAMAGE.getDescriptionId())))
                .withStyle(ChatFormatting.DARK_GREEN));
    }

    /** Where vanilla's own attack damage line sits, or -1 when it wrote none. */
    private static int indexOfAttackDamageLine(List<Component> tooltip) {
        for (int i = 0; i < tooltip.size(); i++) {
            if (isAttackDamageLine(tooltip.get(i))) return i;
        }
        return -1;
    }

    /**
     * Whether a tooltip line is the one vanilla writes for a weapon's own attack damage.
     *
     * Recognised by its shape rather than by its position: the "equals" form of an attribute line
     * naming attack damage. Position is not dependable - enchantments, other attributes and any other
     * mod's additions all move it - and the text is translated, so matching on words would break in
     * every language but English.
     */
    private static boolean isAttackDamageLine(Component line) {
        if (line.getContents() instanceof TranslatableContents contents
                && contents.getKey().startsWith("attribute.modifier.equals.")) {
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component named
                        && named.getContents() instanceof TranslatableContents inner
                        && inner.getKey().equals(Attributes.ATTACK_DAMAGE.getDescriptionId())) {
                    return true;
                }
            }
        }
        for (Component sibling : line.getSiblings()) {
            if (isAttackDamageLine(sibling)) return true;
        }
        return false;
    }

    // ---- Mining ----

    private static boolean appendMiningSpeed(Player player, ItemStack stack, List<Component> tooltip) {
        double base = HeldItemStats.baseMiningSpeed(stack);
        if (base <= 0.0) return false;

        List<SkillBonusSources.Source> counted =
                SkillBonusSources.of(player, HeldItemStats.countedMiningKeys());
        List<SkillBonusSources.Source> situational =
                SkillBonusSources.of(player, HeldItemStats.situationalMiningKeys());
        if (counted.isEmpty() && situational.isEmpty()) return false;

        double bonus = counted.stream().mapToDouble(SkillBonusSources.Source::amount).sum();
        double total = base * (1.0 + bonus);

        // Vanilla prints no mining speed at all, so this is an addition rather than a rewrite, and it
        // goes where the attribute block goes: one indented dark green line.
        tooltip.add(line(DAMAGE_FORMAT.format(total) + " Mining Speed", ChatFormatting.DARK_GREEN));

        if (!Screen.hasControlDown()) return true;

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Mining speed").withStyle(ChatFormatting.GOLD));
        tooltip.add(line(DAMAGE_FORMAT.format(base) + " base", ChatFormatting.GRAY));
        for (SkillBonusSources.Source source : counted) {
            tooltip.add(sourceLine(source, ChatFormatting.GREEN));
        }
        tooltip.add(line(DAMAGE_FORMAT.format(total) + " per block", ChatFormatting.WHITE));

        appendSituational(tooltip, situational);
        return true;
    }

    // ---- Shared ----

    private static void appendSituational(List<Component> tooltip, List<SkillBonusSources.Source> sources) {
        if (sources.isEmpty()) return;

        tooltip.add(Component.literal("Situational").withStyle(ChatFormatting.DARK_GRAY));
        for (SkillBonusSources.Source source : sources) {
            tooltip.add(sourceLine(source, ChatFormatting.DARK_GRAY));
        }
    }

    /** "+15%  Melee - Honed Edge", with the node's own wording from the tree file after it. */
    private static Component sourceLine(SkillBonusSources.Source source, ChatFormatting colour) {
        MutableComponent text = Component.literal(" " + ValueFormat.PERCENT.format(source.amount())
                        + "  " + source.skill() + " - " + source.node()
                        + (source.rank() > 1 ? " " + source.rank() : ""))
                .withStyle(colour);
        return text.append(Component.literal("  ").append(source.description())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component line(String text, ChatFormatting colour) {
        return Component.literal(" " + text).withStyle(colour);
    }
}
