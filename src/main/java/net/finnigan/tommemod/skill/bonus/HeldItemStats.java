package net.finnigan.tommemod.skill.bonus;

import net.finnigan.tommemod.item.custom.MusketItem;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * What a weapon or tool is actually worth in this player's hands.
 *
 * The numbers a vanilla tooltip prints are the item's alone - the mod then multiplies them at the
 * moment of use, from a dozen handlers the player never sees. This works out the same figures those
 * handlers will, so a tooltip can show the damage that will really land rather than the one printed
 * on the item.
 *
 * Split into "counted" and "situational" deliberately. A counted bonus applies to every swing and
 * belongs in the headline number; a situational one depends on the target or the moment - what it is,
 * whether it noticed you, how hurt it is - and can only honestly be listed, not added in. Keeping the
 * two apart is what stops the tooltip promising damage the player will not always get.
 */
public final class HeldItemStats {

    private HeldItemStats() {
    }

    /** What the Well Fed effect multiplies outgoing damage by, mirroring TotemEffectEvents. */
    public static final double WELL_FED_BONUS = 0.2;

    /**
     * The attack damage a vanilla tooltip prints for this item, or zero when it prints none.
     *
     * Made up of the same three parts {@code ItemStack.getTooltipLines} adds together for its
     * "N Attack Damage" line: what the item grants, the player's own bare-handed base, and Sharpness.
     * Whether that line is really there is the tooltip's business to check - an item can grant attack
     * damage through a modifier vanilla lists differently, and correcting a line that was never
     * written is not something this can decide from here.
     */
    public static double baseAttackDamage(Player player, ItemStack stack) {
        double granted = 0.0;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                granted += modifier.getAmount();
            }
        }
        if (granted <= 0.0) return 0.0;

        return granted
                + player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
                + EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED);
    }

    /**
     * Bonuses that raise every hit this item lands, in the order the damage handler adds them.
     *
     * A musket swung as a club still counts as a musket to {@link
     * net.finnigan.tommemod.skill.event.SkillCombatBonuses}, which is why it collects the ranged keys
     * rather than the melee one - the shot and the swing are the same weapon as far as the tree is
     * concerned.
     */
    public static List<ResourceLocation> countedAttackKeys(ItemStack stack) {
        List<ResourceLocation> keys = new ArrayList<>();

        if (stack.getItem() instanceof MusketItem) {
            keys.add(ModSkillBonuses.RANGED_DAMAGE);
            keys.add(ModSkillBonuses.MUSKET_DAMAGE);
            keys.add(ModSkillBonuses.BAYONET_DAMAGE);
        } else if (stack.getItem() instanceof CrossbowItem) {
            keys.add(ModSkillBonuses.MELEE_DAMAGE);
            keys.add(ModSkillBonuses.BAYONET_DAMAGE);
        } else {
            keys.add(ModSkillBonuses.MELEE_DAMAGE);
        }

        if (stack.is(ModTags.Items.UNIQUE)) keys.add(ModSkillBonuses.UNIQUE_DAMAGE);
        return keys;
    }

    /** Bonuses that wait on the target or the moment, listed but never added into the headline. */
    public static List<ResourceLocation> situationalAttackKeys(ItemStack stack) {
        List<ResourceLocation> keys = new ArrayList<>(List.of(
                ModSkillBonuses.UNDEAD_DAMAGE,
                ModSkillBonuses.MOUNTED_MELEE_DAMAGE,
                ModSkillBonuses.AMBUSH_DAMAGE,
                ModSkillBonuses.COMBO_DAMAGE,
                ModSkillBonuses.EXECUTE_DAMAGE,
                ModSkillBonuses.DESPERATION_DAMAGE,
                ModSkillBonuses.OUTNUMBERED));

        if (stack.getItem() instanceof MusketItem) {
            keys.add(ModSkillBonuses.ASSASSINATE);
            keys.add(ModSkillBonuses.SUPPRESSED_DAMAGE);
        }
        return keys;
    }

    /**
     * The mining speed a tool digs at, before the block being dug has any say.
     *
     * Vanilla prints no such line at all, so this is the number {@code Player.getDestroySpeed} starts
     * from: the tier's speed, plus Efficiency's own addition on top of it. Everything after that -
     * Haste, water, standing on nothing - belongs to the block and the moment rather than to the tool.
     */
    public static double baseMiningSpeed(ItemStack stack) {
        if (!(stack.getItem() instanceof DiggerItem digger)) return 0.0;

        float speed = digger.getTier().getSpeed();
        int efficiency = stack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY);
        if (efficiency > 0) speed += efficiency * efficiency + 1;
        return speed;
    }

    /** Mining speed bonuses that apply to every block. */
    public static List<ResourceLocation> countedMiningKeys() {
        return List.of(ModSkillBonuses.MINING_SPEED);
    }

    /** Mining speed bonuses that wait on the block, the streak or where the player is standing. */
    public static List<ResourceLocation> situationalMiningKeys() {
        return List.of(ModSkillBonuses.DIG_MOMENTUM,
                ModSkillBonuses.HARDPAN,
                ModSkillBonuses.MINING_PENALTY_IGNORE);
    }
}
