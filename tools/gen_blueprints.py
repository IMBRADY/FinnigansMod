"""Generates data/tommemod/blueprints/*.json for Finnigan's Mod.

    python tools/gen_blueprints.py .  [path/to/extracted/assets/minecraft/blockstates]

Overwrites every shipped blueprint, so hand edits to those files are lost - edit here instead, or
stop using this and edit the JSON. The optional second argument is vanilla's blockstates folder
(unzip it from client-extra.jar in the ForgeGradle cache); with it, every block state is checked
against vanilla before writing. The game re-checks them all at startup regardless.

Coordinates: x west->east, y up (0 = ground level, replaces the top terrain block), z north->south.
The front door of every design faces SOUTH (max z); the game rotates designs, never the author.
"""
import json, math, os, re, sys, hashlib

ROOT = sys.argv[1]
ASSETS = sys.argv[2] if len(sys.argv) > 2 else None
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "tommemod", "blueprints")
os.makedirs(OUT, exist_ok=True)

KEEP = "~"
CLEAR = None
POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#@$%&*+=-!?<>/|^:;,_'\"(){}[]"

warnings = []


def validate(state):
    m = re.fullmatch(r"(?:minecraft:)?([a-z0-9_]+)(?:\[(.*)\])?", state)
    if not m:
        raise ValueError("bad state " + state)
    block, props = m.group(1), m.group(2)
    if ASSETS is None:
        return
    path = os.path.join(ASSETS, block + ".json")
    if not os.path.exists(path):
        raise ValueError("unknown block " + block)
    text = open(path).read()
    if props:
        for kv in props.split(","):
            k, v = kv.split("=")
            if (k + "=" + v) not in text and ('"' + k + '": "' + v + '"') not in text and not re.search(
                    '"' + k + '": "[^"]*\\b' + v + '\\b', text):
                warnings.append(f"{state}: {k}={v} not seen in blockstate file (may be fine)")


class Design:
    def __init__(self, id, name, W, H, D, desc, category, icon, builders, emeralds, foundation="minecraft:cobblestone"):
        self.id, self.name, self.W, self.H, self.D = id, name, W, H, D
        self.desc, self.category, self.icon, self.builders, self.emeralds = desc, category, icon, builders, emeralds
        self.foundation = foundation
        self.g = [[[CLEAR for _ in range(W)] for _ in range(D)] for _ in range(H)]

    def set(self, x, y, z, st):
        if not (0 <= x < self.W and 0 <= y < self.H and 0 <= z < self.D):
            raise IndexError(f"{self.id}: ({x},{y},{z}) outside {self.W}x{self.H}x{self.D}")
        if st not in (CLEAR, KEEP) and not st.startswith("minecraft:"):
            st = "minecraft:" + st
        self.g[y][z][x] = st

    def get(self, x, y, z):
        return self.g[y][z][x]

    def fill(self, x0, y0, z0, x1, y1, z1, st):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.set(x, y, z, st(x, y, z) if callable(st) else st)

    def ring(self, x0, z0, x1, z1, y0, y1, st):
        """Perimeter of a rectangle, for y0..y1."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        self.set(x, y, z, st(x, y, z) if callable(st) else st)

    def corners(self, x0, z0, x1, z1, y0, y1, st):
        for y in range(y0, y1 + 1):
            for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
                self.set(x, y, z, st)

    def door(self, x, y, z, wood, facing, hinge="left"):
        self.set(x, y, z, f"{wood}_door[facing={facing},half=lower,hinge={hinge},open=false]")
        self.set(x, y + 1, z, f"{wood}_door[facing={facing},half=upper,hinge={hinge},open=false]")

    def bed(self, x, y, z, color, facing):
        """(x,z) is the foot; the head is one step toward `facing`."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        self.set(x, y, z, f"{color}_bed[facing={facing},part=foot]")
        self.set(x + dx, y, z + dz, f"{color}_bed[facing={facing},part=head]")

    def gable_x(self, x0, x1, z0, z1, y0, stair, ridge, gable=None, overhang=1):
        """Roof with its ridge running along X over the box x0..x1, z0..z1 (the walls), starting at y0.
        Slopes rise from both long sides; the ends (x0 and x1) are filled to the slope with `gable`."""
        xa, xb = x0 - overhang, x1 + overhang
        n, s = z0 - overhang, z1 + overhang
        y = y0
        while n < s:
            for x in range(xa, xb + 1):
                self.set(x, y, n, f"{stair}[facing=south,half=bottom]")
                self.set(x, y, s, f"{stair}[facing=north,half=bottom]")
            if gable:
                for x in (x0, x1):
                    for z in range(max(n + 1, z0), min(s - 1, z1) + 1):
                        self.set(x, y, z, gable)
            n += 1
            s -= 1
            y += 1
        if n == s:
            for x in range(xa, xb + 1):
                self.set(x, y, n, ridge)
            y += 1
        return y  # first free layer above the roof

    def gable_z(self, z0, z1, x0, x1, y0, stair, ridge, gable=None, overhang=1):
        """As gable_x, with the ridge running along Z."""
        za, zb = z0 - overhang, z1 + overhang
        w, e = x0 - overhang, x1 + overhang
        y = y0
        while w < e:
            for z in range(za, zb + 1):
                self.set(w, y, z, f"{stair}[facing=east,half=bottom]")
                self.set(e, y, z, f"{stair}[facing=west,half=bottom]")
            if gable:
                for z in (z0, z1):
                    for x in range(max(w + 1, x0), min(e - 1, x1) + 1):
                        self.set(x, y, z, gable)
            w += 1
            e -= 1
            y += 1
        if w == e:
            for z in range(za, zb + 1):
                self.set(w, y, z, ridge)
            y += 1
        return y

    def hip(self, x0, z0, x1, z1, y0, stair, cap):
        """Pyramid roof over x0..x1, z0..z1 (inclusive, already including any overhang)."""
        y = y0
        while x0 < x1 and z0 < z1:
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, f"{stair}[facing=south,half=bottom]")
                self.set(x, y, z1, f"{stair}[facing=north,half=bottom]")
            for z in range(z0 + 1, z1):
                self.set(x0, y, z, f"{stair}[facing=east,half=bottom]")
                self.set(x1, y, z, f"{stair}[facing=west,half=bottom]")
            x0, z0, x1, z1, y = x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 1
        if x0 <= x1 and z0 <= z1:
            self.fill(x0, y, z0, x1, y, z1, cap)
            y += 1
        return y

    # ---- export ----
    def export(self):
        chars = {}
        palette = {}
        it = iter(POOL)
        counts = {}
        for y in range(self.H):
            for z in range(self.D):
                for x in range(self.W):
                    st = self.g[y][z][x]
                    if st in (CLEAR, KEEP):
                        continue
                    if st not in chars:
                        validate(st)
                        c = next(it)
                        chars[st] = c
                        palette[c] = st
                    counts[st] = counts.get(st, 0) + 1
        layers = []
        for y in range(self.H):
            rows = []
            for z in range(self.D):
                row = ""
                for x in range(self.W):
                    st = self.g[y][z][x]
                    row += "~" if st == KEEP else "." if st is CLEAR else chars[st]
                rows.append(row)
            layers.append(rows)
        cost = {"minecraft:emerald": self.emeralds}
        for item, n in material_cost(counts):
            cost[item] = n
        data = {
            "name": self.name,
            "description": self.desc,
            "category": self.category,
            "icon": self.icon,
            "builders": self.builders,
            "foundation": self.foundation,
            "cost": cost,
            "palette": palette,
            "layers": layers,
        }
        with open(os.path.join(OUT, self.id + ".json"), "w") as f:
            f.write(to_json(data))
        return counts


