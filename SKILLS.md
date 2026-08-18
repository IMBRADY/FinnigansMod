# Tuning the skill trees

Everything about the trees is data. You do not need to touch Java, and you do not need to rebuild the
mod, to change how much XP an activity is worth, what a node costs, what it grants, or what it takes
to unlock it.

**Where the files are**

```
src/main/resources/data/tommemod/
├── skill_categories/          the two sidebar headings
│   ├── general.json
│   └── class_based.json
└── skill_trees/               one file per skill
    ├── mining.json  excavation.json  foraging.json  husbandry.json  smithing.json
    ├── agility.json  riding.json  gliding.json  uniques.json
    └── vanguard.json  ranger.json  guardian.json
```

Adding a skill is adding a file. Deleting one is deleting a file - Sailing used to sit alongside
Riding and Gliding and its removal was exactly that, with the `distance/sail` action and the
`boat_speed` key left in place so a boat tree can be reinstated without touching Java. Melee, Archery,
Marksmanship and Defense went the same way when the classes replaced them: Melee and Defense became
Vanguard and Guardian, and Archery and Marksmanship were folded together into Ranger.

**Applying a change.** Edit the file, then run `/reload` in game. The trees reload, every online
player's client is resent the definitions, and their bonuses are recomputed against the new values on
the spot. No restart, no relog.

If a file has a mistake in it, that one tree is skipped and the reason is written to the log
(`Skipping skill tree tommemod:agility: ...`). The other eleven keep working.

**Worth knowing, because it is quiet:** a tree that has been skipped is not missing from the screen
in any obvious way, it simply is not there. A parent naming a node that does not exist is enough to
do it (see §4), and that is easy to cause by renaming or deleting a node without checking what
pointed at it. If a tree you have just edited has vanished, read the log before anything else.

---

## Quick reference — where do I change...

| I want to change | Edit | Where |
|---|---|---|
| XP for an activity | `xp` / `xp_per_unit` | that skill's `xp_sources` |
| Which skill an activity feeds | `action` | move the entry between skill files |
| How fast levels come | `xp_curve.base` | top of the skill file |
| Points per level | `points_per_level` | top of the skill file |
| Level cap | `max_level` | top of the skill file |
| What a node costs | `cost` | that node |
| How many times a node stacks | `max_rank` | that node |
| How strong a node is | `amount_per_rank` / `amounts` | that node's `effects` |
| What a node needs to unlock | `requirements` / `parents` | that node |
| Where a node sits on screen | `x` / `y` | that node |
| Global XP rate for everything | `xpMultiplier` | `run/config/tommemod-common.toml`, `[skills]` |

---

## 1. How much XP an activity gives

Each skill lists the actions it earns from. `xp` is a flat payment for the thing happening;
`xp_per_unit` is multiplied by *how much* of it happened (blocks travelled, damage dealt, durability
mended). A source can use either or both.

```json
"xp_sources": [
  { "action": "tommemod:distance/sprint", "xp_per_unit": 0.55 },
  { "action": "tommemod:block_broken", "xp": 25,
    "filters": [ { "type": "tommemod:block_tag", "tag": "forge:ores" } ] }
]
```

Sprinting a hundred blocks is 55 Agility XP. Breaking any ore is 25 Mining XP.

**One action can feed several skills.** Nothing stops you adding
`{"action": "tommemod:distance/sprint", "xp_per_unit": 0.1}` to `melee.json` if you want running to
count as combat conditioning. The action is broadcast; each skill decides what it is worth.

**Filters** narrow a source. Available types:

| Filter | Fields | Matches |
|---|---|---|
| `tommemod:block_tag` | `tag` | the broken block is in a block tag |
| `tommemod:block` | `block` or `blocks` | specific blocks |
| `tommemod:tool_tag` | `tag` | the held item is in an item tag |
| `tommemod:tool` | `item` or `items` | specific held items |
| `tommemod:empty_handed` | – | nothing in the main hand |
| `tommemod:item_tag` / `tommemod:item` | `tag` / `item` | the item acted on (repaired, crafted) |
| `tommemod:entity_type_tag` / `tommemod:entity_type` | `tag` / `entity` | the other party |
| `tommemod:min_amount` | `amount` | only if the action was at least this big |

Filters on one source are ANDed. For "either of these", write two sources.

### Every action you can draw XP from

These are fixed — they are what the mod's event handlers post. Adding a genuinely new one needs code.

