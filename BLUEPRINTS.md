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
| Left click, `Enter` | build it |
| `X` twice, looking at a site | cancel that construction (blocks put back, materials refunded) |
| `Esc`, `B` | unlock, or glide back down to where you were standing |

The same list is shown in the bottom-right corner while planning. Every key is rebindable under *Options > Controls > Finnigan's Mod - Blueprint Mode*.

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
| Stone Wall | Defense | 1 | tiles end to end |
| Gatehouse | Defense | 3 | |
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

## Testing

`gradlew runGameTestServer` builds every blueprint in a throwaway flat world with real Builder
Villagers (in all four rotations across the set) and checks the result block for block, and checks
that cancelling reverts the ground and that sites survive a save and reload. The tests live in
`gametest/BuilderGameTests.java` and never load outside a dev environment.
