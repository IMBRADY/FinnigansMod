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
 * Attribute-backed armor enchantments (Fleet, Resilience, Tolerance). All are recalculated on every
 * equipment change rather than ticked: the modifiers are transient, so remove-then-re-add off the
 * current gear is enough, and LivingEquipmentChangeEvent also fires when an entity first loads its
 * equipment.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EnchantmentAttributeEvents {

    private static final UUID FLEET_MODIFIER_ID = UUID.fromString("6f2c9d51-0b3a-4a7e-8f21-1c5a9e40b7d1");
    private static final UUID RESILIENCE_MODIFIER_ID = UUID.fromString("6f2c9d51-0b3a-4a7e-8f21-1c5a9e40b7d2");
    private static final UUID TOLERANCE_MODIFIER_ID = UUID.fromString("6f2c9d51-0b3a-4a7e-8f21-1c5a9e40b7d3");

    private static final double FLEET_SPEED_PER_LEVEL = 0.10D;
    /**
     * Halved from the 0.05 it shipped at.
     *
     * Resilience is per-piece and sums, so the old figure put a full set of Resilience III at +60%
     * knockback resistance before netherite's own 0.40 was counted - the two together were immunity,
     * and a player who could not be moved is one the game has no remaining answer to. At 0.025 a full
     * set is +30%, which netherite tops up to a strong but still displaceable 70%.
     */
    private static final double RESILIENCE_PER_LEVEL = 0.025D;
    /** Points of armor toughness per level of Tolerance, per enchanted piece. */
    private static final double TOLERANCE_PER_LEVEL = 1.0D;

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
        applyTolerance(entity);
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

        // Protection-style: every enchanted piece contributes, so 7.5% is the per-piece ceiling rather
        // than the total - a full set of Resilience III comes to +30%.
        int totalLevels = 0;
        for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
            totalLevels += EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.RESILIENCE.get(), entity.getItemBySlot(armorSlot));
        }
        if (totalLevels <= 0) return;

        // Knockback resistance is a 0..1 scale where 1.0 is outright immunity, and a full set tops out
        // at 12 levels = 0.30 - which netherite armor's own 0.10 per piece takes to 0.70. Strong, and
        // still short of the point where nothing in the game can move the player at all.
        resistance.addTransientModifier(new AttributeModifier(RESILIENCE_MODIFIER_ID, "tommemod_resilience",
                RESILIENCE_PER_LEVEL * totalLevels, AttributeModifier.Operation.ADDITION));
    }

    /**
     * Tolerance: +1 armor toughness per level, counted across every enchanted piece as Resilience is.
     *
     * Toughness is the right scale for this to sum on. It is not a percentage and does not approach a
     * ceiling the way knockback resistance does - it scales how much of a big hit armor absorbs, with
     * diminishing returns already built into vanilla's damage formula, so a full set of Tolerance III
     * coming to +12 is strong without being the kind of total that stops the game working.
     */
    private static void applyTolerance(LivingEntity entity) {
        AttributeInstance toughness = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness == null) return;

        toughness.removeModifier(TOLERANCE_MODIFIER_ID);

        int totalLevels = 0;
        for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
            totalLevels += EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.TOLERANCE.get(), entity.getItemBySlot(armorSlot));
        }
        if (totalLevels <= 0) return;

        toughness.addTransientModifier(new AttributeModifier(TOLERANCE_MODIFIER_ID, "tommemod_tolerance",
                TOLERANCE_PER_LEVEL * totalLevels, AttributeModifier.Operation.ADDITION));
    }
}
