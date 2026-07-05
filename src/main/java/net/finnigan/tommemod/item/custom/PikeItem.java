package net.finnigan.tommemod.item.custom;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.ForgeMod;

import java.util.Set;
import java.util.UUID;

public class PikeItem extends Item {

    public static final double CLOSE_RANGE_THRESHOLD = 2.5;

    // Every enchantment the pike is allowed to receive, EXCEPT Sweeping Edge.
    // Add/remove freely — just never add Enchantments.SWEEPING_EDGE here.
    public static final Set<Enchantment> ALLOWED_ENCHANTMENTS = Set.of(
            Enchantments.SHARPNESS,
            Enchantments.SMITE,
            Enchantments.BANE_OF_ARTHROPODS,
            Enchantments.KNOCKBACK,
            Enchantments.FIRE_ASPECT,
            Enchantments.MOB_LOOTING,
            Enchantments.UNBREAKING,
            Enchantments.MENDING,
            Enchantments.VANISHING_CURSE
    );

    private final Tier tier;
    private final Multimap<Attribute, AttributeModifier> attributeModifiers;

    public PikeItem(Tier tier, int attackDamage, float attackSpeedModifier,
                    double reachBonus, float knockbackBonus, Item.Properties properties) {
        super(properties.durability(tier.getUses()));
        this.tier = tier;

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();

        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                tier.getAttackDamageBonus() + attackDamage, AttributeModifier.Operation.ADDITION));

        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                attackSpeedModifier, AttributeModifier.Operation.ADDITION));

        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                UUID.fromString("c8fdf2d3-1e5a-4a8b-9e3c-6b1a2f9d7e11"),
                "Pike reach bonus", reachBonus, AttributeModifier.Operation.ADDITION));

        builder.put(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(
                UUID.fromString("d4a1e6f7-2b3c-4d5e-8f9a-1b2c3d4e5f22"),
                "Pike knockback bonus", knockbackBonus, AttributeModifier.Operation.ADDITION));

        this.attributeModifiers = builder.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? attributeModifiers : super.getDefaultAttributeModifiers(slot);
    }

    // Without this, the pike won't be enchantable at all (default Item enchantment value is 0).
    @Override
    public int getEnchantmentValue() {
        return tier.getEnchantmentValue();
    }

    // Enchanting table / loot-table enchant randomization / villager trades all check this per-enchant.
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return ALLOWED_ENCHANTMENTS.contains(enchantment);
    }

    @Override
    public boolean isValidRepairItem(ItemStack pike, ItemStack repair) {
        return tier.getRepairIngredient().test(repair);
    }
}