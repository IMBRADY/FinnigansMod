package net.finnigan.tommemod.item;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.function.Supplier;

public enum ModArmorMaterials implements ArmorMaterial {

    /**
     * Creative-only, so nothing here is balanced against progression: zero durability (an
     * unbreakable item), no repair ingredient and no enchantability, since
     * {@link net.finnigan.tommemod.item.custom.InvincibootsItem} carries its Protection intrinsically.
     */
    INVINCIBOOTS("invinciboots", 0, defense(5), 0, SoundEvents.ARMOR_EQUIP_NETHERITE, 5.0F, 0.0F,
            () -> Ingredient.EMPTY);

    private final String name;
    private final int durabilityMultiplier;
    private final EnumMap<ArmorItem.Type, Integer> defenceByType;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    ModArmorMaterials(String name, int durabilityMultiplier, EnumMap<ArmorItem.Type, Integer> defenceByType,
                      int enchantmentValue, SoundEvent equipSound, float toughness, float knockbackResistance,
                      Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.defenceByType = defenceByType;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    private static EnumMap<ArmorItem.Type, Integer> defense(int boots) {
        EnumMap<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.BOOTS, boots);
        return map;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return durabilityMultiplier; // 0 - ArmorItem reads this as "no durability bar, never breaks"
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return defenceByType.getOrDefault(type, 0);
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }

    @Override
    public String getName() {
        return TommeMod.MOD_ID + ":" + name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
