package net.finnigan.tommemod.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
 * A multi-swig honey draught: eight levels in one bottle, one spent per drink, emptying into a glass
 * bottle on the last. Levels live in NBT rather than durability so the stack shows the fill level
 * through its model instead of a damage bar (see ClientSetup's "buzz_level" item property).
 *
 * Like BottleOfAleItem this feeds the player by hand instead of through FoodProperties, so the whole
 * consumption reads as a potion: DRINK animation (no chewing particles), drink sound, no burp.
 */
public class BetterBuzzItem extends Item {

    public static final int MAX_LEVELS = 8;
    public static final int MIN_LEVELS = 1;

    private static final String LEVELS_TAG = "Levels";
    private static final int NUTRITION = 2;              // 1 shank
    private static final float SATURATION_MODIFIER = 0.3F;
    private static final float HEAL_AMOUNT = 2.0F;       // 1 heart

    public BetterBuzzItem(Properties properties) {
        super(properties);
    }

    /** Untagged stacks (creative, /give, loot) count as full rather than empty. */
    public static int getLevels(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(LEVELS_TAG, Tag.TAG_INT)) return MAX_LEVELS;
        return Mth.clamp(tag.getInt(LEVELS_TAG), MIN_LEVELS, MAX_LEVELS);
    }

    private static void setLevels(ItemStack stack, int levels) {
        stack.getOrCreateTag().putInt(LEVELS_TAG, Mth.clamp(levels, MIN_LEVELS, MAX_LEVELS));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.removeEffect(MobEffects.POISON);
        }
        entity.heal(HEAL_AMOUNT);

        if (!(entity instanceof Player player)) return stack;

        player.getFoodData().eat(NUTRITION, SATURATION_MODIFIER);
        if (player.getAbilities().instabuild) return stack;

        int remaining = getLevels(stack) - 1;
        if (remaining >= MIN_LEVELS) {
            setLevels(stack, remaining);
            return stack;
        }

        stack.shrink(1); // last swig - the bottle goes with it, nothing left behind
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
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
        tooltip.add(Component.literal(getLevels(stack) + " / " + MAX_LEVELS + " swigs left")
                .withStyle(ChatFormatting.GRAY));
    }
}
