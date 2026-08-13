package net.finnigan.tommemod.skill.bonus;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;

/**
 * Every named bonus a node may grant, and the complete list of what the handlers in
 * {@code skill.event} know how to act on.
 *
 * This is the second half of the code/data seam - {@link net.finnigan.tommemod.skill.xp.ModSkillActions}
 * is what goes in, this is what comes out. A tree file can point any node at any of these keys, stack
 * several nodes onto one, or split one node across several, and no Java changes. What needs code is
 * only ever a genuinely new <em>kind</em> of effect: one constant here, plus the handler that reads it.
 *
 * Anything expressible as a vanilla or Forge attribute is deliberately absent. Health, armour, attack
 * damage, luck, swim speed, gravity, step height and reach are all attributes already, so nodes that
 * want them use {@code tommemod:attribute} and need no key and no handler at all.
 *
 * Every key here is a fraction unless its comment says otherwise, and every one of them is read
 * through {@link SkillBonuses}, which clamps the ones used as probabilities or reductions.
 */
public final class ModSkillBonuses {

    private ModSkillBonuses() {
    }

    // ---- Agility ----

    /** Extra upward velocity per jump, in blocks per tick added to the vanilla impulse. */
    public static final ResourceLocation JUMP_POWER = id("jump_power");
    /** Fraction of fall damage ignored. */
    public static final ResourceLocation FALL_DAMAGE_REDUCTION = id("fall_damage_reduction");
    /** Extra blocks of fall shrugged off before damage begins at all. */
    public static final ResourceLocation SAFE_FALL_BONUS = id("safe_fall_bonus");
    /** Fraction of hunger exhaustion refunded - the design sketch's "reduced sprint exhaustion". */
    public static final ResourceLocation EXHAUSTION_REDUCTION = id("exhaustion_reduction");
    /** Fraction of underwater air loss skipped. */
    public static final ResourceLocation BREATH_RETENTION = id("breath_retention");
    /** Fraction added to movement speed, but only while actually sprinting. */
    public static final ResourceLocation SPRINT_SPEED = id("sprint_speed");

    // ---- Riding, sailing, gliding ----

    /** Fraction added to a ridden mount's own movement speed. */
    public static final ResourceLocation MOUNT_SPEED = id("mount_speed");
    /** Fraction of damage a ridden mount ignores. */
    public static final ResourceLocation MOUNT_DAMAGE_REDUCTION = id("mount_damage_reduction");
    /** Fraction of damage the rider ignores while mounted or aboard a boat. */
    public static final ResourceLocation RIDER_DAMAGE_REDUCTION = id("rider_damage_reduction");
    /** Fraction added to the speed of a boat this player is steering. */
    public static final ResourceLocation BOAT_SPEED = id("boat_speed");
    /** Fraction added to forward momentum while flying an elytra. */
    public static final ResourceLocation GLIDE_SPEED = id("glide_speed");
    /** Chance a tick of elytra flight costs the wings no durability. */
    public static final ResourceLocation ELYTRA_DURABILITY_SAVE = id("elytra_durability_save");

    // ---- Gathering ----

    /** Fraction added to block breaking speed. */
    public static final ResourceLocation MINING_SPEED = id("mining_speed");
    /** Chance a broken ore drops a second time. */
    public static final ResourceLocation ORE_DOUBLE_DROP = id("ore_double_drop");
    /** Chance a broken shovel block drops a second time. */
    public static final ResourceLocation EXCAVATION_DOUBLE_DROP = id("excavation_double_drop");
    /** Chance a broken log, leaf block or crop drops a second time. */
    public static final ResourceLocation FORAGING_DOUBLE_DROP = id("foraging_double_drop");
    /** Chance any durability loss is waived entirely. */
    public static final ResourceLocation TOOL_DURABILITY_SAVE = id("tool_durability_save");
    /** Fraction added to the vanilla experience a broken block drops. */
    public static final ResourceLocation BLOCK_XP_BONUS = id("block_xp_bonus");

    // ---- Husbandry ----

    /** Fraction of the post-breeding cooldown skipped by both parents. */
    public static final ResourceLocation BREEDING_COOLDOWN_REDUCTION = id("breeding_cooldown_reduction");
    /** Chance a bred pair produces a second baby. */
    public static final ResourceLocation TWIN_BIRTH_CHANCE = id("twin_birth_chance");
    /** Chance a killed animal drops an extra copy of its loot. */
    public static final ResourceLocation ANIMAL_DROP_BONUS = id("animal_drop_bonus");

    // ---- Smithing ----

    /** Fraction of an anvil's level cost waived. */
    public static final ResourceLocation ANVIL_COST_REDUCTION = id("anvil_cost_reduction");
    /** Fraction of extra durability an anvil repair restores. */
    public static final ResourceLocation REPAIR_EFFICIENCY = id("repair_efficiency");

    // ---- Combat, shared ----

    /** Fraction added to outgoing damage of the matching kind. */
    public static final ResourceLocation MELEE_DAMAGE = id("melee_damage");
    public static final ResourceLocation UNARMED_DAMAGE = id("unarmed_damage");
    public static final ResourceLocation RANGED_DAMAGE = id("ranged_damage");
    /** Chance an ordinary hit is upgraded to a critical. */
    public static final ResourceLocation CRIT_CHANCE = id("crit_chance");
    /** Fraction added to critical damage. */
    public static final ResourceLocation CRIT_DAMAGE = id("crit_damage");
    /** Fraction of melee damage dealt returned to the attacker as health. */
    public static final ResourceLocation LIFESTEAL = id("lifesteal");
    /** Fraction added to damage against the undead. */
    public static final ResourceLocation UNDEAD_DAMAGE = id("undead_damage");
    /** Fraction added to knockback dealt by unarmed strikes. */
    public static final ResourceLocation UNARMED_KNOCKBACK = id("unarmed_knockback");

    // ---- Archery and marksmanship ----

    /** Fraction of bow draw and crossbow reload time skipped. */
    public static final ResourceLocation DRAW_SPEED = id("draw_speed");
    /** Fraction added to the launch speed of arrows this player fires. */
    public static final ResourceLocation ARROW_VELOCITY = id("arrow_velocity");
    /** Fraction of gravity removed from fired arrows, for flatter trajectories. */
    public static final ResourceLocation ARROW_STEADINESS = id("arrow_steadiness");
    /** Fraction added to projectile damage per ten blocks it travelled. */
    public static final ResourceLocation LONG_SHOT_DAMAGE = id("long_shot_damage");
    /** Fraction added when a projectile strikes above the target's mid-line. */
    public static final ResourceLocation HEADSHOT_DAMAGE = id("headshot_damage");

    // ---- Defence ----

    /** Chance a blocked blow costs the shield no durability. */
    public static final ResourceLocation SHIELD_DURABILITY_SAVE = id("shield_durability_save");
    /** Fraction of blocked damage returned to the attacker. */
    public static final ResourceLocation RIPOSTE = id("riposte");
    /** Fraction of all incoming damage ignored, whatever its source. */
    public static final ResourceLocation DAMAGE_REDUCTION = id("damage_reduction");
    /** Fraction of incoming explosion damage ignored. */
    public static final ResourceLocation BLAST_RESISTANCE = id("blast_resistance");
    /** Fraction of incoming fire damage ignored. */
    public static final ResourceLocation FIRE_RESISTANCE = id("fire_resistance");
    /** Fraction of incoming projectile damage ignored. */
    public static final ResourceLocation PROJECTILE_RESISTANCE = id("projectile_resistance");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(TommeMod.MOD_ID, path);
    }
}
