package net.finnigan.tommemod.gametest;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.VillageFunds;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.WarriorKit;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.finnigan.tommemod.village.buildings.BarracksService;
import net.finnigan.tommemod.village.buildings.BuildingPurpose;
import net.finnigan.tommemod.village.buildings.VillageBuildings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * What finished buildings do for their village. Buildings here are stamped straight into the arena
 * from their blueprints rather than built by Builders - BuilderGameTests covers the building; these
 * cover what happens once it stands.
 */
@GameTestHolder(TommeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingPurposeGameTests {

    private static final String ARENA = "builder_arena";
    private static final BlockPos ARENA_CENTRE = new BlockPos(12, 6, 12);

    /** A finished Barracks musters one Warrior per bed, each with its own bunk and shift. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void barracksMustersWarriors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageBuildings.Building b = stamp(helper, "barracks", UUID.randomUUID());
        BarracksService.onBuilt(level, b);

        helper.succeedWhen(() -> {
            List<WarriorVillagerEntity> warriors = warriorsIn(level, b);
            if (warriors.size() != 4) helper.fail("Expected 4 warriors, found " + warriors.size());
            long shifts = warriors.stream().map(WarriorVillagerEntity::getShiftStart).distinct().count();
            if (shifts != 4) helper.fail("Shifts are not staggered: " + shifts + " distinct");
            for (WarriorVillagerEntity w : warriors) {
                if (w.getBunkBed() == null || w.getBunkChest() == null) helper.fail("A warrior has no bunk");
            }
        });
    }

    /** A Barracks Warrior that dies is replaced at its bunk once the respawn time has passed. */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void barracksReplacesTheFallen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageBuildings.Building b = stamp(helper, "barracks", UUID.randomUUID());
        BarracksService.onBuilt(level, b);
        VillageBuildings.BarracksSlot slot = b.slots.get(0);
        UUID first = slot.warrior;
        WarriorVillagerEntity w = BarracksService.warriorAt(level, slot);
        if (w == null) {
            helper.fail("No warrior mustered");
            return;
        }
        w.kill();
        helper.runAfterDelay(5, () -> {
            if (slot.diedAt < 0) helper.fail("Death was not recorded on the bunk");
            // Skip the day-long wait.
            slot.diedAt = level.getGameTime() - ModConfig.WARRIOR_RESPAWN_TICKS.get();
        });
        helper.succeedWhen(() -> {
            if (slot.warrior == null || slot.warrior.equals(first) || BarracksService.warriorAt(level, slot) == null) {
                helper.fail("Not replaced yet");
            }
        });
    }

    /** Warriors sleep through their shift and are awake outside it. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void shiftsCoverTheDay(GameTestHelper helper) {
        int asleepAtAnyTime = 0;
        for (long t = 0; t < 24000; t += 1000) {
            int asleep = 0;
            for (int i = 0; i < 4; i++) if (BarracksService.isSleepShift(t, i * 6000)) asleep++;
            if (asleep != 2) helper.fail("At tick " + t + ", " + asleep + " of 4 asleep (expected 2)");
            asleepAtAnyTime += asleep;
        }
        if (asleepAtAnyTime == 0) helper.fail("Nobody ever sleeps");
        helper.succeed();
    }

    /** Warriors take what they need from their bunk chest, and leave what they don't. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void warriorTakesKitFromChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chestPos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.IRON_HELMET));
        chest.setItem(1, new ItemStack(Items.ARROW, 16));
        chest.setItem(2, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING));
        chest.setItem(3, new ItemStack(Items.WOODEN_SWORD));

        WarriorVillagerEntity w = ModEntityTypes.WARRIOR_VILLAGER.get().create(level);
        w.moveTo(chestPos.getX() + 1.5, chestPos.getY(), chestPos.getZ() + 0.5);
        level.addFreshEntity(w);
        w.setHealth(w.getMaxHealth() - 6);
        float before = w.getHealth();

        WarriorKit.takeFrom(w, chest);
        if (!w.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)) helper.fail("Helmet not worn");
        if (!w.isSquireIssued(EquipmentSlot.HEAD)) helper.fail("Helmet not marked as village kit");
        if (w.getHealth() <= before) helper.fail("Healing potion not drunk");
        if (!chest.getItem(1).is(Items.ARROW)) helper.fail("Arrows taken by a Warrior that doesn't shoot");
        if (!chest.getItem(3).is(Items.WOODEN_SWORD)) helper.fail("A weaker sword replaced the halberd");
        helper.succeed();
    }

    /** With a Bank standing, village costs come out of the bank first, then the player's pockets. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void bankPaysFirst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID village = UUID.randomUUID();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "bank_test"));
        player.getInventory().add(new ItemStack(Items.EMERALD, 5));
        VillageManager.get(level).bankDeposit(village, Items.EMERALD, 10);

        if (VillageFunds.hasEnough(player, village, Items.EMERALD, 12)) helper.fail("Bank counted before a Bank was built");

        VillageBuildings.Building bank = stamp(helper, "bank", village);
        if (!VillageBuildings.get(level).hasBank(level, village)) helper.fail("Stamped bank does not count as standing");
        if (!VillageFunds.hasEnough(player, village, Items.EMERALD, 15)) helper.fail("Bank + inventory should cover 15");
        if (!VillageFunds.tryDeductItem(player, village, Items.EMERALD, 12)) helper.fail("Payment refused");
        long left = VillageManager.get(level).bankCount(village, Items.EMERALD);
        if (left != 0) helper.fail("Bank should be emptied first, has " + left);
        if (VillageFunds.inventoryCount(player, Items.EMERALD) != 3) helper.fail("Inventory should hold 3, has " + VillageFunds.inventoryCount(player, Items.EMERALD));
        helper.succeed();
    }

    /** Places a blueprint's blocks directly and records the building, as if Builders had just finished it. */
    private static VillageBuildings.Building stamp(GameTestHelper helper, String blueprintId, UUID village) {
        ServerLevel level = helper.getLevel();
        Blueprint bp = Blueprints.get(blueprintId);
        BlockPos ground = BlueprintPlanner.groundBelow(level, helper.absolutePos(ARENA_CENTRE));
        BlockPos origin = BlueprintPlanner.originFor(bp, Rotation.NONE, ground, 0);
        BlueprintPlanner.Plan plan = BlueprintPlanner.plan(level, bp, Rotation.NONE, origin, 6);
        for (BlueprintPlanner.Placement p : plan.placements()) level.setBlock(p.pos(), p.state(), 2 | 16);
        VillageBuildings.Building b = VillageBuildings.get(level).record(village, bp, Rotation.NONE, origin, plan.bounds());
        if (!b.purpose.equals(bp.purpose()) || (blueprintId.equals("barracks") && !b.purpose.equals(BuildingPurpose.BARRACKS))) {
            helper.fail("Purpose not recorded");
        }
        return b;
    }

    private static List<WarriorVillagerEntity> warriorsIn(ServerLevel level, VillageBuildings.Building b) {
        return level.getEntitiesOfClass(WarriorVillagerEntity.class, AABB.of(b.bounds).inflate(3), WarriorVillagerEntity::isAlive);
    }
}
