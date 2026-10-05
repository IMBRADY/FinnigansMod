# Blueprint mode and Builder Villagers

The Village Chief plans buildings from above; Builder Villagers put them up block by block.

## Playing it

**Builders.** The **Blueprint Stand** is the Builder's job block: place one in a village and an
unemployed villager claims it and becomes a Builder - one Builder per stand, so more stands, more
Builders. Crafted from three paper over three planks over two sticks:

```
P P P      P = paper
# # #      # = any planks
S   S      S = stick
```

**The stand's screen.** Right-clicking a stand shows the village's Builders and every building under
way with its progress (live, while open). Anyone can read it; only the village's Chief can press
*Enter Blueprint Mode* (an operator in Creative can plan any village, for testing). The camera glides
up into a free-flying view over the village; you are a spectator while you plan, with night vision,
and can fly about as far as the village reaches plus a margin.

**Placing.** The selected building follows the cursor as a see-through copy of its real blocks -
blue where it can go, red where it can't, with the reason shown at the bottom of the screen.

| Input | Does |
|---|---|
| Scroll, 1-9 | choose building |
| `E` | building catalog, with a turning 3D preview of each building |
| `R` (Shift+`R`, Shift+scroll) | rotate |
| `Page Up` / `Page Down` (Shift+arrows, Ctrl+scroll) | raise / lower |
| Right click | lock the building in place so you can fly round it |
| Arrow keys | nudge one block, relative to where you're looking (locks it) |
| Left click | build it |
| Left click held and dragged (Stone Wall) | lay a straight run of wall from where you pressed to the cursor; let go to build it |
| `X` twice, looking at a site | cancel that construction (blocks put back, materials refunded) |
| `X` twice, looking at a finished building | demolish it (materials refunded, ground put back) |
| `Esc`, `B` | unlock, or glide back down to where you were standing |

The same list is shown in the bottom-right corner while planning, and the building in hand - with
why it can't go where it is, if it can't - is shown just under the crosshair. Every key is rebindable under *Options > Controls > Finnigan's Mod - Blueprint Mode*.

**Building.** Confirming takes the cost from your inventory (nothing, if you came in from Creative)
and starts a construction site. Every Builder within range is called in at once and shared out
between the village's sites. They clear the ground top-down, fill any dip under the floor, raise the
shell, then add stairs, doors, torches and furniture. A Builder that can't reach its next block in a
couple of seconds teleports next to it; with nowhere to stand it places the block from where it is.
They carry what they're about to place, and work through the night. Sites are saved with the world
and pick up where they left off.

Sites in progress show as amber ghosts of their unbuilt blocks, with progress and crew size listed
on the right of the screen.

Placement is refused when it would destroy a chest, bed, job site or other block entity, sit over a
drop deeper than `blueprintMaxGroundGap`, clear out water, overlap another site, or fall outside the
village. Nothing is charged for a refused placement.

## The buildings

| Building | Category | Builders | Brings |
|---|---|---|---|
| Cottage | Homes | 1 | a bed |
| Family House | Homes | 2 | two beds |
| Stone Wall | Defense | 1 | wall (see *Village walls*) |
| Gatehouse | Defense | 3 | wall - the way in |
| Wall Corner (Convex) | Defense | 1 | wall - rounded outer corner |
| Wall Corner (Concave) | Defense | 1 | wall - inward-turning corner |
| Watchtower | Defense | 2 | |
| Barracks | Defense | 3 | four beds, grindstone (Weaponsmith) |
| Village Well | Civic | 1 | bell (meeting point) |
| Library | Civic | 2 | lectern (Librarian) |
| Chapel | Civic | 2 | brewing stand (Cleric), bell |
| Bank | Civic | 3 | |
| Observatory | Civic | 3 | cartography table (Cartographer) |
| Farm Plot | Farming | 1 | composter (Farmer) |
| Fletcher's Stall | Workshops | 1 | fletching table (Fletcher) |
| Smithy | Workshops | 2 | blast furnace (Armorer), grindstone, smithing table |

Beds and job blocks are real POIs, so buildings grow the village and recruit the squires.

## Village walls

Walls are built in blueprint mode, not bought at the Chief Desk any more. The pieces - Stone Wall,
Gatehouse and the two Wall Corners - snap together: bring one near the open end of a wall (built or
still going up) and it jumps onto it, turned to carry on the same way with its parapet on the same
side ("joins wall" under the crosshair). Away from any wall, a piece goes where you point, turned with
`R` as usual. Dragging a Stone Wall lays a straight run; a run started away from any wall lies along
the drag with its parapet facing away from the middle of the village. Convex corners round off the
village's outer corners; concave ones turn inwards.

