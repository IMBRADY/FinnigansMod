package net.finnigan.tommemod.reforge;

import net.finnigan.tommemod.util.ModTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reading, writing and rolling the reforge on an {@link ItemStack}.
 */
public final class Reforges {

    /** Presence of this key is what marks a stack as already rolled. Every roll produces a reforge. */
    private static final String TAG_REFORGE = "TommeModReforge";

    private static final float POSITIVE_CHANCE = 0.85F;

    private static final Map<ReforgeCategory, List<Reforge>> POSITIVE_POOL = new EnumMap<>(ReforgeCategory.class);
    private static final Map<ReforgeCategory, List<Reforge>> NEGATIVE_POOL = new EnumMap<>(ReforgeCategory.class);

    static {
        for (ReforgeCategory category : ReforgeCategory.values()) {
            POSITIVE_POOL.put(category, new ArrayList<>());
            NEGATIVE_POOL.put(category, new ArrayList<>());
        }
        for (Reforge reforge : Reforge.values()) {
            for (ReforgeCategory category : ReforgeCategory.values()) {
                if (!reforge.appliesTo(category)) continue;
                (reforge.positive() ? POSITIVE_POOL : NEGATIVE_POOL).get(category).add(reforge);
            }
        }
    }

    private Reforges() {
    }

    /**
     * Which reforge pool this stack draws from, or null if it takes none.
     *
     * <p>Uniques are excluded by tag. So is anything that stacks past one: reforges live in NBT, and two
     * otherwise-identical items with different reforges stop stacking, which would quietly shred stacks
     * of any eligible stackable item.
     */
    @Nullable
    public static ReforgeCategory categoryOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 1) return null;
        if (stack.is(ModTags.Items.UNIQUE)) return null;

        Item item = stack.getItem();
        if (item instanceof ArmorItem) return ReforgeCategory.ARMOR;
        if (item instanceof ShieldItem) return ReforgeCategory.SHIELD;
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem) {
            return ReforgeCategory.WEAPON;
        }
        // Catches this mod's weapons that deliberately don't extend a vanilla weapon class - daggers,
        // pikes, halberds, the musket, the god sword.
        if (stack.is(ModTags.Items.REFORGEABLE_WEAPONS)) return ReforgeCategory.WEAPON;

        return null;
    }

    public static boolean isEligible(ItemStack stack) {
        return categoryOf(stack) != null;
    }

    @Nullable
    public static Reforge get(ItemStack stack) {
        if (stack.isEmpty()) return null;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_REFORGE)) return null;

        String id = tag.getString(TAG_REFORGE);
        for (Reforge reforge : Reforge.values()) {
            if (reforge.id().equals(id)) return reforge;
        }
        return null;   // unknown id: a reforge that was removed or renamed, treated as absent
    }

    public static void set(ItemStack stack, Reforge reforge) {
        stack.getOrCreateTag().putString(TAG_REFORGE, reforge.id().toLowerCase(Locale.ROOT));
    }

    public static boolean hasBeenRolled(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_REFORGE);
    }

    /**
     * Give this stack a reforge if it should have one and doesn't yet.
     *
     * @return true if a reforge was assigned by this call.
     */
    public static boolean ensureRolled(ItemStack stack, RandomSource random) {
        if (hasBeenRolled(stack)) return false;

        ReforgeCategory category = categoryOf(stack);
        if (category == null) return false;

        set(stack, roll(category, random));
        return true;
    }

    public static Reforge roll(ReforgeCategory category, RandomSource random) {
        boolean positive = random.nextFloat() < POSITIVE_CHANCE;

        List<Reforge> pool = (positive ? POSITIVE_POOL : NEGATIVE_POOL).get(category);
        if (pool.isEmpty()) pool = (positive ? NEGATIVE_POOL : POSITIVE_POOL).get(category);

        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Whether a stack's reforge should be live while sitting in this slot - a sword's bonus shouldn't
     * count from the boots slot, and a chestplate's shouldn't count from the hand.
     */
    public static boolean appliesInSlot(ItemStack stack, EquipmentSlot slot) {
        ReforgeCategory category = categoryOf(stack);
        if (category == null) return false;

        return switch (category) {
            case WEAPON -> slot == EquipmentSlot.MAINHAND;
            case SHIELD -> slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
            case ARMOR  -> slot == LivingEntity.getEquipmentSlotForItem(stack);
        };
    }
}
