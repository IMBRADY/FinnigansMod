package net.finnigan.tommemod.gametest;

import com.mojang.authlib.GameProfile;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.village.VillageFunds;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageWalls;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.finnigan.tommemod.village.blueprint.WallSnapping;
import net.finnigan.tommemod.village.buildings.BuildingDemolition;
import net.finnigan.tommemod.village.buildings.BuildingSurvey;
import net.finnigan.tommemod.village.buildings.VillageBuildings;
import net.finnigan.tommemod.village.buildings.WallPerimeter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Walls built from blueprint pieces, knocking buildings down for a refund, and finding buildings the
 * village has no record of.
 */
@GameTestHolder(TommeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class VillageWallGameTests {

    private static final String ARENA = "builder_arena";
    private static final BlockPos ARENA_CENTRE = new BlockPos(12, 6, 12);

    /**
     * Four runs of wall joined by four convex corners, every piece placed by snapping onto the one
     * before, close into a loop with no open ends - and the ground inside counts as enclosed, until
     * one piece is taken out.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void snappedWallsCloseALoop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Blueprint corner = Blueprints.get("wall_corner_convex");
        Blueprint wall = Blueprints.get("stone_wall");
        BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0));

        List<WallSnapping.Piece> pieces = new ArrayList<>();
        pieces.add(new WallSnapping.Piece(corner, Rotation.NONE, start));
        WallSnapping.WorldPort port = WallSnapping.ports(corner, Rotation.NONE, start).get(0);
        for (int side = 0; side < 4; side++) {
            for (int i = 0; i < 3; i++) {
                WallSnapping.Snap s = WallSnapping.snap(wall, port);
                if (s == null) {
                    helper.fail("A straight wall would not snap on");
                    return;
                }
                pieces.add(new WallSnapping.Piece(wall, s.rotation(), s.origin()));
                WallSnapping.WorldPort heading = port;
                port = s.ports().stream().filter(p -> p.facing() == heading.facing()).findFirst().orElseThrow();
            }
            if (side == 3) break;
            WallSnapping.Snap c = WallSnapping.snap(corner, port);
            if (c == null) {
                helper.fail("A corner would not snap on");
                return;
            }
            pieces.add(new WallSnapping.Piece(corner, c.rotation(), c.origin()));
            WallSnapping.WorldPort joined = port;
            port = c.ports().stream().filter(p -> p.facing() != joined.facing().getOpposite()).findFirst().orElseThrow();
        }

        List<WallSnapping.WorldPort> open = WallSnapping.openPorts(pieces);
        if (!open.isEmpty()) helper.fail("Loop left " + open.size() + " open ends, e.g. " + open.get(0));

        UUID village = UUID.randomUUID();
        VillageBuildings data = VillageBuildings.get(level);
        List<VillageBuildings.Building> buildings = new ArrayList<>();
        for (WallSnapping.Piece p : pieces) {
            buildings.add(data.record(village, p.blueprint(), p.rotation(), p.origin(),
                    BlueprintPlanner.boundsFor(p.blueprint(), p.rotation(), p.origin())));
        }
        VillageWalls walls = WallPerimeter.enclose(buildings);
        if (walls == null) {
            helper.fail("A closed loop enclosed nothing");
            return;
        }
        BlockPos inside = start.offset(20, 0, 20);
        if (!walls.contains(inside)) helper.fail("The middle of the loop is not inside it");
        if (walls.contains(start.offset(-10, 0, -10))) helper.fail("Ground outside the loop counts as inside");
        if (walls.wallColumns().isEmpty()) helper.fail("The walls themselves were not recorded");

        List<VillageBuildings.Building> broken = new ArrayList<>(buildings);
        broken.remove(2);
        if (WallPerimeter.enclose(broken) != null) helper.fail("A loop with a gap still encloses ground");

        for (VillageBuildings.Building b : buildings) data.remove(b.id);
        helper.succeed();
    }

    /**
     * A wall joined onto one standing well above the ground stays within a block of it (builders fill
     * the ground in underneath), and two pieces joined at different heights get a stair ramp between
     * their walkways, rising one block per step to meet the higher one.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void offsetWallsGetARamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Blueprint wall = Blueprints.get("stone_wall");
        BlockPos ground = BlueprintPlanner.groundBelow(level, helper.absolutePos(ARENA_CENTRE));
        BlockPos high = BlueprintPlanner.originFor(wall, Rotation.NONE, ground, 0).above(4);
        WallSnapping.WorldPort east = WallSnapping.ports(wall, Rotation.NONE, high).stream()
                .filter(p -> p.facing() == net.minecraft.core.Direction.EAST).findFirst().orElseThrow();
        WallSnapping.Snap snap = WallSnapping.snap(wall, east);

        int y = WallSnapping.fitY(level, wall, snap.rotation(), snap.origin(), high.getY(), 24);
        if (y != high.getY() - 1) helper.fail("Expected the joined wall one below its neighbour at " + (high.getY() - 1) + ", got " + y);

        // Now as if it had been dug three below instead: the ramp climbs three steps over its walkway.
        BlockPos low = new BlockPos(snap.origin().getX(), high.getY() - 3, snap.origin().getZ());
        List<BlueprintPlanner.Placement> ramp = WallSnapping.connectors(wall, snap.rotation(), low,
                List.of(new WallSnapping.Piece(wall, Rotation.NONE, high)));
        List<Integer> stairYs = ramp.stream().filter(p -> p.state().is(net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS))
                .map(p -> p.pos().getY()).distinct().sorted().toList();
        int walk = low.getY() + wall.walkway();
        if (!stairYs.equals(List.of(walk, walk + 1, walk + 2))) helper.fail("Ramp steps at " + stairYs + ", expected " + List.of(walk, walk + 1, walk + 2));
        for (BlueprintPlanner.Placement p : ramp) {
            if (p.pos().getX() < low.getX() || p.pos().getX() > low.getX() + 8) helper.fail("Ramp block outside the lower wall: " + p.pos());
        }
        if (!WallSnapping.connectors(wall, Rotation.NONE, high, List.of(new WallSnapping.Piece(wall, snap.rotation(), snap.origin().atY(high.getY())))).isEmpty()) {
            helper.fail("Level walls got a ramp");
        }
        helper.succeed();
    }

    /** Demolishing a building takes its blocks away and hands back what was paid for it. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void demolishRefunds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID village = UUID.randomUUID();
        VillageBuildings.Building b = stamp(helper, "stone_wall", village);
        Blueprint bp = b.blueprint();
        b.paid = bp.cost();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "demolish_test"));
        player.getInventory().clearContent();

        BuildingDemolition.demolish(level, b, player);

        if (VillageBuildings.get(level).get(b.id) != null) helper.fail("Building still on record");
        for (Blueprint.Cost c : bp.cost()) {
            long have = VillageFunds.inventoryCount(player, c.item());
            if (have != c.count()) helper.fail("Refunded " + have + " " + c.item() + ", expected " + c.count());
        }
        int left = 0;
        for (Blueprint.Cell cell : bp.cells(b.rotation)) {
            if (cell.pos().getY() == 0 || cell.state().isAir()) continue;
            if (level.getBlockState(b.origin.offset(cell.pos())).is(cell.state().getBlock())) left++;
        }
        if (left > 0) helper.fail(left + " wall blocks still standing above the floor");
        helper.succeed();
    }

    /**
     * A building standing in a village with no record of it - the Observatory built before buildings
     * were tracked - is found by a survey and starts counting.
     *
     * <p>The test world is kept between runs, so records left by earlier runs over this spot are
     * cleared first, and everything this survey records is cleared after.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void surveyFindsUnrecordedBuilding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageBuildings data = VillageBuildings.get(level);
        Blueprint bp = Blueprints.get("observatory");
        BlockPos ground = BlueprintPlanner.groundBelow(level, helper.absolutePos(ARENA_CENTRE));
        BlockPos origin = BlueprintPlanner.originFor(bp, Rotation.CLOCKWISE_90, ground, 0);
        BlueprintPlanner.Plan plan = BlueprintPlanner.plan(level, bp, Rotation.CLOCKWISE_90, origin, 6);
        for (VillageBuildings.Building stale : new ArrayList<>(data.all())) {
            if (stale.bounds.intersects(plan.bounds())) data.remove(stale.id);
        }
        for (BlueprintPlanner.Placement p : plan.placements()) level.setBlock(p.pos(), p.state(), 2 | 16);

        // Job sites register as village POIs a tick after they are placed.
        helper.runAfterDelay(5, () -> {
            UUID village = VillageManager.get(level).resolveVillage(level, origin.offset(4, 1, 4)).orElse(null);
            if (village == null) {
                helper.fail("The observatory's job sites did not make a village");
                return;
            }
            BuildingSurvey.Result result = BuildingSurvey.survey(level, village);
            List<VillageBuildings.Building> recorded = new ArrayList<>(result.found());
            try {
                boolean found = recorded.stream().anyMatch(b -> b.blueprintId.equals("observatory")
                        && b.rotation == Rotation.CLOCKWISE_90 && b.origin.equals(origin));
                if (!found) {
                    StringBuilder all = new StringBuilder();
                    for (VillageBuildings.Building b : recorded) {
                        all.append(' ').append(b.blueprintId).append('@').append(b.origin.toShortString()).append('/').append(b.rotation);
                    }
                    helper.fail("Survey did not record the observatory at " + origin.toShortString() + "; recorded:" + all);
                    return;
                }
                BuildingSurvey.Result again = BuildingSurvey.survey(level, village);
                recorded.addAll(again.found());
                if (again.found().stream().anyMatch(b -> b.origin.equals(origin))) {
                    helper.fail("A second survey recorded the same observatory again");
                    return;
                }
                helper.succeed();
            } finally {
                for (VillageBuildings.Building b : recorded) data.remove(b.id);
            }
        });
    }

    private static VillageBuildings.Building stamp(GameTestHelper helper, String blueprintId, UUID village) {
        ServerLevel level = helper.getLevel();
        Blueprint bp = Blueprints.get(blueprintId);
        BlockPos ground = BlueprintPlanner.groundBelow(level, helper.absolutePos(ARENA_CENTRE));
        BlockPos origin = BlueprintPlanner.originFor(bp, Rotation.NONE, ground, 0);
        BlueprintPlanner.Plan plan = BlueprintPlanner.plan(level, bp, Rotation.NONE, origin, 6);
        for (BlueprintPlanner.Placement p : plan.placements()) level.setBlock(p.pos(), p.state(), 2 | 16);
        return VillageBuildings.get(level).record(village, bp, Rotation.NONE, origin, plan.bounds());
    }
}