A piece on its own sits at the middle height of the ground under it. A piece joining a wall follows
the ground too, but never steps more than one block from the piece it joins: where the land falls away
faster, it stays up and builders fill the ground in under it with stone. It only steps further when it
has to - when the dip is deeper than `wallMaxGroundGap`, or when keeping level would bury it more than
three blocks into a hillside.

Where two joined pieces end up at different heights, builders add stairs between their walkways, one
step per block of height (up to three), with the parapet filled in beside them. The ramp is built up
over the end of the lower wall; on a curved corner, where a long ramp won't fit, it's cut down into the
straight wall above instead. Gatehouses have no ramp - their towers have ladders. Demolishing a piece
takes its ramps and foundation back out.

Until the pieces close a loop, the village is still outlined by its beds and job sites, as before.
The moment walls and gatehouses make a complete ring, everything inside it becomes the village: the
Chief Desk map shows the walls (grey) and the ring's edge (green), raids are pushed outside it, and
it's what counts as in the village from then on. Break the ring (a piece knocked below 60%, or
demolished) and the village goes back to its POI outline. Villages walled with the old Chief Desk
upgrade keep that boundary.

Wall pieces have their own allowance of sites under way (`blueprintMaxWallSites`), separate from
`blueprintMaxActiveSites`.

## Demolishing

Look at a finished blueprint building in blueprint mode and press `X` twice. Its blocks come down
(only the ones still as the design put them - anything you've added is left alone), its floor and
foundation go back to what was there before, chests spill their contents, and what was paid comes
back - into the bank if the village has one, otherwise to you. Buildings recorded before this existed
get their design's price back and dirt where their floor was.

## Buildings with a purpose

These buildings do something for the village once they stand; they're listed in green in blueprint
mode and the catalog. A building only counts while most of it (60% of its blocks) is still standing -
knock it down and its effect stops until it's rebuilt.

- **Barracks** - musters a Warrior for each bed (four). Warriors no longer come from handing a villager
  a Halberd. Each Warrior has a bunk: it sleeps in its bed in shifts staggered across the day, so the
  same share of the garrison is always awake and each sleeps as long as an ordinary villager
  (`warriorSleepTicks`), healing as it sleeps. A target - or being hit - gets it up. Squires no longer
  equip Warriors directly; they leave kit in the chest beside each bed (one piece per outfitter per day,
  plus healing/strength potions and arrows kept stocked), and the Warrior takes what it needs by the
  same rules as before. A fallen Warrior is replaced at its bunk a day later (`warriorRespawnTicks`).
  Ordinary villagers are kept out of barracks beds.
- **Bank** - holds the village's wealth. Its **Village Vault** shows the emerald total and everything
  else stored; anyone can pay in (all their emeralds, or the stack in hand), only the Chief can take out
  (click for a stack, shift-click for all). While a Bank stands, blueprints, Chief Desk upgrades and the
  Chief's villager trades are paid from the bank first, then from the inventory (traded goods still go
  to the inventory). Cancelled builds refund into the bank.
