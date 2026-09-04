package net.finnigan.tommemod.village;

import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What a village of a given tier lets its squires hand out.
 *
 * <p>The tables live here rather than inside each squire because three separate places have to agree
 * on them: the Armorer/Weaponsmith/Fletcher/Cleric handing gear over, the Chief Desk telling a player
 * what the next tier buys them, and the strength comparison deciding whether a Warrior's existing
 * piece is worth replacing. A table copied into a squire is a table that drifts from the description
 * the player was sold.
 *
 * <p>Tier 0 is every village's starting state and gives nothing: no armor, no weapons, no potions.
 */
public final class VillageTier {

    public static final int MAX = 3;

    private VillageTier() {
    }

    /** Reputation the Chief must hold with the village to buy this tier. */
    @Nullable
    public static ReputationTier reputationRequiredFor(int tier) {
        return switch (tier) {
            case 1 -> ReputationTier.JOURNEYMAN;
            case 2 -> ReputationTier.EXPERT;
            case 3 -> ReputationTier.MASTER;
            default -> null;
        };
    }

    public static String describe(int tier) {
        return switch (tier) {
            case 1 -> "Squires issue chainmail, iron swords and bows";
            case 2 -> "Adds iron plate, spears and cleavers, strength, village buffs";
            case 3 -> "Full iron, instant health II, a chance of diamond";
            default -> "Squires arm no one - the village has no militia";
        };
    }

    // ---- Armorer ----

    /**
     * The armor piece an Armorer of this tier issues for this slot, or null if it issues none.
     *
     * <p>Tier 2's split kit - chainmail above the waist, iron below - is deliberate and not a typo:
     * it is the half-step between a fully chainmail tier 1 and a fully iron tier 3.
     */
    @Nullable
    public static Item armorFor(int tier, EquipmentSlot slot) {
        if (tier <= 0 || !slot.isArmor()) return null;

        boolean iron = switch (tier) {
            case 1 -> false;
            case 2 -> slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
            default -> true;
        };

        return switch (slot) {
            case HEAD -> iron ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET;
            case CHEST -> iron ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE;
            case LEGS -> iron ? Items.IRON_LEGGINGS : Items.CHAINMAIL_LEGGINGS;
            case FEET -> iron ? Items.IRON_BOOTS : Items.CHAINMAIL_BOOTS;
            default -> null;
        };
    }

    // ---- Weaponsmith ----

    /** The iron weapons a Weaponsmith of this tier will forge. Empty below tier 1. */
    public static List<Item> weaponsFor(int tier) {
        if (tier <= 0) return List.of();
        if (tier == 1) return List.of(Items.IRON_SWORD);
        return List.of(Items.IRON_SWORD, ModItems.IRON_PIKE.get(), ModItems.IRON_CLEAVER.get());
    }

    /**
     * The diamond versions of the same three, which only a tier 3 Weaponsmith can roll into. Empty
     * elsewhere, so a caller can roll the chance unconditionally and get nothing below tier 3.
     */
    public static List<Item> diamondWeaponsFor(int tier) {
        if (tier < 3) return List.of();
        return List.of(Items.DIAMOND_SWORD, ModItems.DIAMOND_PIKE.get(), ModItems.DIAMOND_CLEAVER.get());
    }

    // ---- Fletcher ----

    /** Bows and plain arrows at every tier - the Fletcher's kit does not scale, by design. */
    @Nullable
    public static Item bowFor(int tier) {
        return tier <= 0 ? null : Items.BOW;
    }

    @Nullable
    public static Item arrowFor(int tier) {
        return tier <= 0 ? null : Items.ARROW;
    }

    // ---- Cleric ----

    /** Instant Health I from tier 1, II from tier 3. */
    @Nullable
    public static Potion healingFor(int tier) {
        if (tier <= 0) return null;
        return tier >= 3 ? Potions.STRONG_HEALING : Potions.HEALING;
    }

    /** Strength I from tier 2, II from tier 3. Tier 1 Clerics only heal. */
    @Nullable
    public static Potion strengthFor(int tier) {
        if (tier < 2) return null;
        return tier >= 3 ? Potions.STRONG_STRENGTH : Potions.STRENGTH;
    }
}