| Action | Amount means |
|---|---|
| `distance/walk` `distance/sprint` `distance/swim` `distance/climb` `distance/fall` | blocks |
| `distance/glide` `distance/sail` `distance/ride` `distance/minecart` | blocks |
| `jump` | 1 per jump |
| `block_broken` | 1 per block (carries the block and tool) |
| `damage_dealt/melee` `damage_dealt/unarmed` | half-hearts |
| `damage_dealt/bow` `damage_dealt/crossbow` `damage_dealt/thrown` | half-hearts |
| `damage_dealt/musket` `damage_dealt/trident` | half-hearts |
| `damage_blocked` `damage_taken` | half-hearts |
| `kill` | 1 per kill, however landed (carries the victim) |
| `kill/melee` `kill/unarmed` `kill/bow` `kill/crossbow` | 1 per kill with that weapon |
| `kill/thrown` `kill/musket` `kill/trident` | 1 per kill with that weapon |
| `projectile_hit` | blocks the projectile flew |
| `animal_bred` `animal_tamed` `animal_fed` | 1 each (carries the animal) |
| `item_repaired` | durability points restored |
| `item_enchanted` | levels the table charged |
| `item_crafted` | stack size |

---

## 2. How fast levels arrive

```json
"max_level": 100,
"points_per_level": 1,
"xp_curve": { "type": "tommemod:polynomial", "base": 120, "exponent": 1.45 }
```

`polynomial` costs `base × level^exponent` to leave each level. Raising `base` makes the whole skill
slower by a flat factor; raising `exponent` makes the late levels disproportionately slower.

Two other shapes exist:

```json
{ "type": "tommemod:linear", "base": 100, "step": 50 }
{ "type": "tommemod:table", "levels": [50, 120, 300, 700] }
```

`table` uses the last entry for every level past the end of the list.

---

## 3. What a node costs and does

```json
{
  "id": "movement_speed",
  "title": "Movement Speed",
  "description": "Wasting less of every step.",
  "icon": "minecraft:leather_boots",
  "x": 0, "y": 1,
  "max_rank": 3,
  "cost": [1, 2, 3],
  "parents": ["agility"],
  "effects": [
    { "type": "tommemod:attribute", "attribute": "minecraft:generic.movement_speed",
      "operation": "multiply_base", "amount_per_rank": 0.04,
      "description": "%s movement speed" }
  ]
}
```

`cost` is per rank — this node costs 1 point for the first rank, 2 for the second, 3 for the third.
A single number (`"cost": 2`) means every rank costs the same. Ranks past the end of the list keep
the last price.

`amount_per_rank` is what each rank adds, so rank 3 above is +12%. For a node whose first rank should
be worth more than the rest, use `amounts` instead — the entries are increments and are summed:

```json
"amounts": [0.10, 0.05, 0.05]
```

### The two effect types

**`tommemod:attribute`** moves a real game attribute. Nothing else in the mod has to know about it.

```json
{ "type": "tommemod:attribute", "attribute": "minecraft:generic.max_health",
  "operation": "addition", "amount_per_rank": 2.0, "format": "decimal",
  "description": "%s max health" }
```

`operation` is `addition`, `multiply_base` or `multiply_total`. Usable attributes include
`minecraft:generic.` `max_health` `movement_speed` `attack_damage` `attack_speed` `armor`
`armor_toughness` `knockback_resistance` `luck` `attack_knockback`, and Forge's `forge:swim_speed`
`forge:entity_gravity` `forge:block_reach` `forge:entity_reach` `forge:step_height_addition`.

**`tommemod:bonus`** raises a named effect that a handler in the mod reads back.

```json
{ "type": "tommemod:bonus", "key": "tommemod:ore_double_drop",
  "amount_per_rank": 0.05, "description": "%s chance of a second ore drop" }
```

You can point any node at any key, split one key across several nodes, or stack several nodes onto
one — they sum. The full list of keys, with a comment on each explaining exactly what it does and
what unit it is in, is in
`src/main/java/net/finnigan/tommemod/skill/bonus/ModSkillBonuses.java`. In short:

- **Agility** — `jump_power` `fall_damage_reduction` `safe_fall_bonus` `exhaustion_reduction`
  `breath_retention` `sprint_speed` `sprint_acceleration` `underwater_vision` `jump_forward`
  `sustained_sprint_speed` `unhindered_stride` `fall_speed_cap`
- **Riding** — `mount_speed` `mount_damage_reduction` `rider_damage_reduction` `boat_speed`
  `mount_sustained_speed` `mount_step_height` `mount_jump_power` `mount_acceleration` `mount_health`
  `mounted_fall_damage_reduction` `mount_trample` `mount_regen` `mounted_melee_damage`