- **Observatory** - each one widens the Chief Desk map by `observatoryMapBonusBlocks`.
- **Farm Plot** - open at both ends with a path through (villagers can't use fence gates). Farmers
  work every plot near them as well as the farmland round their composter. While a Bank stands, every
  `farmReplantsPerEmerald` crops they replant on a plot add an emerald to the village's wealth.
- **Walls** - see *Village walls*.

**Checking buildings.** The Chief Desk's *Buildings* tab lists every finished building, whether it's
standing and what it does. A building's job comes from its design as it is now, so one finished before
its design had a job does that job. *Check buildings* sweeps the village for blueprint buildings it has
no record of - ones finished before buildings were tracked - and records them, and musters Warriors for
any Barracks that never got them.

## Farmers

During working hours every Farmer goes from ripe crop to ripe crop on the farmland within
`farmerWorkRadiusBlocks` of its composter and on every Farm Plot nearby, harvesting each and replanting
it straight away from the harvest, and picks up any crops lying about as it goes. It keeps a reserve
of each crop (what villagers eat, share and breed with) and seed to replant. While the village has a
Bank, the rest is carried, and when the workday ends the Farmer takes it to the Bank's vault and pays
it in. Without a Bank, it all goes in the Farmer's inventory, and what doesn't fit is dropped.

## Warriors

Warriors put protecting the village's own first: a mob chasing or attacking a villager, an Elder, a
Warrior or the Warrior itself pulls it off anything that threatens nobody. A villager panicking from a
zombie or raider calls the village's Warriors over, as being hit does. No Warrior sleeps during a raid.
(Ordinary villagers staying by their beds through a raid is vanilla - they hide indoors.)

## Adding or changing a building

Each building is one file in `src/main/resources/data/tommemod/blueprints/`, listed by id in
`index.json` there (that order is the catalog order). They are read from the mod jar on both sides,
so the client can draw the ghost; changing one means rebuilding the mod. A file with a mistake is
skipped and the reason logged (`Skipping blueprint <id>: ...`) at startup - the rest still load.

```json
{
  "name": "Cottage",
  "description": "Shown in the catalog and on the HUD.",
  "category": "Homes",
  "icon": "minecraft:oak_door",
  "builders": 1,
  "foundation": "minecraft:cobblestone",
  "cost": { "minecraft:emerald": 6, "minecraft:oak_planks": 34 },
  "palette": { "A": "minecraft:cobblestone", "D": "minecraft:oak_door[facing=north,half=lower]" },
  "layers": [
    [ ".AAA.", ".AAA." ],
    [ ".ADA.", ".A.A." ]
  ]
}
```

- `layers` go bottom to top. Each is a list of rows running **north to south**, each row a string
  running **west to east**. Every layer must be the same size.
- **Layer 0 is ground level** - it replaces the top block of the terrain, so floors sit flush.
- **Design every building facing south** (front door on the last row). Rotation is done by the game,
  block states included - stairs, doors, logs and beds all turn with the building.
- Palette values are block states exactly as `/setblock` takes them. Any property left out gets its
  default. Fences, walls, panes and stairs connect themselves as they're placed.
- Reserved characters: `.` (or space) means *must be air* above layer 0 - builders clear terrain and
  trees from it - and *leave the ground alone* on layer 0. `~` leaves a cell untouched on any layer.
- `foundation` is what fills the gap under floor blocks when the ground dips (default dirt).
- `cost` is taken from the Chief's inventory when the building is placed and refunded on cancel.
- Wall pieces add `"purpose": "wall"` and `ports`: where another piece may join, as the edge cell at
  the middle of the joint, the way it faces and which side is outside there -
  `{"x": 8, "z": 1, "facing": "east", "outside": "south"}`. `"runs": true` lets a piece be dragged out
  in a straight run.

**Doors and beds** need both halves in the design (`half=lower`/`upper`, `part=foot`/`head`) - the
builders place the two together. **Torches, lanterns, ladders and bells** need the block they hang
from in the design too; builders place everything solid first, so order inside the file doesn't
matter.

The shipped designs are generated by `tools/gen_blueprints.py` (helpers for walls, gable and hip
roofs, weathered stone). Either edit the JSON by hand, or edit the script and re-run it - but not
both, since re-running overwrites the JSON.

## Settings

`run/config/tommemod-common.toml`, `[builderHub]`:

| Key | Default | |
|---|---|---|
| `builderTicksPerBlock` | 4 | ticks per block, per Builder (clearing takes half) |
| `builderSearchRadiusBlocks` | 96 | how far away Builders are called in from |
| `blueprintMaxGroundGap` | 6 | deepest dip under a floor that will be filled |
| `builderHubRegionPaddingBlocks` | 16 | how far past the village edge you may build |
| `blueprintMaxActiveSites` | 4 | sites one village can have under way |
| `blueprintFlightMarginBlocks` | 48 | how far past the village edge the camera can fly |
| `blueprintMaxWallSites` | 48 | wall pieces one village can have under way |
| `wallMaxGroundGap` | 24 | deepest dip under a wall piece that will be filled |

`[buildings]`:

| Key | Default | |
|---|---|---|
| `warriorSleepTicks` | 12000 | how long each Barracks Warrior sleeps per day |
| `warriorRespawnTicks` | 24000 | wait before a fallen Warrior is replaced |
| `warriorSleepHealIntervalTicks` | 40 | a sleeping Warrior heals 1 health this often |
| `observatoryMapBonusBlocks` | 64 | map radius added per Observatory |
| `farmReplantsPerEmerald` | 4 | Farm Plot replants per emerald paid into the bank |
| `farmerWorkRadiusBlocks` | 24 | how far from its composter a Farmer harvests |

## Testing

`gradlew runGameTestServer` builds every blueprint in a throwaway flat world with real Builder
Villagers (in all four rotations across the set) and checks the result block for block, and checks
that cancelling reverts the ground and that sites survive a save and reload. The tests live in
`gametest/BuilderGameTests.java` and never load outside a dev environment.