def to_json(data):
    """Pretty JSON with each layer row on its own line, so designs read like floor plans."""
    out = ["{"]
    keys = list(data.keys())
    for i, k in enumerate(keys):
        comma = "," if i < len(keys) - 1 else ""
        v = data[k]
        if k == "layers":
            out.append('  "layers": [')
            for li, layer in enumerate(v):
                out.append("    [")
                for ri, row in enumerate(layer):
                    out.append("      " + json.dumps(row) + ("," if ri < len(layer) - 1 else ""))
                out.append("    ]" + ("," if li < len(v) - 1 else ""))
            out.append("  ]" + comma)
        elif isinstance(v, dict):
            out.append(f'  "{k}": {{')
            items = list(v.items())
            for j, (kk, vv) in enumerate(items):
                out.append(f"    {json.dumps(kk)}: {json.dumps(vv)}" + ("," if j < len(items) - 1 else ""))
            out.append("  }" + comma)
        else:
            out.append(f"  {json.dumps(k)}: {json.dumps(v)}{comma}")
    out.append("}")
    return "\n".join(out) + "\n"


# Families: what the village actually pays for. Builders shape and cut it themselves, so the bill is
# about half of the raw block count, in base materials.
FAMILIES = [
    (r"^(oak)_(log|wood)", "minecraft:oak_log"),
    (r"^(spruce)_(log|wood)", "minecraft:spruce_log"),
    (r"^(dark_oak)_(log|wood)", "minecraft:dark_oak_log"),
    (r"^(birch)_(log|wood)", "minecraft:birch_log"),
    (r"^oak_", "minecraft:oak_planks"),
    (r"^spruce_", "minecraft:spruce_planks"),
    (r"^dark_oak_", "minecraft:dark_oak_planks"),
    (r"^birch_", "minecraft:birch_planks"),
    (r"(stone_brick|chiseled_stone|cracked_stone)", "minecraft:stone_bricks"),
    (r"cobblestone", "minecraft:cobblestone"),
    (r"(polished_andesite|^andesite)", "minecraft:andesite"),
    (r"(smooth_stone|^stone_slab|^stone$)", "minecraft:stone"),
    (r"glass", "minecraft:glass"),
    (r"(white_terracotta|^terracotta)", "minecraft:terracotta"),
    (r"_wool$|_carpet$", "minecraft:white_wool"),
    (r"bookshelf", "minecraft:bookshelf"),
    (r"lantern", "minecraft:lantern"),
    (r"torch", "minecraft:torch"),
    (r"^gold_block", "minecraft:gold_ingot"),
    (r"^iron_bars", "minecraft:iron_ingot"),
]


def material_cost(counts):
    fam = {}
    for st, n in counts.items():
        block = re.sub(r"\[.*", "", st).replace("minecraft:", "")
        if re.search(r"_door", block) and "half=upper" in st:
            continue
        for pat, item in FAMILIES:
            if re.search(pat, block):
                fam[item] = fam.get(item, 0) + n
                break
    out = []
    for item, n in sorted(fam.items(), key=lambda kv: -kv[1]):
        scale = 0.5 if n >= 8 else 1.0
        if item == "minecraft:gold_ingot":
            n, scale = n * 9, 0.5
        out.append((item, max(1, math.ceil(n * scale))))
    return out[:5]


def noise(x, y, z, salt=0):
    h = hashlib.md5(f"{x},{y},{z},{salt}".encode()).digest()
    return h[0] / 255.0


def weathered(base, mossy, cracked, p_moss=0.18, p_crack=0.12, salt=0):
    def f(x, y, z):
        r = noise(x, y, z, salt)
        if r < p_moss:
            return mossy
        if r < p_moss + p_crack:
            return cracked
        return base
    return f


designs = []

# =====================================================================================
# Cottage - the starter home. 7x6 timber-framed body, gabled spruce roof, chimney with a
# smoking campfire, porch path, flower beds, and a bed (grows the village).
# =====================================================================================
d = Design("cottage", "Cottage", 9, 10, 9,
           "A snug timber-framed home with a hearth and a bed for one villager.",
           "Homes", "minecraft:oak_door", 1, 6)
X0, X1, Z0, Z1 = 1, 7, 1, 6
d.fill(X0, 0, Z0, X1, 0, Z1, "cobblestone")
d.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce_planks")
d.ring(X0, Z0, X1, Z1, 1, 3, "oak_planks")
d.corners(X0, Z0, X1, Z1, 1, 3, "oak_log[axis=y]")
for x in range(X0, X1 + 1):
    d.set(x, 4, Z0, "spruce_log[axis=x]")
    d.set(x, 4, Z1, "spruce_log[axis=x]")
# windows
for z in (3, 4):
    d.set(X0, 2, z, "glass_pane")
    d.set(X1, 2, z, "glass_pane")
for x in (2, 6):
    d.set(x, 2, Z1, "glass_pane")
