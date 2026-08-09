package net.finnigan.tommemod.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Deliberately NOT a FoodProperties item, even though it feeds you: the brief wants it to drink like
 * a potion, and vanilla's food path is what produces both the chewing particles and Player#eat's
 * burp. Restoring hunger by hand here keeps the whole consumption sounding like PotionItem while
 * still handing back the empty bottle the way HoneyBottleItem does.
 */
public class BottleOfAleItem extends Item {

    private static final int NUTRITION = 3;              // 1.5 shanks
    private static final float SATURATION_MODIFIER = 0.3F;
    private static final int NAUSEA_TICKS = 160;         // 8s
    private static final int RESISTANCE_TICKS = 300;     // 15s

    public BottleOfAleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TICKS, 0));
        }

        if (!(entity instanceof Player player)) return stack;

        player.getFoodData().eat(NUTRITION, SATURATION_MODIFIER);
        if (player.getAbilities().instabuild) return stack;

        stack.shrink(1); // drunk dry - no empty bottle left behind
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK; // DRINK is what suppresses the item particles, EAT is what spawns them
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Goes down rough, but steadies the nerves")
                .withStyle(style -> style.withColor(0xB07A3C)));
    }
}
