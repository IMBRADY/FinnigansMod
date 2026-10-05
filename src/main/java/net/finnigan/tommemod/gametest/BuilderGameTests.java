package net.finnigan.tommemod.gametest;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.finnigan.tommemod.village.construction.BuilderWorkHandler;
import net.finnigan.tommemod.village.construction.ConstructionManager;
import net.finnigan.tommemod.village.construction.ConstructionService;
import net.finnigan.tommemod.village.construction.ConstructionSite;
import net.finnigan.tommemod.villager.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * In-world checks for blueprints and Builder Villagers. Run with {@code gradlew runGameTestServer}
 * (dev only - Forge never registers these in a real install).
 *
 * <p>Every blueprint gets its own test: it is planned onto a flat grass arena, three Builders are
 * spawned, and the test passes once they have finished and the result matches the design block for
 * block. That catches broken designs (a torch with nothing to hang on, a door missing a half) as
 * well as broken builders.
 */
@GameTestHolder(TommeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuilderGameTests {

    /** @GameTest adds the holder's namespace itself; generated TestFunctions need it spelled out. */
    private static final String ARENA = "builder_arena";
    private static final String ARENA_ID = TommeMod.MOD_ID + ":" + ARENA;
    /** Above the middle of the arena; the ground is found from here the way the blueprint cursor finds it. */
    private static final BlockPos ARENA_CENTRE = new BlockPos(12, 6, 12);
    private static final Rotation[] ROTATIONS = Rotation.values();
    /** Crops grow and lily pads drift; a handful of cells may legitimately differ from the design. */
    private static final double TOLERATED_MISMATCH = 0.01;

    @GameTestGenerator
    public static Collection<TestFunction> buildEveryBlueprint() {
        List<TestFunction> tests = new ArrayList<>();
        List<Blueprint> all = Blueprints.all();
        for (int i = 0; i < all.size(); i++) {
            Blueprint bp = all.get(i);
            Rotation rotation = ROTATIONS[i % ROTATIONS.length];
            // One batch each, so nothing (water, wandering Builders) crosses from a neighbouring arena.
            tests.add(new TestFunction("blueprint_" + bp.id(), "build_" + bp.id(), ARENA_ID, Rotation.NONE, 9000, 0L, true,
                    helper -> buildAndVerify(helper, bp, rotation)));
        }
        return tests;
    }

    private static void buildAndVerify(GameTestHelper helper, Blueprint bp, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = ground(helper);
        BlockPos origin = BlueprintPlanner.originFor(bp, rotation, ground, 0);
        BlueprintPlanner.Plan plan = BlueprintPlanner.plan(level, bp, rotation, origin, ModConfig.BLUEPRINT_MAX_GROUND_GAP.get());
        if (!plan.valid()) {
            helper.fail("Plan for " + bp.id() + " refused on a flat arena: " + plan.problem().getString());
            return;
        }
        clearLeftovers(helper);
        for (int i = 0; i < 3; i++) spawnBuilder(helper, new BlockPos(1 + i, 2, 1));
        ConstructionSite site = ConstructionService.start(level, UUID.randomUUID(), null, plan, List.of());

        helper.succeedWhen(() -> {
            if (ConstructionManager.get(level).get(site.id()) != null) {
                List<Villager> near = BuilderWorkHandler.buildersNear(level, origin, 96);
                StringBuilder crew = new StringBuilder();
                for (Villager v : near) crew.append(String.format(" [%s hp=%.0f stuck=%s job=%s]", v.blockPosition().subtract(origin).toShortString(),
                        v.getHealth(), v.isInWall(), v.getVillagerData().getProfession()));
                helper.fail("Still building " + bp.id() + ": " + site.completedCount() + "/" + site.total() + ", builders alive=" + near.size()
                        + " assigned=" + BuilderWorkHandler.workersOn(level, site.id()) + crew);
            }
            List<String> wrong = new ArrayList<>();
            int checked = 0;
            for (Blueprint.Cell cell : bp.cells(rotation)) {
                BlockPos pos = origin.offset(cell.pos());
                BlockState actual = level.getBlockState(pos);
                // Plants are not checked: the test server runs far faster than the light engine, so a
                // fresh arena is still pitch dark when crops go in, and crops rightly die in the dark.
                if (cell.state().getBlock() instanceof net.minecraft.world.level.block.BushBlock) continue;
                checked++;
                boolean ok = cell.state().isAir() ? actual.isAir() : actual.is(cell.state().getBlock());
                if (!ok) wrong.add(cell.state().getBlock().getName().getString() + "@" + cell.pos() + " was " + actual.getBlock().getName().getString());
            }
            if (wrong.size() > Math.max(1, checked * TOLERATED_MISMATCH)) {
                List<String> water = new ArrayList<>();
                for (BlockPos p : BlockPos.betweenClosed(origin.offset(-8, 0, -8), origin.offset(24, 6, 24))) {
                    if (level.getFluidState(p).isSource()) water.add(p.subtract(origin).toShortString());
                    if (water.size() > 5) break;
                }
                if (!water.isEmpty()) wrong.add(0, "fluid sources near: " + water);
                helper.fail(bp.id() + " (" + rotation + ") finished with " + wrong.size() + " wrong blocks, e.g. "
                        + String.join("; ", wrong.subList(0, Math.min(6, wrong.size()))));
            }
        });
    }

    /** A cancelled site must leave the ground exactly as it found it. */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void cancelRevertsTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Blueprint bp = Blueprints.get("cottage");
        if (bp == null) {
            helper.fail("cottage blueprint missing");
            return;
        }
        BlockPos origin = BlueprintPlanner.originFor(bp, Rotation.NONE, ground(helper), 0);
        BlueprintPlanner.Plan plan = BlueprintPlanner.plan(level, bp, Rotation.NONE, origin, 6);
        clearLeftovers(helper);
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        for (BlueprintPlanner.Placement p : plan.placements()) before.put(p.pos(), level.getBlockState(p.pos()));
        for (int i = 0; i < 3; i++) spawnBuilder(helper, new BlockPos(1 + i, 2, 1));
        ConstructionSite site = ConstructionService.start(level, UUID.randomUUID(), null, plan, List.of());

        helper.runAfterDelay(200, () -> {
            if (site.completedCount() == 0) helper.fail("Builders placed nothing in 200 ticks");
            ConstructionService.cancel(level, site, null);
            if (ConstructionManager.get(level).get(site.id()) != null) helper.fail("Site still registered after cancel");
            for (java.util.Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                if (!level.getBlockState(e.getKey()).is(e.getValue().getBlock())) {
                    helper.fail("Not reverted at " + e.getKey().subtract(origin) + ": was " + e.getValue() + ", now " + level.getBlockState(e.getKey()));
                }
            }
            helper.succeed();
        });
    }

    /** Sites survive a save and reload with their queue, progress and originals intact. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void siteSurvivesSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Blueprint bp = Blueprints.get("village_well");
        if (bp == null) {
            helper.fail("village_well blueprint missing");
            return;
        }
        BlockPos origin = BlueprintPlanner.originFor(bp, Rotation.CLOCKWISE_90, ground(helper), 0);
        BlueprintPlanner.Plan plan = BlueprintPlanner.plan(level, bp, Rotation.CLOCKWISE_90, origin, 6);
        ConstructionSite site = new ConstructionSite(UUID.randomUUID(), UUID.randomUUID(), null, bp.id(),
                Rotation.CLOCKWISE_90, origin, plan.bounds(), 0L, plan.placements(), bp.cost());
        Integer claimed = site.claimNext(level);
        if (claimed == null) {
            helper.fail("Nothing to claim");
            return;
        }
        site.recordOriginal(site.placement(claimed).pos(), level.getBlockState(site.placement(claimed).pos()));

        ConstructionSite copy = ConstructionSite.load(level, site.save());
        if (copy.total() != site.total()) helper.fail("Placement count changed: " + site.total() + " -> " + copy.total());
        if (copy.rotation() != Rotation.CLOCKWISE_90) helper.fail("Rotation lost");
        for (int i = 0; i < site.total(); i++) {
            if (!copy.placement(i).state().equals(site.placement(i).state()) || !copy.placement(i).pos().equals(site.placement(i).pos())) {
                helper.fail("Placement " + i + " changed in the round trip");
            }
        }
        if (copy.originalsNewestFirst().size() != 1) helper.fail("Originals not saved");
        if (copy.paid().size() != bp.cost().size()) helper.fail("Paid cost not saved");
        // The claimed-but-unplaced block must come back as work to do, not be skipped.
        Integer reclaimed = copy.claimNext(level);
        if (reclaimed == null || !reclaimed.equals(claimed)) helper.fail("Claimed work lost across save: expected " + claimed + " got " + reclaimed);
        helper.succeed();
    }

    private static BlockPos ground(GameTestHelper helper) {
        return BlueprintPlanner.groundBelow(helper.getLevel(), helper.absolutePos(ARENA_CENTRE));
    }

    /** Batches reuse the same spot, so villagers and falling blocks from the last one may still be about. */
    private static void clearLeftovers(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48);
        for (Entity e : helper.getLevel().getEntitiesOfClass(Entity.class, area, en -> !(en instanceof Player))) {
            e.discard();
        }
    }

    private static void spawnBuilder(GameTestHelper helper, BlockPos rel) {
        Villager v = helper.spawn(EntityType.VILLAGER, rel);
        v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER.get()));
        // A professional with no experience and no job site gets its profession taken away; a
        // little experience keeps it a Builder without needing a Blueprint Stand in the arena.
        v.setVillagerXp(1);
    }
}