d.set(4, 2, Z0, "glass_pane")
d.door(4, 1, Z1, "oak", "north")
d.gable_x(X0, X1, Z0, Z1, 4, "spruce_stairs", "spruce_planks", gable="oak_planks")
# gable window
d.set(X0, 5, 3, "glass_pane"); d.set(X0, 5, 4, "glass_pane")
d.set(X1, 5, 3, "glass_pane"); d.set(X1, 5, 4, "glass_pane")
# chimney + hearth
for y in range(2, 9):
    d.set(6, y, 2, "cobblestone")
d.set(6, 1, 2, "furnace[facing=south,lit=false]")
d.set(6, 9, 2, "campfire[facing=north,lit=true,signal_fire=false]")
# interior
d.bed(2, 1, 3, "red", "north")
d.set(2, 1, 5, "crafting_table")
d.set(6, 1, 5, "chest[facing=west,type=single]")
d.set(5, 1, 2, "barrel[facing=up]")
d.set(4, 1, 3, "red_carpet"); d.set(4, 1, 4, "red_carpet")
d.set(4, 3, 2, "wall_torch[facing=south]")
d.set(2, 3, 4, "wall_torch[facing=east]")
# porch: path, lanterns on posts, flower beds
d.set(4, 0, 7, "dirt_path"); d.set(4, 0, 8, "dirt_path")
for x in (2, 6):
    d.set(x, 0, 7, "grass_block")
d.set(2, 1, 7, "poppy"); d.set(6, 1, 7, "dandelion")
d.set(3, 1, 7, "oak_fence"); d.set(5, 1, 7, "oak_fence")
d.set(3, 2, 7, "lantern[hanging=false]"); d.set(5, 2, 7, "lantern[hanging=false]")
designs.append(d)

# =====================================================================================
# Family House - two storeys: cobblestone ground floor, timber-framed upper floor, ladder,
# two beds upstairs, kitchen and storage downstairs, covered front porch.
# =====================================================================================
d = Design("family_house", "Family House", 11, 14, 10,
           "A two-storey timber-framed house with a kitchen below and two bedrooms above.",
           "Homes", "minecraft:red_bed", 2, 14)
