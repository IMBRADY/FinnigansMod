package net.finnigan.tommemod.village;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;

/**
 * What a Barracks Warrior takes from the chest by its bed, and how.
 *
 * <p>The squires leave kit in that chest rather than putting it on the Warrior; the Warrior decides
 * what to keep using the same rules the squires used to apply themselves (see {@link SquireGear}):
 * <ul>
 *   <li>a bare slot takes anything that fits it;</li>
 *   <li>a slot the village filled is upgraded only by something genuinely stronger;</li>
 *   <li>a slot a player filled is never touched - the chest's piece is left where it is;</li>
 *   <li>a Warrior that shoots keeps shooting - melee weapons don't replace a bow;</li>
 *   <li>arrows go to the supply slot, healing is drunk when hurt and strength when unbuffed.</li>
 * </ul>
 */
public final class WarriorKit {

    private WarriorKit() {
    }

    public static boolean wants(WarriorVillagerEntity w, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ArmorItem) {
            EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
            return canReplace(w, slot) && SquireGear.isStronger(stack, w.getItemBySlot(slot), slot);
        }
        if (isRanged(stack)) {
            return canReplace(w, EquipmentSlot.MAINHAND) && !isRanged(w.getMainHandItem());
        }
        if (isMeleeWeapon(stack)) {
            return canReplace(w, EquipmentSlot.MAINHAND) && !isRanged(w.getMainHandItem())
                    && SquireGear.isStronger(stack, w.getMainHandItem(), EquipmentSlot.MAINHAND);
        }
        if (stack.getItem() instanceof ArrowItem) {
            ItemStack supply = w.getSupplyContainer().getItem(0);
            return isRanged(w.getMainHandItem()) && (supply.isEmpty()
                    || (ItemStack.isSameItemSameTags(supply, stack) && supply.getCount() < supply.getMaxStackSize()));
        }
        if (isDrinkable(stack)) {
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
                if (effect.getEffect() == MobEffects.HEAL && w.getHealth() < w.getMaxHealth() - 2) return true;
                if (effect.getEffect() == MobEffects.DAMAGE_BOOST && !w.hasEffect(MobEffects.DAMAGE_BOOST)) return true;
            }
        }
        return false;
    }

    public static boolean anyWanted(WarriorVillagerEntity w, Container chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (wants(w, chest.getItem(i))) return true;
        }
        return false;
    }

    /** Takes everything wanted from the chest, in chest order. Returns true if anything was taken. */
    public static boolean takeFrom(WarriorVillagerEntity w, Container chest) {
        boolean took = false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!wants(w, stack)) continue;
            took = true;
            if (stack.getItem() instanceof ArrowItem) {
                ItemStack supply = w.getSupplyContainer().getItem(0);
                int room = supply.isEmpty() ? stack.getMaxStackSize() : supply.getMaxStackSize() - supply.getCount();
                int moved = Math.min(room, stack.getCount());
                if (supply.isEmpty()) w.getSupplyContainer().setItem(0, stack.split(moved));
                else {
                    supply.grow(moved);
                    stack.shrink(moved);
                }
            } else if (isDrinkable(stack)) {
                drink(w, stack.split(1));
            } else {
                EquipmentSlot slot = stack.getItem() instanceof ArmorItem
                        ? LivingEntity.getEquipmentSlotForItem(stack) : EquipmentSlot.MAINHAND;
                // The piece it replaces was village kit (see canReplace) and goes back to the village.
                w.setItemSlot(slot, stack.split(1));
                w.markSquireIssued(slot);
            }
            chest.setItem(i, stack);
        }
        if (took) chest.setChanged();
        return took;
    }

    private static boolean canReplace(WarriorVillagerEntity w, EquipmentSlot slot) {
        return w.getItemBySlot(slot).isEmpty() || w.isSquireIssued(slot);
    }

    private static void drink(WarriorVillagerEntity w, ItemStack potion) {
        for (MobEffectInstance effect : PotionUtils.getMobEffects(potion)) {
            if (effect.getEffect().isInstantenous()) {
                effect.getEffect().applyInstantenousEffect(w, w, w, effect.getAmplifier(), 1.0D);
            } else {
                w.addEffect(new MobEffectInstance(effect));
            }
        }
        w.playSound(SoundEvents.GENERIC_DRINK, 1.0F, 1.0F);
    }

    public static boolean isRanged(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    private static boolean isDrinkable(ItemStack stack) {
        return stack.getItem() instanceof PotionItem
                && !(stack.getItem() instanceof SplashPotionItem)
                && !(stack.getItem() instanceof LingeringPotionItem);
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return !stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty();
    }
}
