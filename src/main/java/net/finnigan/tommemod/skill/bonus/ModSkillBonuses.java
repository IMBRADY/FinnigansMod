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
 * Anything expressible as a vanilla or Forge attribute is deliberately absent. Health, armor, attack
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
    /** Any amount at all: a sprint reaches its top speed on the first tick instead of winding up. */
    public static final ResourceLocation SPRINT_ACCELERATION = id("sprint_acceleration");
    /** Any amount at all: night vision for as long as the player's eyes are under water. */
    public static final ResourceLocation UNDERWATER_VISION = id("underwater_vision");
    /** Forward impulse added to a jump, in blocks per tick, along the way the player is already going. */
    public static final ResourceLocation JUMP_FORWARD = id("jump_forward");
    /** Fraction added to sprint speed, but only once the sprint has been held a while. */
    public static final ResourceLocation SUSTAINED_SPRINT_SPEED = id("sustained_sprint_speed");
    /** Any amount at all: cobwebs, honey and powder snow stop dragging on the player. */
    public static final ResourceLocation UNHINDERED_STRIDE = id("unhindered_stride");
    /**
     * Fraction taken off the fastest a player may fall, in blocks per tick.
     *
     * Not a gravity change. Gravity acts every tick and compounds, which is what let stacked reductions
     * end in a player who never came down at all; this leaves the pull alone and only refuses to let
     * the fall get past a certain speed. Terminal velocity drops, floating does not become possible.
     */
    public static final ResourceLocation FALL_SPEED_CAP = id("fall_speed_cap");

    // ---- Riding ----

    /** Fraction added to a ridden mount's own movement speed. */
    public static final ResourceLocation MOUNT_SPEED = id("mount_speed");
    /** Fraction of damage a ridden mount ignores. */
    public static final ResourceLocation MOUNT_DAMAGE_REDUCTION = id("mount_damage_reduction");
    /** Fraction of damage the rider ignores while mounted or aboard a boat. */
    public static final ResourceLocation RIDER_DAMAGE_REDUCTION = id("rider_damage_reduction");
    /** Fraction added to the speed of a boat this player is steering. */
    public static final ResourceLocation BOAT_SPEED = id("boat_speed");
    /** Fraction added to a ridden mount's speed, but only once it has been running a while. */
    public static final ResourceLocation MOUNT_SUSTAINED_SPEED = id("mount_sustained_speed");
    /** Blocks of step height added to a ridden mount. */
    public static final ResourceLocation MOUNT_STEP_HEIGHT = id("mount_step_height");
    /** Fraction added to a ridden mount's jump strength. */
    public static final ResourceLocation MOUNT_JUMP_POWER = id("mount_jump_power");
    /** Any amount at all: a mount reaches its top speed on the first tick instead of winding up. */
    public static final ResourceLocation MOUNT_ACCELERATION = id("mount_acceleration");
    /** Half-hearts of max health added to a ridden mount. */
    public static final ResourceLocation MOUNT_HEALTH = id("mount_health");
    /** Fraction of fall damage ignored by a mount, and by its rider while aboard. */
    public static final ResourceLocation MOUNTED_FALL_DAMAGE_REDUCTION = id("mounted_fall_damage_reduction");
    /** Blocks of knockback a mount at a gallop throws whatever it runs into. */
    public static final ResourceLocation MOUNT_TRAMPLE = id("mount_trample");
    /** Half-hearts a ridden mount recovers per second while it is travelling. */
    public static final ResourceLocation MOUNT_REGEN = id("mount_regen");
    /** Fraction added to melee damage, but only while the attacker is riding something. */
    public static final ResourceLocation MOUNTED_MELEE_DAMAGE = id("mounted_melee_damage");

    // ---- Gliding ----

    /** Fraction added to forward momentum while flying an elytra. */
    public static final ResourceLocation GLIDE_SPEED = id("glide_speed");
    /** Chance a tick of elytra flight costs the wings no durability. */
    public static final ResourceLocation ELYTRA_DURABILITY_SAVE = id("elytra_durability_save");
    /** Fraction of gravity cancelled, but only while actually flying an elytra. */
    public static final ResourceLocation ELYTRA_LIFT = id("elytra_lift");
    /** Fraction added to the speed picked up by pointing the nose down. */
    public static final ResourceLocation DIVE_SPEED = id("dive_speed");
    /** Fraction of the speed a hard turn would normally scrub off that is kept instead. */
    public static final ResourceLocation TURN_RETENTION = id("turn_retention");
    /** Fraction added to the shove a firework gives an elytra. */
    public static final ResourceLocation ROCKET_ACCELERATION = id("rocket_acceleration");
    /** Fraction added to how long a firework keeps pushing. */
    public static final ResourceLocation ROCKET_DURATION = id("rocket_duration");
    /** Strength of the burst of air thrown out on taking off, in blocks of knockback. */
    public static final ResourceLocation TAKEOFF_BURST = id("takeoff_burst");
    /** Fraction of fall damage ignored, but only with a working elytra on. */
    public static final ResourceLocation ELYTRA_FALL_DAMAGE_REDUCTION = id("elytra_fall_damage_reduction");
    /** Fraction of the damage of flying into a wall that is ignored. */
    public static final ResourceLocation FLIGHT_COLLISION_REDUCTION = id("flight_collision_reduction");
    /** Durability an idle elytra mends per second on the ground. */
    public static final ResourceLocation ELYTRA_REPAIR = id("elytra_repair");
    /** Fraction added to gliding speed, scaled by how far above sea level the flier is. */
    public static final ResourceLocation ALTITUDE_SPEED = id("altitude_speed");
    /** Fraction added to gliding speed, but only in rain or thunder. */
    public static final ResourceLocation STORM_SPEED = id("storm_speed");
    /** Fraction added to melee damage dealt while diving under an elytra. */
    public static final ResourceLocation DIVE_DAMAGE = id("dive_damage");
    /** Fraction of a flight's speed carried into the run after touching down. */
    public static final ResourceLocation LANDING_MOMENTUM = id("landing_momentum");
    /** Half-hearts recovered per second while gliding. */
    public static final ResourceLocation FLIGHT_REGEN = id("flight_regen");
    /** Chance a firework used to boost a flight is not used up. */
    public static final ResourceLocation ROCKET_CONSERVATION = id("rocket_conservation");
    /** Blocks of shove thrown out when pulling out of a fast dive near the ground. */
    public static final ResourceLocation DIVE_IMPACT = id("dive_impact");

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

    // ---- Excavation ----

    /** Any amount at all: soul sand and honey stop slowing the player down. */
    public static final ResourceLocation LOOSE_GROUND_STRIDE = id("loose_ground_stride");
    /** Fraction added to digging speed per soft block broken in a row, up to a cap. */
    public static final ResourceLocation DIG_MOMENTUM = id("dig_momentum");
    /** How far either side of the struck block a shovel clears: 1 is a 3x3 face, 3 a 7x7. */
    public static final ResourceLocation EXCAVATION_AREA = id("excavation_area");
    /** Any amount at all: a wide swing never digs above the block that was aimed at. */
    public static final ResourceLocation TERRACING = id("terracing");
    /** Any amount at all: sneaking narrows a wide swing to one block, and a shovel can replace earth. */
    public static final ResourceLocation BACKFILL = id("backfill");
    /** Any amount at all: the extra blocks of a wide swing cost the shovel no durability. */
    public static final ResourceLocation WIDE_SWING_FREE = id("wide_swing_free");
    /** Any amount at all: clay, mud and rooted dirt dig at full speed and give their best drop. */
    public static final ResourceLocation HARDPAN = id("hardpan");
    /** Chance digging turns up something buried. */
    public static final ResourceLocation BURIED_FIND = id("buried_find");
    /** Chance a buried find comes from the good table rather than the common one. */
    public static final ResourceLocation TREASURE_QUALITY = id("treasure_quality");
    /** Any amount at all: dug blocks go to the inventory rather than onto the floor. */
    public static final ResourceLocation EXCAVATION_DIRECT_HARVEST = id("excavation_direct_harvest");
    /** Blocks of range at which suspicious sand and gravel are marked for this player alone. */
    public static final ResourceLocation DIG_SITE_SENSE = id("dig_site_sense");
    /** Fraction of falling-block and suffocation damage ignored. */
    public static final ResourceLocation CAVE_IN_IMMUNITY = id("cave_in_immunity");

    // ---- Mining ----

    /** Fraction of vanilla's underwater and mid-air break-speed penalty that is cancelled. */
    public static final ResourceLocation MINING_PENALTY_IGNORE = id("mining_penalty_ignore");
    /** Chance a mined ore takes the ores connected to it as well. */
    public static final ResourceLocation VEIN_CHANCE = id("vein_chance");
    /** Chance an ore's drops come out of the ground already smelted. Silk touch is left alone. */
    public static final ResourceLocation AUTO_SMELT = id("auto_smelt");
    /** Extra ore-doubling chance at the bottom of the world, scaled by how deep the player is. */
    public static final ResourceLocation DEPTH_BONUS = id("depth_bonus");
    /** Chance an ore pays out several times over instead of once. */
    public static final ResourceLocation MOTHERLODE_CHANCE = id("motherlode_chance");
    /** Blocks added to the distance dropped items are drawn in from. */
    public static final ResourceLocation ITEM_MAGNET = id("item_magnet");
    /** Fraction of suffocation and falling-block damage ignored. */
    public static final ResourceLocation SPELUNKER_GUARD = id("spelunker_guard");
    /** Fraction added to movement speed, but only standing in the dark. */
    public static final ResourceLocation DARKNESS_SPEED = id("darkness_speed");
    /** Any amount at all: a tool one hit from breaking stops taking damage instead. */
    public static final ResourceLocation TOOL_LAST_STAND = id("tool_last_stand");

    // ---- Foraging ----

    /** Chance chopping a log brings down the logs connected above it. */
    public static final ResourceLocation TREE_FELLER = id("tree_feller");
    /** Chance a felled tree puts one of its own saplings back in the ground. */
    public static final ResourceLocation AUTO_REPLANT = id("auto_replant");
    /** Any amount at all: foraged drops go to the inventory rather than onto the floor. */
    public static final ResourceLocation DIRECT_HARVEST = id("direct_harvest");
    /** Multiplier on the chance leaves give up an apple, and a slice of that for a golden one. */
    public static final ResourceLocation LEAF_BOUNTY = id("leaf_bounty");
    /** Fraction added to the hunger and saturation foraged food restores. */
    public static final ResourceLocation FORAGE_NUTRITION = id("forage_nutrition");
    /** Chance a harvested crop comes straight back fully grown. */
    public static final ResourceLocation CROP_REGROW = id("crop_regrow");
    /** Extra growth ticks handed to crops near the player each second. */
    public static final ResourceLocation GROWTH_AURA = id("growth_aura");
    /** Chance chopping a log puts a point of durability back into the axe. */
    public static final ResourceLocation AXE_SELFREPAIR = id("axe_selfrepair");
    /** Chance eating something does not use it up. */
    public static final ResourceLocation FOOD_CONSERVATION = id("food_conservation");
    /**
     * Any amount at all: a crop broken with a hoe is replanted, paid for out of the player's own
     * seeds. Unlike {@link #CROP_REGROW} this costs a seed and puts the plant back unripe.
     */
    public static final ResourceLocation HOE_REPLANT = id("hoe_replant");

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
    /** Durability the held item mends per second while stood at a heat source. */
    public static final ResourceLocation HEAT_MENDING = id("heat_mending");
    /** Fraction of the anvil's prior-work penalty growth that is skipped. */
    public static final ResourceLocation PRIOR_WORK_REDUCTION = id("prior_work_reduction");
    /** Chance a piece of worn armor shrugs off its durability loss. */
    public static final ResourceLocation ARMOR_DURABILITY_SAVE = id("armor_durability_save");
    /** Fraction of an anvil's chance of chipping that is prevented. */
    public static final ResourceLocation ANVIL_PRESERVE = id("anvil_preserve");
    /** Chance an anvil repair also raises one enchantment the item already carries. */
    public static final ResourceLocation REFORGE_CHANCE = id("reforge_chance");
    /** Fraction of a target's armor ignored by swords and pikes. */
    public static final ResourceLocation ARMOR_PIERCE = id("armor_pierce");
    /** Chance something crafted comes out of the workbench already enchanted. */
    public static final ResourceLocation CRAFT_ENCHANT_CHANCE = id("craft_enchant_chance");
    /** Chance a craft yields a second copy. */
    public static final ResourceLocation CRAFT_DOUBLE_CHANCE = id("craft_double_chance");
    /** Chance collecting a smelted item gives an extra one. */
    public static final ResourceLocation SMELT_BONUS = id("smelt_bonus");

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

    // ---- Uniques ----

    /** Fraction added to the base swing of a weapon in {@code tommemod:unique}. */
    public static final ResourceLocation UNIQUE_DAMAGE = id("unique_damage");
    /** Fraction taken off the cooldown a unique's right-click ability puts on itself. */
    public static final ResourceLocation ABILITY_COOLDOWN = id("ability_cooldown");

    // ---- Melee ----

    /** Fraction added to a hit on something that had not noticed the attacker yet. */
    public static final ResourceLocation AMBUSH_DAMAGE = id("ambush_damage");
    /** Fraction of a swing's damage that its sweep carries to everything else caught. */
    public static final ResourceLocation SWEEP_DAMAGE = id("sweep_damage");
    /** Seconds added to a struck mob's own attack cooldown. Never applies to a boss. */
    public static final ResourceLocation STAGGER = id("stagger");
    /** Half-hearts per second a crit's wound keeps taking off, for a few seconds. */
    public static final ResourceLocation BLEED_DAMAGE = id("bleed_damage");
    /** Fraction added per consecutive hit landed on the same target without switching away. */
    public static final ResourceLocation COMBO_DAMAGE = id("combo_damage");
    /** Fraction added to attack speed for a few seconds after a kill. */
    public static final ResourceLocation KILL_MOMENTUM = id("kill_momentum");
    /** Fraction added against anything already below a third of its health. */
    public static final ResourceLocation EXECUTE_DAMAGE = id("execute_damage");
    /**
     * Fraction of the damage a target's armor was about to absorb that is handed back instead.
     *
     * Capped at what the armor actually stopped, so an armored target can never take more than a bare
     * one - the node closes the gap that armor opens rather than reversing it. Blades, daggers and
     * uniques only, by {@code tommemod:piercing_weapons}.
     */
    public static final ResourceLocation ARMOR_RECOVERY = id("armor_recovery");
    /** Fraction added to damage, scaled by how much of the attacker's own health is gone. */
    public static final ResourceLocation DESPERATION_DAMAGE = id("desperation_damage");
    /** Fraction of damage added, and of damage taken removed, while badly outnumbered. */
    public static final ResourceLocation OUTNUMBERED = id("outnumbered");

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
    /** Fraction added to a musket's damage. Its own key: a hitscan shot has no flight to measure. */
    public static final ResourceLocation MUSKET_DAMAGE = id("musket_damage");

    // ---- Archery ----

    /** Extra targets a fully drawn arrow passes through. */
    public static final ResourceLocation ARROW_PIERCE = id("arrow_pierce");
    /** Chance a shot does not use up its arrow. */
    public static final ResourceLocation ARROW_CONSERVATION = id("arrow_conservation");
    /** Chance a shot is followed a moment later by a second one, without interrupting the draw. */
    public static final ResourceLocation FOLLOW_UP_SHOT = id("follow_up_shot");
    /** Levels of knockback added to arrows, as the Punch enchantment counts them. */
    public static final ResourceLocation BOW_KNOCKBACK = id("bow_knockback");
    /** Seconds a struck target is lit up for. */
    public static final ResourceLocation HUNTERS_MARK = id("hunters_mark");
    /** Any amount at all: drawing a bow no longer slows the archer to a crawl. */
    public static final ResourceLocation DRAW_MOBILITY = id("draw_mobility");
    /** Any amount at all: a shot loosed early still flies as though fully drawn. */
    public static final ResourceLocation SNAP_SHOT = id("snap_shot");
    /**
     * Chance a shot produces a second arrow alongside it.
     *
     * Only the extra arrow is spread. The one that was aimed flies exactly where it was pointed, so a
     * long shot is never made worse by owning the node.
     */
    public static final ResourceLocation MULTISHOT_CHANCE = id("multishot_chance");
    /** Any amount at all: a shot loosed after standing still is always a critical. */
    public static final ResourceLocation AIMED_CRIT = id("aimed_crit");
    /** Chance a struck target is slowed. */
    public static final ResourceLocation ARROW_SLOW = id("arrow_slow");
    /** Chance a bow shrugs off the durability a shot would cost it. */
    public static final ResourceLocation BOW_DURABILITY_SAVE = id("bow_durability_save");
    /** Fraction added per extra second the draw is held past full. */
    public static final ResourceLocation OVERDRAW_DAMAGE = id("overdraw_damage");
    /** Seconds of blindness a headshot inflicts. */
    public static final ResourceLocation HEADSHOT_BLIND = id("headshot_blind");
    /** Fraction added against a target lit up by Hunter's Mark. */
    public static final ResourceLocation MARKED_DAMAGE = id("marked_damage");

    // ---- Marksmanship ----

    /** Fraction taken off a musket's reload. */
    public static final ResourceLocation MUSKET_RELOAD = id("musket_reload");
    /** Extra targets a bolt or a ball passes through. */
    public static final ResourceLocation BOLT_PIERCE = id("bolt_pierce");
    /** Fraction added against a target this marksman has already slowed with Suppressing Fire. */
    public static final ResourceLocation SUPPRESSED_DAMAGE = id("suppressed_damage");
    /** Chance a hit slows the target, to keep it at range. */
    public static final ResourceLocation SUPPRESS_SLOW = id("suppress_slow");
    /** Fraction added to a melee blow struck with a crossbow or musket in hand. */
    public static final ResourceLocation BAYONET_DAMAGE = id("bayonet_damage");
    /** Chance a crossbow looses a second bolt alongside the first. */
    public static final ResourceLocation CROSSBOW_MULTISHOT = id("crossbow_multishot");
    /**
     * Any amount at all: a holstered crossbow or musket loads itself.
     *
     * Three times as slow as reloading it by hand, so it is a convenience for the weapon you are not
     * currently using rather than a way to skip reloading the one you are.
     */
    public static final ResourceLocation HOLSTER_RELOAD = id("holster_reload");
    /** Blocks of splash a bolt or ball does on impact. */
    public static final ResourceLocation IMPACT_AREA = id("impact_area");
    /** Fraction added to a thrown trident, and taken off how long Loyalty takes to bring it home. */
    public static final ResourceLocation TRIDENT_MASTERY = id("trident_mastery");
    /** Fraction added to a shot at an unaware non-boss. */
    public static final ResourceLocation ASSASSINATE = id("assassinate");
    /** Any amount at all: a shot landed beyond forty blocks is always a critical. */
    public static final ResourceLocation DISTANCE_CRIT = id("distance_crit");

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
    /** Any amount at all: raising a shield no longer slows the player to a walk. */
    public static final ResourceLocation BLOCK_MOBILITY = id("block_mobility");
    /** Blocks of knockback, and seconds of stagger, dealt by the first swing after a block. */
    public static final ResourceLocation SHIELD_BASH = id("shield_bash");
    /** Fraction of knockback removed while blocking. Clamped well short of immunity. */
    public static final ResourceLocation BLOCK_KNOCKBACK_REDUCTION = id("block_knockback_reduction");
    /**
     * Fraction of damage removed per consecutive hit from the same attacker.
     *
     * Per attacker, so a crowd never earns the discount - the node pays for surviving one thing's
     * sustained attention rather than for being swarmed.
     */
    public static final ResourceLocation DAMAGE_FALLOFF = id("damage_falloff");
    /** Durability worn armor mends per second while out of combat. */
    public static final ResourceLocation ARMOR_REPAIR = id("armor_repair");
    /** Fraction of all incoming damage ignored while sneaking. */
    public static final ResourceLocation SNEAK_DEFENSE = id("sneak_defense");
    /** Fraction added to the first attack after a successful block. */
    public static final ResourceLocation BLOCK_CHARGE = id("block_charge");
    /** Fraction of damage removed from players sheltering behind this one's raised shield. */
    public static final ResourceLocation GUARD_ALLY = id("guard_ally");
    /** Any amount at all: a Thorns trigger costs the armor no durability. */
    public static final ResourceLocation THORNS_NO_WEAR = id("thorns_no_wear");
    /**
     * Any amount at all: a killing blow from a non-boss leaves the player on half a heart instead.
     *
     * Strictly after both totem checks, so a totem is always spent first and the two never overlap.
     */
    public static final ResourceLocation LAST_BREATH = id("last_breath");
    /** Absorption hearts grown back after fifteen seconds without dealing or taking a hit. */
    public static final ResourceLocation OUT_OF_COMBAT_ABSORPTION = id("out_of_combat_absorption");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(TommeMod.MOD_ID, path);
    }
}
