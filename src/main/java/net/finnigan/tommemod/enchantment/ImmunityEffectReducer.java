package net.finnigan.tommemod.enchantment;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Immunity's actual effect, split out of LivingEntityImmunityMixin so the mixin stays a one-liner.
 *
 * Each armor piece carrying Immunity halves both the power and the remaining duration of an incoming
 * harmful effect, rounding up and never dropping below 1 - so a full set lands at 0.5^4 = 0.0625 of
 * the original. The halving is applied once per piece rather than as a single multiply because that
 * is literally how the design describes it (and, because ceil(ceil(x/2)/2) == ceil(x/4), the two come
 * out identical anyway).
 *
 * Power is treated as the 1-based level the player sees, not the 0-based amplifier: Poison II halves
 * to Poison I, and Poison I stays Poison I rather than vanishing.
 */
public final class ImmunityEffectReducer {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ImmunityEffectReducer() {
    }

    /** Returns the instance to actually apply - the original untouched when Immunity doesn't apply. */
    public static MobEffectInstance reduce(LivingEntity entity, MobEffectInstance instance) {
        if (instance == null) return null;
        if (instance.getEffect().getCategory() != MobEffectCategory.HARMFUL) return instance;

        int pieces = countImmunityPieces(entity);
        if (pieces <= 0) return instance;

        int level = instance.getAmplifier() + 1;
        int duration = instance.getDuration();
        for (int i = 0; i < pieces; i++) {
            level = Math.max(1, halveRoundingUp(level));
            duration = Math.max(1, halveRoundingUp(duration));
        }

        if (level == instance.getAmplifier() + 1 && duration == instance.getDuration()) {
            return instance;
        }

        MobEffectInstance reduced = new MobEffectInstance(instance.getEffect(), duration, level - 1,
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
        reduced.setCurativeItems(instance.getCurativeItems());
        return reduced;
    }

    private static int countImmunityPieces(LivingEntity entity) {
        int pieces = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.IMMUNITY.get(), entity.getItemBySlot(slot)) > 0) {
                pieces++;
            }
        }
        return pieces;
    }

    private static int halveRoundingUp(int value) {
        return (value + 1) / 2;
    }
}
