package net.finnigan.tommemod.reforge;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static net.finnigan.tommemod.reforge.ReforgeCategory.ARMOR;
import static net.finnigan.tommemod.reforge.ReforgeCategory.SHIELD;
import static net.finnigan.tommemod.reforge.ReforgeCategory.WEAPON;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL;

/**
 * The reforge table. Every entry is a 1% nudge, positive or negative, applied while the item is
 * equipped in the slot it belongs to.
 *
 * <p><b>On the two operations.</b> Most of these use {@link AttributeModifier.Operation#MULTIPLY_TOTAL}
 * so a percentage means that share of whatever the stat already is. Luck does not: it sits at a base of
 * zero for a player, so a multiplicative modifier on it would be exactly zero forever. Lucky and Unlucky
 * use {@link AttributeModifier.Operation#ADDITION} instead, which is what a percentage has to mean on a
 * stat that starts empty.
 *
 * <p>{@link #WARDED} and {@link #EXPOSED} have no attribute at all - vanilla has no "damage taken" stat,
 * so they are resolved in a {@code LivingHurtEvent} handler instead. See
 * {@link net.finnigan.tommemod.event.ReforgeEvents}.
 */
public enum Reforge {

    // ---- Positive ----
    SHARP    ("sharp",    true,  () -> Attributes.ATTACK_DAMAGE,  MULTIPLY_TOTAL,  0.01D, WEAPON),
    KEEN     ("keen",     true,  () -> Attributes.ATTACK_SPEED,   MULTIPLY_TOTAL,  0.05D, WEAPON),
    HASTY    ("hasty",    true,  () -> Attributes.MOVEMENT_SPEED, MULTIPLY_TOTAL,  0.01D, WEAPON, ARMOR, SHIELD),
    WARDED   ("warded",   true,  null,                            null,           -0.01D, ARMOR, SHIELD),
    STURDY   ("sturdy",   true,  () -> Attributes.ARMOR,          MULTIPLY_TOTAL,  0.01D, ARMOR, SHIELD),
    HARDY    ("hardy",    true,  () -> Attributes.MAX_HEALTH,     MULTIPLY_TOTAL,  0.01D, ARMOR),
    LUCKY    ("lucky",    true,  () -> Attributes.LUCK,           ADDITION,        0.10D, WEAPON, ARMOR, SHIELD),

    // ---- Negative ----
    DULL     ("dull",     false, () -> Attributes.ATTACK_DAMAGE,  MULTIPLY_TOTAL, -0.01D, WEAPON),
    SLUGGISH ("sluggish", false, () -> Attributes.ATTACK_SPEED,   MULTIPLY_TOTAL, -0.05D, WEAPON),
    HEAVY    ("heavy",    false, () -> Attributes.MOVEMENT_SPEED, MULTIPLY_TOTAL, -0.01D, WEAPON, ARMOR, SHIELD),
    EXPOSED  ("exposed",  false, null,                            null,            0.01D, ARMOR, SHIELD),
    BRITTLE  ("brittle",  false, () -> Attributes.ARMOR,          MULTIPLY_TOTAL, -0.01D, ARMOR, SHIELD),
    UNLUCKY  ("unlucky",  false, () -> Attributes.LUCK,           ADDITION,       -0.10D, WEAPON, ARMOR, SHIELD);

    private final String id;
    private final boolean positive;
    @Nullable private final Supplier<Attribute> attribute;
    @Nullable private final AttributeModifier.Operation operation;
    private final double amount;
    private final Set<ReforgeCategory> categories;

    /**
     * One stable id per slot. Attribute modifier ids have to be constant across sessions or a modifier
     * applied on login is never the one removed on logout, and the stat drifts upward every relog.
     */
    private final Map<EquipmentSlot, UUID> modifierIds = new EnumMap<>(EquipmentSlot.class);

    Reforge(String id, boolean positive, @Nullable Supplier<Attribute> attribute,
            @Nullable AttributeModifier.Operation operation, double amount, ReforgeCategory... categories) {
        this.id = id;
        this.positive = positive;
        this.attribute = attribute;
        this.operation = operation;
        this.amount = amount;
        this.categories = EnumSet.of(categories[0], categories);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.modifierIds.put(slot, UUID.nameUUIDFromBytes(
                    ("tommemod:reforge:" + id + ":" + slot.getName()).getBytes(StandardCharsets.UTF_8)));
        }
    }

    public String id()        { return this.id; }
    public boolean positive() { return this.positive; }
    public double amount()    { return this.amount; }

    public boolean appliesTo(ReforgeCategory category) {
        return this.categories.contains(category);
    }

    /** True for the two reforges resolved at damage time rather than through an attribute. */
    public boolean affectsDamageTaken() {
        return this.attribute == null;
    }

    /** @return the modifier to apply in this slot, or null if this reforge isn't attribute-driven. */
    @Nullable
    public AttributeModifier modifierFor(EquipmentSlot slot) {
        if (this.operation == null) return null;
        return new AttributeModifier(this.modifierIds.get(slot), "tommemod:reforge", this.amount, this.operation);
    }

    @Nullable
    public Attribute attribute() {
        return this.attribute == null ? null : this.attribute.get();
    }

    public String nameKey() { return "reforge.tommemod." + this.id; }
    public String descKey() { return "reforge.tommemod." + this.id + ".desc"; }
}
