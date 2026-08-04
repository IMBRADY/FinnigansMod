package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.enchantment.ModEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Attribute-backed armor enchantments (Fleet, Resilience). Both are recalculated on every equipment
 * change rather than ticked: the modifiers are transient, so remove-then-re-add off the current gear
 * is enough, and LivingEquipmentChangeEvent also fires when an entity first loads its equipment.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EnchantmentAttributeEvents {

    private static final UUID FLEET_MODIFIER_ID = UUID.fromString("6f2c9d51-0b3a-4a7e-8f21-1c5a9e40b7d1");
    private static final UUID RESILIENCE_MODIFIER_ID = UUID.fromString("6f2c9d51-0b3a-4a7e-8f21-1c5a9e40b7d2");

    private static final double FLEET_SPEED_PER_LEVEL = 0.10D;
    private static final double RESILIENCE_PER_LEVEL = 0.05D;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        EquipmentSlot slot = event.getSlot();
        if (slot.getType() != EquipmentSlot.Type.ARMOR) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        if (slot == EquipmentSlot.FEET) {
            applyFleet(entity);
        }
        applyResilience(entity);
    }

    private static void applyFleet(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        speed.removeModifier(FLEET_MODIFIER_ID);

        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FLEET.get(), entity.getItemBySlot(EquipmentSlot.FEET));
        if (level <= 0) return;

        speed.addTransientModifier(new AttributeModifier(FLEET_MODIFIER_ID, "tommemod_fleet",
                FLEET_SPEED_PER_LEVEL * level, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void applyResilience(LivingEntity entity) {
        AttributeInstance resistance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance == null) return;

        resistance.removeModifier(RESILIENCE_MODIFIER_ID);

        // Protection-style: every enchanted piece contributes, so +15% is the per-piece ceiling rather
        // than the total - a full set of Resilience III comes to +60%.
        int totalLevels = 0;
        for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
            totalLevels += EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.RESILIENCE.get(), entity.getItemBySlot(armorSlot));
        }
        if (totalLevels <= 0) return;

        // Knockback resistance is a 0..1 scale where 1.0 is outright immunity, and a full set tops out
        // at 12 levels = 0.60 - which netherite armor's own 0.10 per piece can top up the rest of the way.
        resistance.addTransientModifier(new AttributeModifier(RESILIENCE_MODIFIER_ID, "tommemod_resilience",
                RESILIENCE_PER_LEVEL * totalLevels, AttributeModifier.Operation.ADDITION));
    }
}
