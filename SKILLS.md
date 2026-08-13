# Tuning the skill trees

Everything about the trees is data. You do not need to touch Java, and you do not need to rebuild the
mod, to change how much XP an activity is worth, what a node costs, what it grants, or what it takes
to unlock it.

**Where the files are**

```
src/main/resources/data/tommemod/
├── skill_categories/          the three sidebar headings
│   ├── movement.json
│   ├── gathering.json
│   └── combat.json
└── skill_trees/               one file per skill
    ├── agility.json  riding.json  sailing.json  gliding.json
    ├── mining.json   excavation.json  foraging.json  husbandry.json  smithing.json
    └── melee.json    archery.json  defense.json  marksmanship.json  unarmed.json
```

**Applying a change.** Edit the file, then run `/reload` in game. The trees reload, every online
player's client is resent the definitions, and their bonuses are recomputed against the new values on
the spot. No restart, no relog.

If a file has a mistake in it, that one tree is skipped and the reason is written to the log
(`Skipping skill tree tommemod:agility: ...`). The other thirteen keep working.

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
| `damage_blocked` `damage_taken` | half-hearts |
| `kill` | 1 per kill (carries the victim) |
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
  `breath_retention` `sprint_speed`
- **Riding / Sailing / Gliding** — `mount_speed` `mount_damage_reduction` `rider_damage_reduction`
  `boat_speed` `glide_speed` `elytra_durability_save`
- **Gathering** — `mining_speed` `ore_double_drop` `excavation_double_drop` `foraging_double_drop`
  `tool_durability_save` `block_xp_bonus`
- **Husbandry / Smithing** — `breeding_cooldown_reduction` `twin_birth_chance` `animal_drop_bonus`
  `anvil_cost_reduction` `repair_efficiency`
- **Combat** — `melee_damage` `unarmed_damage` `ranged_damage` `crit_chance` `crit_damage`
  `lifesteal` `undead_damage` `unarmed_knockback`
- **Ranged** — `draw_speed` `arrow_velocity` `arrow_steadiness` `long_shot_damage` `headshot_damage`
- **Defence** — `shield_durability_save` `riposte` `damage_reduction` `blast_resistance`
  `fire_resistance` `projectile_resistance`

`description` is the tooltip line; `%s` is replaced by the formatted number. `format` is `percent`
(the default), `flat` or `decimal` — use `decimal` for anything that is not a percentage, or 2.0 max
health will read as "+200%".

---

## 4. What unlocks a node

Two mechanisms, and they stack.

### Parents

`"parents": ["sprint", "dexterity"]` draws the connecting lines *and* means "you need rank 1 in one
of these". **Several parents means any one of them** — a fork is a real choice, not two prerequisites
wearing a disguise. If you want a node that genuinely demands all of its parents, add
`"parent_mode": "all"` to it.

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

## 5. Layout

`x` and `y` place a node on a grid. `y` is depth (0 is the root, increasing downward), `x` is
horizontal and may be negative. The screen fits `x` from -3 to 3 without scrolling; deeper than about
four rows and the player scrolls, which is normal.

The stock trees all use the same 18-node shape, so they read consistently:

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

---

## 6. Testing without playing to it

```
/tommemod skill addxp <players> <skill> <amount>
/tommemod skill points <players> <skill> <amount>
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
