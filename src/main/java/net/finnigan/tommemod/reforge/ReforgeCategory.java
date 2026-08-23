package net.finnigan.tommemod.reforge;

/**
 * What kind of gear a reforge can land on. Kept separate from the item classes because the categories
 * are about what a reforge means, not what an item extends - a dagger is a weapon despite deliberately
 * not extending {@code SwordItem}, and a shield takes the defensive rolls without being armour.
 */
public enum ReforgeCategory {
    /** Swords, sword types, bows and bow types. */
    WEAPON,
    /** Helmets, chestplates, leggings and boots. */
    ARMOR,
    /** Shields. Takes the defensive rolls, but not the ones that only make sense worn. */
    SHIELD
}