X0, X1, Z0, Z1 = 1, 9, 1, 7
d.fill(X0, 0, Z0, X1, 0, Z1, "cobblestone")
d.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "oak_planks")
d.ring(X0, Z0, X1, Z1, 1, 3, weathered("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.2, 0))
d.corners(X0, Z0, X1, Z1, 1, 8, "dark_oak_log[axis=y]")
# floor of the upper storey + beams
d.fill(X0, 4, Z0, X1, 4, Z1, "spruce_planks")
for x in range(X0, X1 + 1):
    d.set(x, 4, Z0, "dark_oak_log[axis=x]"); d.set(x, 4, Z1, "dark_oak_log[axis=x]")
for z in range(Z0 + 1, Z1):
    d.set(X0, 4, z, "dark_oak_log[axis=z]"); d.set(X1, 4, z, "dark_oak_log[axis=z]")
# upper walls: white plaster panels between dark timber
d.ring(X0, Z0, X1, Z1, 5, 7, "white_terracotta")
for x in (3, 5, 7):
    for y in (5, 6, 7):
        d.set(x, y, Z0, "dark_oak_log[axis=y]"); d.set(x, y, Z1, "dark_oak_log[axis=y]")
for z in (4,):
    for y in (5, 6, 7):
        d.set(X0, y, z, "dark_oak_log[axis=y]"); d.set(X1, y, z, "dark_oak_log[axis=y]")
for x in range(X0, X1 + 1):
    d.set(x, 8, Z0, "dark_oak_log[axis=x]"); d.set(x, 8, Z1, "dark_oak_log[axis=x]")
# windows
for x in (3, 7):
    d.set(x, 2, Z1, "glass_pane"); d.set(x, 2, Z0, "glass_pane")
for z in (3, 5):
    d.set(X0, 2, z, "glass_pane"); d.set(X1, 2, z, "glass_pane")
for x in (2, 4, 6, 8):
    d.set(x, 6, Z1, "glass_pane"); d.set(x, 6, Z0, "glass_pane")
for z in (2, 3, 5, 6):
    d.set(X0, 6, z, "glass_pane"); d.set(X1, 6, z, "glass_pane")
d.door(5, 1, Z1, "spruce", "north")
top = d.gable_x(X0, X1, Z0, Z1, 8, "dark_oak_stairs", "dark_oak_slab[type=bottom]", gable="white_terracotta")
# ladder up the inside of the north wall, through the floor
for y in range(1, 5):
    d.set(8, y, 2, "ladder[facing=south]")
# ground floor: kitchen + storage
d.set(2, 1, 2, "smoker[facing=south,lit=false]")
d.set(3, 1, 2, "crafting_table")
d.set(4, 1, 2, "barrel[facing=up]")
d.set(2, 1, 6, "chest[facing=east,type=single]")
d.set(2, 1, 5, "chest[facing=east,type=single]")
d.set(6, 1, 4, "oak_fence"); d.set(6, 2, 4, "oak_pressure_plate")
d.set(5, 1, 4, "oak_stairs[facing=east,half=bottom]")
d.set(7, 1, 4, "oak_stairs[facing=west,half=bottom]")
d.set(5, 3, 2, "wall_torch[facing=south]")
d.set(X1 - 1, 3, 5, "wall_torch[facing=west]")
# upstairs: two bedrooms
d.bed(2, 5, 3, "blue", "north")
d.bed(4, 5, 3, "red", "north")
d.set(3, 5, 2, "flower_pot")
d.set(6, 5, 2, "bookshelf"); d.set(7, 5, 2, "bookshelf")
d.set(6, 5, 6, "chest[facing=north,type=single]")
d.set(2, 5, 6, "potted_azure_bluet")
d.set(5, 7, 4, "lantern[hanging=true]")
d.fill(3, 5, 4, 6, 5, 5, "white_carpet")
d.set(5, 5, 4, CLEAR)
# hanging lantern needs support: it hangs from the roof rafters' gable fill above
d.set(5, 8, 4, "spruce_planks")
# porch
for x in (3, 7):
    for y in (1, 2, 3):
        d.set(x, y, 8, "spruce_fence")
for x in range(2, 9):
    d.set(x, 4, 8, "spruce_slab[type=bottom]")
d.set(4, 0, 8, "dirt_path"); d.set(5, 0, 8, "dirt_path"); d.set(6, 0, 8, "dirt_path")
d.set(5, 0, 9, "dirt_path")
d.set(3, 3, 8, CLEAR); d.set(7, 3, 8, CLEAR)
d.set(3, 3, 8, "lantern[hanging=true]"); d.set(7, 3, 8, "lantern[hanging=true]")
designs.append(d)

# =====================================================================================
# Stone Wall - a fortification segment. Thick weathered stone bricks, corbelled walkway on the
# inside, crenellated parapet, a ladder up, lanterns. Tiles end to end.
# =====================================================================================
d = Design("stone_wall", "Stone Wall", 9, 8, 3,
           "A crenellated stone wall segment with a walkway and ladder. Place several end to end.",
           "Defense", "minecraft:stone_bricks", 1, 4, foundation="minecraft:stone_bricks")
stone = weathered("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", salt=1)
d.fill(0, 0, 0, 8, 0, 2, "stone_bricks")
d.fill(0, 1, 1, 8, 5, 2, stone)
for x in range(9):
    d.set(x, 4, 0, "stone_brick_stairs[facing=south,half=top]")
    d.set(x, 5, 1, CLEAR)
    if x % 2 == 0:
        d.set(x, 6, 2, stone(x, 6, 2))
d.set(0, 7, 2, "lantern[hanging=false]")
d.set(8, 7, 2, "lantern[hanging=false]")
for y in range(1, 5):
    d.set(4, y, 0, "ladder[facing=north]")
d.set(2, 3, 0, "wall_torch[facing=north]")
d.set(6, 3, 0, "wall_torch[facing=north]")
designs.append(d)

# =====================================================================================
# Gatehouse - two hollow towers flanking an arched passage, bridge deck with parapets,
# hanging lanterns in the passage, torches on the facade.
# =====================================================================================
d = Design("gatehouse", "Gatehouse", 9, 10, 6,
           "Twin towers over an arched gate - the way in through your walls.",
           "Defense", "minecraft:iron_bars", 3, 24, foundation="minecraft:stone_bricks")
stone = weathered("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", salt=2)
d.fill(0, 0, 0, 8, 0, 4, "stone_bricks")
d.fill(3, 0, 0, 5, 0, 5, "cobblestone")
for tx in (0, 6):
    d.ring(tx, 0, tx + 2, 4, 1, 7, stone)
    for z in (1, 2, 3):
        for y in range(1, 8):
            d.set(tx + 1, y, z, CLEAR)
    d.fill(tx, 8, 0, tx + 2, 8, 4, "stone_bricks")
    for x in range(tx, tx + 3):
        for z in (0, 4):
            if (x + z) % 2 == 0:
                d.set(x, 9, z, stone(x, 9, z))
    d.set(tx, 9, 2, stone(tx, 9, 2)); d.set(tx + 2, 9, 2, stone(tx + 2, 9, 2))
    for y in range(1, 9):
        d.set(tx + 1, y, 1, "ladder[facing=south]")
    d.set(tx + 1, 5, 4, CLEAR)  # arrow slit
    d.set(tx + 1, 6, 4, CLEAR)
# doorways from the passage into each tower
for y in (1, 2, 5, 6):
    d.set(2, y, 2, CLEAR); d.set(6, y, 2, CLEAR)
# bridge over the passage
d.fill(3, 4, 0, 5, 4, 4, "stone_bricks")
for z in range(0, 5):
    d.set(3, 3, z, "stone_brick_stairs[facing=west,half=top]")
    d.set(5, 3, z, "stone_brick_stairs[facing=east,half=top]")
for x in (3, 4, 5):
    d.set(x, 5, 0, stone(x, 5, 0)); d.set(x, 5, 4, stone(x, 5, 4))
for x in (3, 5):
    d.set(x, 6, 0, stone(x, 6, 0)); d.set(x, 6, 4, stone(x, 6, 4))
d.set(4, 3, 1, "lantern[hanging=true]")
d.set(4, 3, 3, "lantern[hanging=true]")
d.set(4, 3, 0, "iron_bars"); d.set(4, 3, 4, "iron_bars")
for tx in (0, 8):
    d.set(tx, 3, 5, "wall_torch[facing=south]")
d.set(2, 3, 5, "wall_torch[facing=south]"); d.set(6, 3, 5, "wall_torch[facing=south]")
d.fill(3, 0, 5, 5, 0, 5, "dirt_path")
designs.append(d)

# =====================================================================================
# Watchtower - stone and timber tower, ladder to a railed lookout with a hip roof.
# =====================================================================================
d = Design("watchtower", "Watchtower", 7, 19, 7,
           "A tall lookout with a railed platform and a pyramid roof - see raids coming.",
           "Defense", "minecraft:spyglass", 2, 12)
d.fill(1, 0, 1, 5, 0, 5, "cobblestone")
d.ring(1, 1, 5, 5, 1, 4, weathered("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.25, 0, 3))
d.ring(1, 1, 5, 5, 5, 9, "spruce_planks")
d.corners(1, 1, 5, 5, 1, 9, "spruce_log[axis=y]")
for x in range(1, 6):
    d.set(x, 5, 1, "spruce_log[axis=x]"); d.set(x, 5, 5, "spruce_log[axis=x]")
d.door(3, 1, 5, "spruce", "north")
for (x, z) in ((3, 1), (1, 3), (5, 3)):
    d.set(x, 7, z, "glass_pane")
d.set(1, 3, 3, CLEAR); d.set(5, 3, 3, CLEAR)
for y in range(1, 11):
    d.set(3, y, 2, "ladder[facing=south]")
d.fill(0, 10, 0, 6, 10, 6, "spruce_planks")
d.set(3, 10, 2, "ladder[facing=south]")
d.ring(0, 0, 6, 6, 11, 11, "spruce_fence")
d.corners(0, 0, 6, 6, 11, 13, "spruce_log[axis=y]")
d.set(3, 11, 6, "spruce_fence")
d.hip(0, 0, 6, 6, 14, "spruce_stairs", "spruce_planks")
d.fill(2, 15, 2, 4, 15, 4, "spruce_planks")
d.set(3, 14, 3, "lantern[hanging=true]")
d.set(3, 18, 3, "lantern[hanging=false]")
d.set(2, 3, 2, "wall_torch[facing=south]")
d.set(4, 0, 6, "dirt_path"); d.set(3, 0, 6, "dirt_path")
designs.append(d)

# =====================================================================================
# Village Well - stone-rimmed well under a little roof, with the village bell hanging in it.
# =====================================================================================
d = Design("village_well", "Village Well", 7, 9, 7,
           "A covered well with the village bell - a gathering point that helps the village grow.",
           "Civic", "minecraft:bell", 1, 10, foundation="minecraft:cobblestone")
d.fill(0, 0, 0, 6, 0, 6, weathered("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel", 0.2, 0.08, 4))
d.ring(1, 1, 5, 5, 0, 0, "stone_bricks")
d.fill(2, 0, 2, 4, 0, 4, "water[level=0]")
d.ring(1, 1, 5, 5, 1, 1, "stone_brick_wall")
d.corners(1, 1, 5, 5, 1, 5, "spruce_log[axis=y]")
d.hip(0, 0, 6, 6, 5, "spruce_stairs", "spruce_planks")
d.fill(2, 6, 2, 4, 6, 4, "spruce_planks")
d.set(3, 5, 3, "bell[attachment=ceiling,facing=north]")
designs.append(d)

# =====================================================================================
# Farm - log-edged plot, central irrigation channel, four crops, fence and gate, composter
# (the Farmer's job site) and lanterns on the corner posts.
# =====================================================================================
d = Design("farm", "Farm Plot", 11, 3, 11,
           "An irrigated, fenced field of wheat, carrots, potatoes and beetroot, with a composter for a Farmer.",
           "Farming", "minecraft:wheat", 1, 4, foundation="minecraft:dirt")
for x in range(11):
    d.set(x, 0, 0, "oak_log[axis=x]"); d.set(x, 0, 10, "oak_log[axis=x]")
for z in range(1, 10):
    d.set(0, 0, z, "oak_log[axis=z]"); d.set(10, 0, z, "oak_log[axis=z]")
crops = {1: "wheat[age=3]", 2: "wheat[age=2]", 3: "carrots[age=2]", 4: "carrots[age=1]",
         6: "potatoes[age=2]", 7: "potatoes[age=1]", 8: "beetroots[age=1]", 9: "beetroots[age=2]"}
for z in range(1, 10):
    for x in range(1, 10):
        if z == 5:
            d.set(x, 0, z, "water[level=0]")
        else:
            d.set(x, 0, z, "farmland[moisture=7]")
            d.set(x, 1, z, crops[z])
d.set(3, 1, 5, "lily_pad"); d.set(7, 1, 5, "lily_pad")
d.ring(0, 0, 10, 10, 1, 1, "oak_fence")
d.set(5, 1, 10, "oak_fence_gate[facing=south,open=false,in_wall=false]")
d.set(0, 1, 5, "composter[level=0]")
for (x, z) in ((0, 0), (10, 0), (0, 10), (10, 10)):
    d.set(x, 2, z, "lantern[hanging=false]")
d.set(10, 1, 5, "hay_block[axis=y]")
designs.append(d)

# =====================================================================================
# Fletcher's Stall - a striped-canopy market stall with a fletching table (Fletcher's job site,
# one of the Warrior squires), barrels, and a practice target.
# =====================================================================================
d = Design("fletcher_stall", "Fletcher's Stall", 5, 5, 4,
           "A striped market stall with a fletching table - brings a Fletcher to arm your Warriors.",
           "Workshops", "minecraft:fletching_table", 1, 6, foundation="minecraft:spruce_planks")
d.fill(0, 0, 0, 4, 0, 3, "spruce_planks")
d.corners(0, 0, 4, 3, 1, 2, "spruce_fence")
d.set(1, 1, 3, "barrel[facing=up]"); d.set(2, 1, 3, "fletching_table"); d.set(3, 1, 3, "barrel[facing=up]")
d.set(1, 1, 0, "hay_block[axis=y]"); d.set(3, 1, 0, "target")
d.set(2, 2, 3, "lantern[hanging=false]")
d.set(1, 2, 3, "potted_dandelion")
stripe = lambda x, y, z: "minecraft:red_wool" if x % 2 == 0 else "minecraft:white_wool"
d.fill(0, 3, 0, 4, 3, 3, stripe)
d.fill(0, 4, 1, 4, 4, 2, stripe)
designs.append(d)

# =====================================================================================
# Smithy - an open-fronted forge: blast furnace (Armorer), grindstone (Weaponsmith) and smithing
# table (Toolsmith), anvil, lava and quenching cauldrons, chimney.
# =====================================================================================
d = Design("smithy", "Smithy", 11, 11, 8,
           "An open forge with a blast furnace, grindstone and smithing table - brings Armorers and Weaponsmiths.",
           "Workshops", "minecraft:anvil", 2, 18)
X0, X1, Z0, Z1 = 1, 9, 1, 6
d.fill(X0, 0, Z0, X1, 0, Z1, weathered("minecraft:cobblestone", "minecraft:stone", "minecraft:andesite", 0.15, 0.15, 5))
d.fill(X0, 1, Z0, X1, 3, Z0, weathered("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.15, 0, 6))
for x in (X0, X1):
    for z in range(Z0, Z1):
        for y in (1, 2, 3):
            d.set(x, y, z, "cobblestone")
for x in (1, 5, 9):
    for y in (1, 2, 3):
        d.set(x, y, Z1, "spruce_log[axis=y]")
for x in range(X0, X1 + 1):
    d.set(x, 4, Z1, "spruce_log[axis=x]")
    d.set(x, 4, Z0, "cobblestone")
d.gable_x(X0, X1, Z0, Z1, 4, "spruce_stairs", "spruce_slab[type=bottom]", gable="spruce_planks")
d.set(X0, 2, 3, "iron_bars"); d.set(X1, 2, 3, "iron_bars")
# chimney through the roof
for y in range(4, 10):
    d.set(3, y, Z0, "cobblestone")
d.set(3, 10, Z0, "campfire[facing=north,lit=true,signal_fire=false]")
# workshop
d.set(3, 1, 2, "blast_furnace[facing=south,lit=false]")
d.set(2, 1, 2, "lava_cauldron"); d.set(4, 1, 2, "water_cauldron[level=3]")
d.set(5, 1, 3, "anvil[facing=west]")
d.set(7, 1, 2, "smithing_table")
d.set(8, 1, 2, "grindstone[face=floor,facing=south]")
d.set(8, 1, 4, "barrel[facing=up]"); d.set(8, 1, 5, "chest[facing=west,type=single]")
d.set(2, 1, 5, "barrel[facing=up]")
d.set(3, 3, Z1, "lantern[hanging=true]"); d.set(7, 3, Z1, "lantern[hanging=true]")
d.fill(3, 0, 7, 7, 0, 7, "gravel")
designs.append(d)

# =====================================================================================
# Library - stone-founded hall lined with bookshelves, tall windows, lectern (Librarian),
# reading tables, carpet, hanging lanterns.
# =====================================================================================
d = Design("library", "Library", 11, 11, 9,
           "A tall reading hall lined with bookshelves, with a lectern for a Librarian.",
           "Civic", "minecraft:lectern", 2, 20)
X0, X1, Z0, Z1 = 1, 9, 1, 7
d.fill(X0, 0, Z0, X1, 0, Z1, "stone_bricks")
d.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "dark_oak_planks")
d.ring(X0, Z0, X1, Z1, 1, 1, "stone_bricks")
d.ring(X0, Z0, X1, Z1, 2, 4, "birch_planks")
d.corners(X0, Z0, X1, Z1, 1, 4, "dark_oak_log[axis=y]")
for x in (3, 5, 7):
    for y in (1, 2, 3, 4):
        d.set(x, y, Z0, "dark_oak_log[axis=y]")
for z in (4,):
    for y in (1, 2, 3, 4):
        d.set(X0, y, z, "dark_oak_log[axis=y]"); d.set(X1, y, z, "dark_oak_log[axis=y]")
for x in range(X0, X1 + 1):
    d.set(x, 5, Z0, "dark_oak_log[axis=x]"); d.set(x, 5, Z1, "dark_oak_log[axis=x]")
# tall windows
for z in (2, 3, 5, 6):
    for y in (2, 3):
        d.set(X0, y, z, "glass_pane"); d.set(X1, y, z, "glass_pane")
for x in (2, 3, 7, 8):
    for y in (2, 3):
        d.set(x, y, Z1, "glass_pane")
d.door(5, 1, Z1, "dark_oak", "north")
d.set(5, 3, Z1, "birch_planks")
d.gable_x(X0, X1, Z0, Z1, 5, "dark_oak_stairs", "dark_oak_slab[type=bottom]", gable="birch_planks")
d.set(X0, 6, 4, "glass_pane"); d.set(X1, 6, 4, "glass_pane")
# shelves along the back wall, two high, plus side stacks
for x in range(2, 9):
    for y in (1, 2, 3):
        if x != 5:
            d.set(x, y, 2, "bookshelf")
d.set(5, 1, 2, "chiseled_stone_bricks"); d.set(5, 2, 2, "lantern[hanging=false]")
for z in (5, 6):
    for y in (1, 2):
        d.set(2, y, z, "bookshelf"); d.set(8, y, z, "bookshelf")
d.set(5, 1, 4, "lectern[facing=south,has_book=false,powered=false]")
# reading tables
for x in (3, 7):
    d.set(x, 1, 4, "spruce_fence"); d.set(x, 2, 4, "spruce_pressure_plate")
    d.set(x, 1, 5, "spruce_stairs[facing=south,half=bottom]")
d.fill(4, 1, 5, 6, 1, 6, "red_carpet")
d.set(3, 4, 4, "lantern[hanging=true]"); d.set(7, 4, 4, "lantern[hanging=true]")
d.fill(3, 5, 4, 7, 5, 4, "dark_oak_log[axis=x]")
d.set(5, 0, 8, "dirt_path"); d.set(4, 0, 8, "stone_brick_slab[type=bottom]"); d.set(6, 0, 8, "stone_brick_slab[type=bottom]")
designs.append(d)

# =====================================================================================
# Chapel - white nave under a steep dark roof, stained-glass windows, pews, candle-lit altar
# with a brewing stand (Cleric - the healing squire), and a belfry over the door.
# =====================================================================================
d = Design("chapel", "Chapel", 9, 15, 13,
           "A white-walled chapel with stained glass, pews, a belfry and a candle-lit altar - brings a Cleric to heal your Warriors.",
           "Civic", "minecraft:brewing_stand", 2, 22, foundation="minecraft:stone_bricks")
X0, X1, Z0, Z1 = 1, 7, 1, 10
d.fill(X0, 0, Z0, X1, 0, Z1, "stone_bricks")
d.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "polished_andesite")
d.fill(4, 0, Z0 + 1, 4, 0, Z1 - 1, "red_wool")
d.ring(X0, Z0, X1, Z1, 1, 1, "stone_bricks")
d.ring(X0, Z0, X1, Z1, 2, 5, "white_terracotta")
for z in (Z0, 4, 7, Z1):
    for y in range(1, 6):
        d.set(X0, y, z, "stone_bricks"); d.set(X1, y, z, "stone_bricks")
for x in (X0, X1):
    for z in range(Z0, Z1 + 1):
        d.set(x, 6, z, "stone_bricks")
for z in (2, 3, 5, 6, 8, 9):
    for y in (2, 3, 4):
        d.set(X0, y, z, "light_blue_stained_glass_pane" if y == 4 else "yellow_stained_glass_pane")
        d.set(X1, y, z, "light_blue_stained_glass_pane" if y == 4 else "yellow_stained_glass_pane")
d.door(4, 1, Z1, "dark_oak", "north")
d.set(4, 3, Z1, "chiseled_stone_bricks")
top = d.gable_z(Z0, Z1, X0, X1, 6, "dark_oak_stairs", "dark_oak_planks", gable="white_terracotta")
# rose window high on the front gable
d.set(4, 7, Z1, "purple_stained_glass_pane"); d.set(4, 8, Z1, "purple_stained_glass_pane")
d.set(3, 7, Z1, "light_blue_stained_glass_pane"); d.set(5, 7, Z1, "light_blue_stained_glass_pane")
# belfry on the ridge above the door
d.corners(3, 9, 5, 11, 10, 11, "dark_oak_fence")
d.set(4, 9, 10, "dark_oak_planks")
d.hip(3, 9, 5, 11, 12, "dark_oak_stairs", "dark_oak_planks")
d.set(4, 14, 10, "lightning_rod[facing=up]")
d.set(4, 12, 10, "dark_oak_planks")
d.set(4, 11, 10, "bell[attachment=ceiling,facing=south]")
# altar
d.fill(3, 1, 2, 5, 1, 2, "chiseled_stone_bricks")
d.set(4, 1, 2, "polished_andesite")
d.set(4, 2, 2, "brewing_stand")
d.set(3, 2, 2, "candle[candles=3,lit=true]"); d.set(5, 2, 2, "candle[candles=3,lit=true]")
d.set(2, 1, 2, "potted_white_tulip"); d.set(6, 1, 2, "potted_white_tulip")
# pews facing the altar
for z in (5, 7, 9):
    for x in (2, 3, 5, 6):
        d.set(x, 1, z, "spruce_stairs[facing=south,half=bottom]")
d.set(4, 4, 4, "lantern[hanging=true]"); d.set(4, 4, 7, "lantern[hanging=true]")
d.fill(2, 5, 4, 6, 5, 4, "dark_oak_log[axis=x]"); d.fill(2, 5, 7, 6, 5, 7, "dark_oak_log[axis=x]")
d.fill(3, 0, 11, 5, 0, 12, "dirt_path")
d.set(4, 0, 11, "stone_bricks"); d.set(4, 0, 12, "stone_bricks")
designs.append(d)

# =====================================================================================
# Bank - a classical stone hall on a raised plinth: front steps, columned portico, gold-capped
# pediment, a barred vault of chests, and a teller's counter.
# =====================================================================================
d = Design("bank", "Bank", 11, 10, 10,
           "A columned stone bank on a raised plinth, with a teller's counter and a barred vault.",
           "Civic", "minecraft:gold_ingot", 3, 40, foundation="minecraft:stone_bricks")
d.fill(0, 0, 0, 10, 0, 8, "stone_bricks")
d.fill(0, 1, 0, 10, 1, 8, "polished_andesite")
for x in range(2, 9):
    d.set(x, 1, 9, "stone_brick_stairs[facing=north,half=bottom]")
    d.set(x, 0, 9, "stone_bricks")
X0, X1, Z0, Z1 = 1, 9, 1, 6
d.ring(X0, Z0, X1, Z1, 2, 5, "smooth_stone")
d.corners(X0, Z0, X1, Z1, 2, 5, "chiseled_stone_bricks")
for y in range(2, 6):
    for x in (1, 3, 7, 9):
        d.set(x, y, 8, "stone_brick_wall")
d.set(1, 5, 8, "chiseled_stone_bricks"); d.set(3, 5, 8, "chiseled_stone_bricks")
d.set(7, 5, 8, "chiseled_stone_bricks"); d.set(9, 5, 8, "chiseled_stone_bricks")
for x in (2, 3, 7, 8):
    d.set(x, 3, Z1, "glass_pane"); d.set(x, 4, Z1, "glass_pane")
for z in (3, 4):
    d.set(X0, 3, z, "iron_bars"); d.set(X1, 3, z, "iron_bars")
    d.set(X0, 4, z, "iron_bars"); d.set(X1, 4, z, "iron_bars")
d.door(5, 2, Z1, "spruce", "north", "left")
d.set(5, 4, Z1, "gold_block")
d.fill(0, 6, 0, 10, 6, 8, "smooth_stone_slab[type=double]")
for x in range(0, 11):
    d.set(x, 6, 9, "stone_brick_stairs[facing=north,half=top]")
# pediment
d.fill(2, 7, 8, 8, 7, 8, "polished_andesite")
d.fill(4, 8, 8, 6, 8, 8, "polished_andesite")
d.set(5, 9, 8, "gold_block")
d.set(1, 7, 8, "stone_brick_stairs[facing=east,half=bottom]"); d.set(9, 7, 8, "stone_brick_stairs[facing=west,half=bottom]")
d.set(3, 8, 8, "stone_brick_stairs[facing=east,half=bottom]"); d.set(7, 8, 8, "stone_brick_stairs[facing=west,half=bottom]")
d.set(4, 9, 8, "stone_brick_stairs[facing=east,half=bottom]"); d.set(6, 9, 8, "stone_brick_stairs[facing=west,half=bottom]")
# counter and vault
for x in range(2, 9):
    if x != 5:
        d.set(x, 2, 4, "polished_andesite_slab[type=top]" if x not in (2, 8) else "polished_andesite")
d.set(5, 2, 4, "spruce_trapdoor[facing=south,half=top,open=false]")
for x in (3, 4, 6, 7):
    d.set(x, 3, 4, "iron_bars")
for x in (2, 3, 7, 8):
    d.set(x, 2, 2, "chest[facing=south,type=single]")
d.set(4, 2, 2, "barrel[facing=up]"); d.set(6, 2, 2, "barrel[facing=up]")
d.set(5, 2, 2, "emerald_block")
d.set(3, 5, 5, "lantern[hanging=true]"); d.set(7, 5, 5, "lantern[hanging=true]")
d.set(5, 5, 3, "lantern[hanging=true]")
d.fill(4, 2, 5, 6, 2, 5, "red_carpet")
d.set(5, 2, 5, "red_carpet")
designs.append(d)

# =====================================================================================
# Barracks - long timber hall on a stone base: four beds, weapon rack (grindstone - Weaponsmith),
# armour storage, a training yard with targets and a hay dummy.
# =====================================================================================
d = Design("barracks", "Barracks", 15, 9, 12,
           "A long hall with four bunks, a weapon rack and a training yard - houses your village's defenders.",
           "Defense", "minecraft:iron_sword", 3, 28)
X0, X1, Z0, Z1 = 1, 13, 1, 7
d.fill(X0, 0, Z0, X1, 0, Z1, "cobblestone")
d.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce_planks")
d.ring(X0, Z0, X1, Z1, 1, 1, weathered("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:cobblestone", 0.2, 0, 7))
d.ring(X0, Z0, X1, Z1, 2, 3, "spruce_planks")
for x in range(X0, X1 + 1, 3):
    for y in (1, 2, 3):
        d.set(x, y, Z0, "spruce_log[axis=y]"); d.set(x, y, Z1, "spruce_log[axis=y]")
for y in (1, 2, 3):
    d.set(X0, y, 4, "spruce_log[axis=y]"); d.set(X1, y, 4, "spruce_log[axis=y]")
for x in range(X0, X1 + 1):
    d.set(x, 4, Z0, "spruce_log[axis=x]"); d.set(x, 4, Z1, "spruce_log[axis=x]")
for x in (2, 3, 5, 6, 8, 9, 11, 12):
    d.set(x, 2, Z0, "glass_pane")
for x in (2, 3, 5, 9, 11, 12):
    d.set(x, 2, Z1, "glass_pane")
d.set(X0, 2, 3, "glass_pane"); d.set(X0, 2, 5, "glass_pane"); d.set(X1, 2, 3, "glass_pane"); d.set(X1, 2, 5, "glass_pane")
d.door(7, 1, Z1, "spruce", "north")
d.gable_x(X0, X1, Z0, Z1, 4, "cobblestone_stairs", "cobblestone_slab[type=bottom]", gable="spruce_planks")
for x in (2, 4, 10, 12):
    d.bed(x, 1, 3, "blue" if x < 7 else "red", "north")
    d.set(x, 1, 4, "chest[facing=north,type=single]")
d.set(6, 1, 2, "grindstone[face=floor,facing=south]")
d.set(7, 1, 2, "smithing_table")
d.set(8, 1, 2, "barrel[facing=up]")
d.set(3, 1, 6, "barrel[facing=up]"); d.set(11, 1, 6, "barrel[facing=up]")
d.fill(2, 4, 4, 12, 4, 4, "spruce_log[axis=x]")
for x in (4, 10):
    d.set(x, 3, 4, "lantern[hanging=true]")
d.set(7, 3, 4, "lantern[hanging=true]")
# training yard
for x in range(1, 14):
    d.set(x, 0, 9, "dirt_path" if x % 4 else "coarse_dirt")
    d.set(x, 0, 10, "coarse_dirt" if x % 3 == 0 else "dirt_path")
d.set(7, 0, 8, "dirt_path")
d.set(2, 1, 10, "target"); d.set(12, 1, 10, "target")
d.set(5, 1, 10, "hay_block[axis=y]"); d.set(5, 2, 10, "carved_pumpkin[facing=south]")
d.set(9, 1, 10, "hay_block[axis=y]"); d.set(9, 2, 10, "carved_pumpkin[facing=south]")
for x in (0, 14):
    for z in (8, 11):
        d.set(x, 1, z, "spruce_fence")
        d.set(x, 2, z, "torch")
for x in range(1, 14):
    d.set(x, 1, 11, "spruce_fence")
d.set(7, 1, 11, CLEAR)
designs.append(d)

# =====================================================================================
# Observatory - an octagonal stone tower with a glass dome, a telescope, and a cartography
# table (Cartographer) on the ground floor.
# =====================================================================================
d = Design("observatory", "Observatory", 9, 16, 9,
           "An octagonal tower crowned with a glass dome and telescope, with a cartography table for a Cartographer.",
           "Civic", "minecraft:spyglass", 3, 32, foundation="minecraft:stone_bricks")
oct7 = [(2, 4), (1, 5), (0, 6), (0, 6), (0, 6), (1, 5), (2, 4)]


def in_oct(x, z, shape, off):
    zi = z - off
    if zi < 0 or zi >= len(shape):
        return False
    a, b = shape[zi]
    return a + off <= x <= b + off


def oct_edge(x, z, shape, off):
    if not in_oct(x, z, shape, off):
        return False
    return any(not in_oct(x + dx, z + dz, shape, off) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))


stone = weathered("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", 0.1, 0.08, 8)
for z in range(9):
    for x in range(9):
        if in_oct(x, z, oct7, 1):
            d.set(x, 0, z, "polished_andesite")
            d.set(x, 5, z, "spruce_planks")
            d.set(x, 10, z, "stone_bricks")
            for y in range(1, 10):
                if oct_edge(x, z, oct7, 1):
                    d.set(x, y, z, stone(x, y, z))
# window bands
for y in (3, 7):
    for (x, z) in ((1, 4), (7, 4), (4, 1)):
        d.set(x, y, z, "glass_pane")
for y in (2, 3, 6, 7):
    for (x, z) in ((2, 2), (6, 2), (2, 6), (6, 6)):
        if oct_edge(x, z, oct7, 1):
            d.set(x, y, z, "glass_pane")
d.door(4, 1, 7, "spruce", "north")
d.set(4, 3, 7, "chiseled_stone_bricks")
for y in range(1, 11):
    d.set(4, y, 2, "ladder[facing=south]")
# cornice
for z in range(9):
    for x in range(9):
        if oct_edge(x, z, oct7, 1):
            d.set(x, 10, z, "chiseled_stone_bricks" if (x + z) % 2 == 0 else "stone_bricks")
# dome
oct5 = [(1, 3), (0, 4), (0, 4), (0, 4), (1, 3)]
oct3 = [(0, 2), (0, 2), (0, 2)]
for z in range(9):
    for x in range(9):
        if oct_edge(x, z, oct7, 1):
            d.set(x, 11, z, "light_blue_stained_glass")
            d.set(x, 12, z, "glass")
        if oct_edge(x, z, oct5, 2):
            d.set(x, 13, z, "glass")
        if in_oct(x, z, oct3, 3):
            d.set(x, 14, z, "glass")
d.set(4, 15, 4, "lightning_rod[facing=up]")
# telescope
d.set(4, 11, 4, "spruce_fence")
d.set(4, 12, 4, "lightning_rod[facing=north]")
# ground floor: cartography
d.set(3, 1, 3, "cartography_table")
d.set(5, 1, 3, "lectern[facing=south,has_book=false,powered=false]")
d.set(2, 1, 5, "barrel[facing=up]")
d.set(6, 1, 5, "chest[facing=west,type=single]")
d.set(4, 4, 4, "lantern[hanging=true]")
d.set(4, 9, 4, "lantern[hanging=true]")
# upper floor: study
d.set(2, 6, 4, "bookshelf"); d.set(6, 6, 4, "bookshelf")
d.set(3, 6, 5, "white_carpet"); d.set(4, 6, 5, "white_carpet"); d.set(5, 6, 5, "white_carpet")
d.set(4, 5, 2, "ladder[facing=south]")
d.set(4, 10, 2, "ladder[facing=south]")
d.fill(3, 0, 8, 5, 0, 8, "dirt_path")
designs.append(d)

# -------------------------------------------------------------------------------------
index = []
for des in designs:
    counts = des.export()
    index.append(des.id)
    total = sum(n for st, n in counts.items())
    print(f"{des.id:16s} {des.W}x{des.D}x{des.H}  {total} blocks")
with open(os.path.join(OUT, "index.json"), "w") as f:
    f.write(json.dumps({"blueprints": index}, indent=2) + "\n")
for w in warnings:
    print("WARN", w)