- **Gliding** — `glide_speed` `elytra_durability_save` `elytra_lift` `dive_speed` `turn_retention`
  `rocket_acceleration` `rocket_duration` `takeoff_burst` `elytra_fall_damage_reduction`
  `flight_collision_reduction` `elytra_repair` `altitude_speed` `storm_speed` `dive_damage`
  `landing_momentum` `flight_regen` `rocket_conservation` `dive_impact`
- **Gathering, shared** — `mining_speed` `ore_double_drop` `excavation_double_drop`
  `foraging_double_drop` `tool_durability_save` `block_xp_bonus`
- **Excavation** — `loose_ground_stride` `dig_momentum` `shovel_durability_save` `shovel_last_stand`
  `hardpan` `buried_find` `treasure_quality` `excavation_direct_harvest`
  `dig_site_sense` `cave_in_immunity`
- **Mining** — `mining_penalty_ignore` `vein_chance` `auto_smelt` `depth_bonus` `motherlode_chance`
  `item_magnet` `spelunker_guard` `darkness_speed` `tool_last_stand`
- **Foraging** — `tree_feller` `auto_replant` `direct_harvest` `leaf_bounty` `forage_nutrition`
  `crop_regrow` `growth_aura` `axe_selfrepair` `food_conservation` `hoe_replant`
- **Smithing** — `anvil_cost_reduction` `repair_efficiency` `heat_mending` `prior_work_reduction`
  `armor_durability_save` `anvil_preserve` `reforge_chance` `armor_pierce` `craft_enchant_chance`
  `craft_double_chance` `smelt_bonus`
- **Husbandry** — `breeding_cooldown_reduction` `twin_birth_chance` `animal_drop_bonus`
- **Uniques** — `unique_damage` `ability_cooldown`
- **Combat, shared** — `melee_damage` `unarmed_damage` `ranged_damage` `crit_chance` `crit_damage`
  `lifesteal` `undead_damage` `unarmed_knockback`
- **Vanguard** — `ambush_damage` `sweep_damage` `stagger` `bleed_damage` `combo_damage`
  `kill_momentum` `execute_damage` `armor_recovery` `desperation_damage` `outnumbered`, and for the
  subclasses `dash` `dodge_chance` `kill_stacks` `combo_cap_bonus` `max_health_damage`
  `ally_crit_mark`
- **Ranger** — `draw_speed` `arrow_velocity` `arrow_steadiness` `long_shot_damage` `headshot_damage`
  `musket_damage` `arrow_pierce` `arrow_conservation` `follow_up_shot` `bow_knockback` `hunters_mark`
  `draw_mobility` `snap_shot` `multishot_chance` `aimed_crit` `arrow_slow` `bow_durability_save`
  `overdraw_damage` `headshot_blind` `marked_damage` `musket_reload` `bolt_pierce`
  `suppressed_damage` `suppress_slow` `bayonet_damage` `crossbow_multishot` `holster_reload`
  `impact_area` `trident_mastery` `assassinate` `distance_crit`, and for the subclasses
  `extra_projectiles` `projectile_homing` `true_damage_share` `ability_power` `manasteal`
  `ally_cooldown_aura` `ultimate`
- **Guardian** — `shield_durability_save` `riposte` `damage_reduction` `blast_resistance`
  `fire_resistance` `projectile_resistance` `block_mobility` `shield_bash`
  `block_knockback_reduction` `damage_falloff` `armor_repair` `sneak_defense` `block_charge`
  `guard_ally` `thorns_no_wear` `last_breath` `out_of_combat_absorption`, and for the subclasses
  `ally_damage_share` `ally_fatal_absorb` `ally_regen` `healing_given` `healing_received`
  `bonded_ally` `ally_knockback_aura`

`boat_speed` is still read by its handler with no tree pointing at it, Sailing having been removed.
Point a node at it and boats are quick again. A good many of the Ranger keys are in the same position
after Archery and Marksmanship were folded into one tree — the handlers all still work, so a node
pointed at any of them starts paying immediately.

`ally_damage_share` is the one key with a second owner: the Allprot chestplate does the same job, and
both are served by `AllyDamageShare`, which takes the single best cover on offer rather than the sum.
Raising the numbers on Aegis and Sentinel will not stack them with the enchantment.

### Switches, not dials

Some keys only ask whether the player has *any* of them, so ranking one up does nothing. Give them
`max_rank: 1` and a description with no `%s` in it:

`sprint_acceleration` `underwater_vision` `unhindered_stride` `mount_acceleration` `direct_harvest`
`hoe_replant` `tool_last_stand` `loose_ground_stride` `hardpan` `excavation_direct_harvest`
`shovel_last_stand` `block_mobility` `draw_mobility` `snap_shot` `aimed_crit` `distance_crit`
`holster_reload` `thorns_no_wear` `last_breath` `dash` `ultimate` `ally_fatal_absorb`

### One rule worth keeping

Two of these were written the wrong way round first, and both times the bug was the same shape:
**a bonus that compounds every tick will always find a total that breaks the game.** Gliding once had
four nodes reducing gravity, which summed to 59% and left a fully-invested player floating with no way
down; Agility's Featherfall did the same thing through `forge:entity_gravity`, and did it while walking
about town as well as while falling. Both are now single effects with a ceiling — one lift node capped
at 15%, and a cap on falling speed rather than a cut to the force causing it.

If a new key would be read every tick and applied to velocity or to gravity, **give it exactly one node
and check what full investment comes to** before shipping it.

`description` is the tooltip line; `%s` is replaced by the formatted number. A description with no
`%s` in it is printed as written, which is what a node with nothing to quantify wants ("Night vision
while under water").

`format` decides how the number is written:

| `format` | 0.15 reads as | For |
|---|---|---|
| `percent` (default) | `+15%` | anything multiplicative |
| `flat` | `+0.2` | whole-number scales |
| `decimal` | `+0.15` | flat additions too small for rounding to survive |
| `percent_reduction` | `-15%` | bonuses that take something away |

Use `decimal` for anything that is not a percentage, or 2.0 max health will read as "+200%".

`percent_reduction` exists because the reduction keys store *the fraction they remove*, so the number
is positive while the effect is a subtraction. `fall_damage_reduction: 0.15` is fifteen percent less
fall damage, and under `percent` it announced itself as "+15% fall damage" — the exact opposite. Only
the sign it is printed with changes; the stored value and everything downstream of the tooltip are
untouched. Anything named `*_reduction`, plus `breath_retention`, wants this. Resistances do not —
"+20% blast resistance" already reads the right way round.

---

## 4. What unlocks a node

Two mechanisms, and they stack.

### Parents

`"parents": ["sprint", "dexterity"]` draws the connecting lines *and* means "you need rank 1 in one
of these". **Several parents means any one of them** — a fork is a real choice, not two prerequisites
wearing a disguise. If you want a node that genuinely demands all of its parents, add
`"parent_mode": "all"` to it. Every tree's capstone carries it, so the three branches feeding it read
as three requirements rather than as a choice between them.

**A parent naming a node that does not exist fails the whole file.** `Skill.validateParents` throws
and the tree is skipped — deliberately, because the alternative is a node carrying an implicit
"rank 1 in `<missing>`" that can never be satisfied, which in play is indistinguishable from a node
that is simply, permanently, silently locked. Rename a node and you must fix whatever pointed at it.

### Requirements

`requirements` is a list, and every entry must pass. Each entry becomes its own line in the node's
detail panel, ticked green or red with live progress.

```json
"requirements": [
  { "type": "tommemod:skill_level", "level": 25 },
  { "type": "tommemod:points_invested", "points": 12 }
]
```

| Type | Fields | Means |
|---|---|---|
| `tommemod:skill_level` | `level` | this skill is at least that level |
| `tommemod:node_rank` | `node`, `rank` | you own that many ranks of another node |
| `tommemod:points_invested` | `points` | you have spent that many points in this tree |
| `tommemod:all_nodes` | `rank` (default 1) | every *other* node in this tree is at that rank |
| `tommemod:and` / `tommemod:or` | `children` | composites |
| `tommemod:not` | `child` | negation |

Every one of these takes an optional `"skill"` field. Leave it out and it means this tree; name
another skill and the requirement becomes cross-tree. That is how Gliding's capstone demands Agility
15:

```json
"requirements": [
  { "type": "tommemod:all_nodes", "rank": 1 },
  { "type": "tommemod:skill_level", "level": 50 },
  { "type": "tommemod:skill_level", "skill": "tommemod:agility", "level": 15 }
]
```

Prefer a flat list to a single `and` — it reads as several tickable lines rather than one long
sentence. Use `and`/`or` when you need them nested inside something else.

**The capstones** all carry `{"type": "tommemod:all_nodes", "rank": 1}`, so the last node in a tree
needs every other node in that tree claimed first. It counts against the tree as loaded, so if you
add a node to Agility, Windrunner starts requiring that one too without you editing it. Change
`"rank": 1` to `"rank": 2` if you would rather the capstone demand nodes be ranked up, not merely
bought.

---

## 4a. Classes, and what makes one

Class-Based is an ordinary category with one extra line in it:

```json
{ "display_name": "Class-Based", "sort_order": 1, "exclusive": true }
```

`exclusive` is what turns three trees into a choice. Buying a node in one of them commits the player
to it, and every other tree under the same heading is closed off from that moment — the Unlock button
greys out, the detail panel says which class they took instead, and the server refuses the purchase
even if a client asks anyway. Nothing names Vanguard, Ranger or Guardian in Java; moving a tree into
or out of the category is all it takes to add or remove a class.

**Committing is buying, not earning.** Experience keeps accruing in all three from ordinary play —
swinging a sword is Vanguard experience whether or not the player means to be one. What locks the
choice in is spending a point, because that is the first thing the player actually decided.

**Subclasses** are not a mechanism. They are two nodes at the same depth, each carrying a
`tommemod:not` requirement naming the other:

```json
"requirements": [
  { "type": "tommemod:skill_level", "level": 20 },
  { "type": "tommemod:not", "child": { "type": "tommemod:node_rank", "node": "dreadnought", "rank": 1 } }
]
```

That is the whole of it. A third subclass is a third node with a `not` for each of the other two.

**The reset** wipes every tree under the exclusive heading back to level 1 — not only the committed
one, since the others have been quietly accruing experience the whole time and leaving them would hand
the player their second class most of the way built. Half of what was banked in the class they
actually took (`SkillService.CLASS_RESET_REFUND`) becomes a credit, which is paid into the next class
the moment they buy its root node. The credit is not spendable on General trees and does not expire.

Reachable from the Reset Class button on the committed tree, which asks first and shows both numbers,
and from `/tommemod skill resetclass <players>` — the same code path, so testing one tests the other.

---

## 5. Layout

`x` and `y` place a node on a grid. `y` is depth (0 is the root, increasing downward), `x` is
horizontal and may be negative. The screen fits `x` from -3 to 3 without scrolling; deeper than about
four rows and the player scrolls, which is normal.

The General trees all use the same 18-node shape, so they read consistently:

```
y=0                        root(0,0)
y=1          A(-2,1)        B(0,1)        C(2,1)
y=2   A1(-3,2) A2(-1,2)     B1(0,2)    C1(1,2) C2(3,2)
y=3   A3(-3,3) A4(-1,3)     B2(0,3)    C3(1,3) C4(3,3)
y=4       A5(-2,4)          B3(0,4)       C5(2,4)
y=5                      capstone(0,5)
```

Fully maxing one tree costs 100 points; 100 levels earns 99. That is deliberate — you can very nearly
complete one tree and never two.

The three class trees are a different shape, because they have to say something the General ones do
not: where the fork is. They run to `y=7` rather than `y=5`, with a shared trunk down to a gate node
at `y=3`, the two subclasses side by side at `y=4`, and each subclass's own four nodes below it:

```
y=0                      root(0,0)
y=1        A(-2,1)        B(0,1)        C(2,1)
y=2  A1(-3,2) A2(-1,2)  B1(1,2)      C1(3,2)
y=3                      gate(0,3)
y=4          left(-2,4)              right(2,4)
y=5   L1(-3,5) L2(-1,5)              R1(1,5) R2(3,5)
y=6          L3(-2,6)                  R3(2,6)
y=7          L4(-2,7)                  R4(2,7)
```

Because a player only ever goes down one side, the reachable half of a class tree is about fourteen
nodes — deliberately smaller than a General tree, and cheaper to finish.

---

## 6. Testing without playing to it

```
/tommemod skill addxp <players> <skill> <amount>
/tommemod skill points <players> <skill> <amount>
/tommemod skill resetclass <players>
/tommemod skill respec <players> <skill>
/tommemod skill query  <players>
```

`respec` refunds every point spent in a tree and keeps the levels that earned them. Note that
`points` and `addxp` grant currency but do **not** bypass a node's requirements — buying a node
always goes through the same check the game uses, so testing a tree tests the tree.

## 7. Global rate

`run/config/tommemod-common.toml`:

```toml
[skills]
	xpMultiplier = 1.0
```

Multiplies every award in every skill. Raise it to shorten the grind across the board without
touching fourteen files.
