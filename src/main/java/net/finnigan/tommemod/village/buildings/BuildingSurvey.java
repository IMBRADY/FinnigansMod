package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.finnigan.tommemod.village.construction.ConstructionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Chief Desk's "check buildings": finds every blueprint building standing in a village that the
 * village has no record of, and records it - so a building finished before buildings were tracked,
 * or before its design did anything, starts doing its job.
 *
 * <p>Each design is looked for by its rarest block (a lantern, a cartography table, a bell): every one
 * of those in the village is a place the design might be, tried in all four rotations, and it counts
 * as found when nearly all of its blocks are there. Chunk sections that hold none of the blocks being
 * looked for are skipped whole, which is what keeps a sweep over a whole village cheap enough to run
 * on a button press.
 *
 * <p>Also lays out the bunks of any Barracks that was recorded before Barracks had a purpose.
 */
public final class BuildingSurvey {

    /** Share of a design's blocks that must be in place to call it found. Higher than "standing", so
     * a stray lantern on a cobblestone house never passes for a building. */
    private static final double FOUND_THRESHOLD = 0.85;
    private static final int BELOW = 24;
    private static final int ABOVE = 48;

    public record Result(List<VillageBuildings.Building> found, int barracksMustered) {
    }

    private record Anchor(Blueprint blueprint, Rotation rotation, BlockPos cellPos) {
    }

    private BuildingSurvey() {
    }

    public static Result survey(ServerLevel level, UUID villageId) {
        VillageManager manager = VillageManager.get(level);
        VillageBuildings data = VillageBuildings.get(level);
        VillageRegion region = manager.resolveVillageRegion(level, villageId);
        int reach = (int) Math.ceil(region.radius()) + ModConfig.BUILDER_HUB_REGION_PADDING_BLOCKS.get();
        BlockPos centre = region.anchor();

        // What to look for: each design's rarest block, and where that block sits in every rotation.
        Map<Block, List<Anchor>> anchors = new HashMap<>();
        for (Blueprint bp : Blueprints.all()) {
            Block rarest = rarestBlock(bp);
            if (rarest == null) continue;
            for (Rotation rotation : Rotation.values()) {
                for (Blueprint.Cell cell : bp.cells(rotation)) {
                    if (cell.state().is(rarest)) anchors.computeIfAbsent(rarest, k -> new ArrayList<>()).add(new Anchor(bp, rotation, cell.pos()));
                }
            }
        }
        Set<Block> wanted = anchors.keySet();

        List<VillageBuildings.Building> found = new ArrayList<>();
        Set<String> tried = new HashSet<>();
        int minY = Math.max(level.getMinBuildHeight(), centre.getY() - BELOW);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + ABOVE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int cx = (centre.getX() - reach) >> 4; cx <= (centre.getX() + reach) >> 4; cx++) {
            for (int cz = (centre.getZ() - reach) >> 4; cz <= (centre.getZ() + reach) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> wanted.contains(s.getBlock()))) continue;
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    if (baseY + 15 < minY || baseY > maxY) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                List<Anchor> candidates = anchors.get(state.getBlock());
                                if (candidates == null) continue;
                                pos.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                                for (Anchor a : candidates) {
                                    BlockPos origin = pos.subtract(a.cellPos());
                                    String key = a.blueprint().id() + a.rotation() + origin.asLong();
                                    if (!tried.add(key)) continue;
                                    VillageBuildings.Building b = tryRecord(level, data, villageId, a.blueprint(), a.rotation(), origin);
                                    if (b != null) found.add(b);
                                }
                            }
                        }
                    }
                }
            }
        }

        int mustered = 0;
        for (VillageBuildings.Building b : data.inVillage(level, villageId)) {
            if (BuildingPurpose.BARRACKS.equals(b.purpose) && b.slots.isEmpty() && data.isStanding(level, b)) {
                BarracksService.onBuilt(level, b);
                mustered++;
            }
        }
        if (!found.isEmpty()) WallPerimeter.recompute(level, villageId);
        return new Result(found, mustered);
    }

    private static VillageBuildings.Building tryRecord(ServerLevel level, VillageBuildings data, UUID villageId,
                                                       Blueprint bp, Rotation rotation, BlockPos origin) {
        BoundingBox box = BlueprintPlanner.boundsFor(bp, rotation, origin);
        if (data.overlapsAny(box) || ConstructionManager.get(level).overlaps(box)) return null;

        int total = 0;
        int matching = 0;
        for (Blueprint.Cell cell : bp.cells(rotation)) {
            if (cell.state().isAir()) continue;
            total++;
            if (level.getBlockState(origin.offset(cell.pos())).is(cell.state().getBlock())) matching++;
        }
        if (total == 0 || matching < total * FOUND_THRESHOLD) return null;
        return data.record(villageId, bp, rotation, origin, box);
    }

    /** The block this design uses least - the fewest places to try it from. */
    private static Block rarestBlock(Blueprint bp) {
        Map<Block, Integer> counts = new HashMap<>();
        for (Blueprint.Cell cell : bp.cells(Rotation.NONE)) {
            if (cell.state().isAir()) continue;
            counts.merge(cell.state().getBlock(), 1, Integer::sum);
        }
        Block best = null;
        int bestCount = Integer.MAX_VALUE;
        for (Map.Entry<Block, Integer> e : counts.entrySet()) {
            if (e.getValue() < bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}
