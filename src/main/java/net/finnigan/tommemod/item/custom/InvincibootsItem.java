package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.item.ModArmorMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creative-only boots: Protection 25 and Elytra flight, with a sneak-boost that tunnels through
 * whatever is in front of you and detonates when you let go. The flight and the boost live in
 * event/InvincibootsHandler; this class is the item itself and its intrinsic Protection.
 *
 * <p>Protection is reported through Forge's enchantment hooks rather than written into the stack's
 * NBT, so it can't be ground off, overwritten by an anvil, or lost by a /give without tags - the
 * boots simply always have it. Note vanilla caps total Protection at 20 points of the
 * damage-reduction formula ({@code CombatRules#getDamageAfterMagicAbsorb}), so 25 is worn as the
 * maximum 80% reduction rather than anything beyond it.
 */
public class InvincibootsItem extends ArmorItem {

    public static final int PROTECTION_LEVEL = 25;

    public InvincibootsItem(Properties properties) {
        super(ModArmorMaterials.INVINCIBOOTS, Type.BOOTS, properties);
    }

    @Override
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.ALL_DAMAGE_PROTECTION) return PROTECTION_LEVEL;
        return super.getEnchantmentLevel(stack, enchantment);
    }

    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> enchantments = new HashMap<>(super.getAllEnchantments(stack));
        enchantments.put(Enchantments.ALL_DAMAGE_PROTECTION, PROTECTION_LEVEL);
        return enchantments;
    }

    /** Nothing may be added on top - a second Protection would only fight the intrinsic one. */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Placeholder art: the gilded stand-in layer shipped with the mod. */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "tommemod:textures/models/armor/invinciboots_layer_1.png";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // Vanilla's enchantment tooltip is built from the stack's NBT, which this Protection is
        // deliberately not in, so it has to be listed by hand or it looks like it isn't there.
        tooltip.add(Component.translatable(Enchantments.ALL_DAMAGE_PROTECTION.getDescriptionId())
                .append(" " + PROTECTION_LEVEL).withStyle(ChatFormatting.GRAY));
    }
}
