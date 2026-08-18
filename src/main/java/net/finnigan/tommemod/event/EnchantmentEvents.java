package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.enchantment.ModEnchantments;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EnchantmentEvents {

    // Heal the attacker a % of the damage they deal, based on Lifesteal level
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            int level = EnchantmentHelper.getEnchantmentLevel(ModEnchantments.LIFE_STEAL.get(), attacker);
            if (level > 0) {
                float healAmount = event.getAmount() * (0.03F * level); // 3% per level, 30% at max
                attacker.heal(healAmount);
            }
        }
    }
    @SubscribeEvent
    public static void onLivingDamagePoison(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            int level = EnchantmentHelper.getEnchantmentLevel(ModEnchantments.POISON.get(), attacker);
            if (level > 0) {
                float chance = 0.25F * level;
                if (attacker.getRandom().nextFloat() < chance) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0)); // 5 seconds poison I
                }
            }
        }
    }

    /** Per level of Heroblade, against anything in {@code tommemod:bosses}. */
    private static final float HEROBLADE_PER_LEVEL = 0.15F;

    /**
     * Heroblade: a weapon that is only worth carrying to the fights that are worth fighting.
     *
     * On {@link LivingHurtEvent} rather than {@link LivingDamageEvent} so the bonus is added before the
     * target's armor takes its cut, which is how Smite and Bane of Arthropods behave and what makes the
     * five levels read as an increase rather than as a rounding error against a boss's protection.
     *
     * Read off the mainhand rather than through {@code getEnchantmentLevel}, so shooting a boss with a
     * bow while a Heroblade sits in the offhand pays nothing - the enchantment is on the blade and it
     * only counts when the blade is what landed.
     */
    @SubscribeEvent
    public static void onLivingHurtHeroblade(LivingHurtEvent event) {
        if (!event.getEntity().getType().is(ModTags.EntityTypes.BOSSES)) return;
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;

        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.HEROBLADE.get(), attacker.getItemBySlot(EquipmentSlot.MAINHAND));
        if (level <= 0) return;

        event.setAmount(event.getAmount() * (1.0F + HEROBLADE_PER_LEVEL * level));
    }
}