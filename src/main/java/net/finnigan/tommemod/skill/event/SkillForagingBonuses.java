package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Foraging tree's own behaviour: what a tree does when it is felled, and what a wood does for
 * somebody who lives in it.
 *
 * Written against the same rule the Mining rework was - a node earns its place by changing when
 * something happens rather than by being another percent of the same thing. Foraging had five nodes
 * that were all "a chance at a second drop" wearing different names; what is here instead is a tree
 * that comes down in one piece, a wood that replants itself, leaves worth stripping, and food worth
 * carrying.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillForagingBonuses {

    /** Most logs one cut may bring down. A jungle giant is a haul, not an afternoon's supply. */
    private static final int FELL_LIMIT = 24;
    /** How far above the cut the feller will keep looking for more of the same trunk. */
    private static final int FELL_HEIGHT = 24;

    /** Vanilla's own chance of an oak leaf giving up an apple, which Limbing multiplies. */
    private static final double VANILLA_APPLE_CHANCE = 0.005;
    /** What slice of the improved apple chance comes up golden instead. */
    private static final double GOLDEN_APPLE_SHARE = 0.02;

    /** How often the growth aura is applied. */
    private static final int GROWTH_INTERVAL_TICKS = 20;
    /** How far from the player crops feel it. */
    private static final int GROWTH_RADIUS = 5;

    // ---- Felling ----

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.isCanceled() || player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        if (state.is(BlockTags.LOGS)) onLogChopped(level, player, pos, state);
        if (state.is(BlockTags.LEAVES)) onLeavesStripped(level, player, pos);
        if (state.getBlock() instanceof CropBlock crop) onCropHarvested(level, player, pos, state, crop);
    }

    private static void onLogChopped(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        repairAxe(player);

        if (!SkillBonuses.roll(player, ModSkillBonuses.TREE_FELLER)) return;

        List<BlockPos> trunk = trunkAbove(level, pos, state);
        ItemStack tool = player.getItemBySlot(EquipmentSlot.MAINHAND);
        for (BlockPos logPos : trunk) {
            BlockState logState = level.getBlockState(logPos);
            harvest(level, player, logPos, logState, tool);
            level.removeBlock(logPos, false);
        }

        if (!trunk.isEmpty() && SkillBonuses.roll(player, ModSkillBonuses.AUTO_REPLANT)) {
            replant(level, pos, state);
        }
    }

    /**
     * The rest of this trunk, upward.
     *
     * Deliberately only upward and only the same species: a downward search runs into whatever the
     * tree is standing on, and a search that ignores species turns a mixed forest into one cut. The
     * horizontal spread is kept so branching trees - jungle, acacia, the big oaks - come down whole
     * rather than leaving an arm floating.
     */
    private static List<BlockPos> trunkAbove(ServerLevel level, BlockPos origin, BlockState struck) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);

        while (!queue.isEmpty() && found.size() < FELL_LIMIT) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos next = current.offset(dx, dy, dz);
                        if (next.getY() - origin.getY() > FELL_HEIGHT) continue;
                        if (found.size() >= FELL_LIMIT || !seen.add(next)) continue;
                        if (!level.getBlockState(next).is(struck.getBlock())) continue;

                        found.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return found;
    }

    /** Puts the matching sapling back where the trunk was, if the ground will still take one. */
    private static void replant(ServerLevel level, BlockPos pos, BlockState struck) {
        Block sapling = saplingFor(struck);
        if (sapling == null) return;
        if (!level.getBlockState(pos).isAir()) return;

        BlockState saplingState = sapling.defaultBlockState();
        if (saplingState.canSurvive(level, pos)) level.setBlockAndUpdate(pos, saplingState);
    }

    /**
     * The sapling belonging to a log, worked out by name.
     *
     * By name rather than by a table because the table would need an entry per wood and would go
     * quietly out of date the first time a mod or a version added one - every log in the game is
     * {@code <namespace>:<wood>_log} or {@code _wood} against a {@code <namespace>:<wood>_sapling},
     * and anything that does not fit simply does not replant.
     */
    private static Block saplingFor(BlockState log) {
        var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(log.getBlock());
        if (id == null) return null;

        String wood = id.getPath().replace("stripped_", "").replace("_log", "").replace("_wood", "");
        var saplingId = new net.minecraft.resources.ResourceLocation(id.getNamespace(), wood + "_sapling");
        Block sapling = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(saplingId);
        return sapling == null || sapling == Blocks.AIR ? null : sapling;
    }

    /** Axe Care: the work keeps the edge rather than wearing it. */
    private static void repairAxe(ServerPlayer player) {
        ItemStack held = player.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(held.getItem() instanceof AxeItem) || !held.isDamaged()) return;
        if (!SkillBonuses.roll(player, ModSkillBonuses.AXE_SELFREPAIR)) return;

        held.setDamageValue(held.getDamageValue() - 1);
    }

    // ---- Leaves and crops ----

    /**
     * Limbing: stripping leaves is worth doing rather than something to get through.
     *
     * Rolled here as an addition on top of whatever the leaf block already dropped, so the vanilla
     * half-percent still applies and this is the difference rather than a replacement for it.
     */
    private static void onLeavesStripped(ServerLevel level, ServerPlayer player, BlockPos pos) {
        double multiplier = SkillBonuses.get(player, ModSkillBonuses.LEAF_BOUNTY);
        if (multiplier <= 0.0) return;

        double chance = VANILLA_APPLE_CHANCE * multiplier;
        if (player.getRandom().nextDouble() >= chance) return;

        boolean golden = player.getRandom().nextDouble() < GOLDEN_APPLE_SHARE;
        Block.popResource(level, pos, new ItemStack(golden ? Items.GOLDEN_APPLE : Items.APPLE));
    }

    /**
     * The two things that can happen to a harvested crop, in order of how good they are.
     *
     * Seed Eye puts the plant back fully grown and costs nothing, so it is tried first; Forestlord's
     * replanting puts it back unripe and charges a seed for it. Only one of them fires - a crop that
     * has just regrown ripe does not then want replanting on top.
     */
    private static void onCropHarvested(ServerLevel level, ServerPlayer player, BlockPos pos,
                                        BlockState state, CropBlock crop) {
        if (!crop.isMaxAge(state)) return;

        if (SkillBonuses.roll(player, ModSkillBonuses.CROP_REGROW)) {
            replace(level, pos, state);
            return;
        }

        if (SkillBonuses.has(player, ModSkillBonuses.HOE_REPLANT)) hoeReplant(level, player, pos, state, crop);
    }

    /**
     * Forestlord: harvesting with a hoe puts the row back in the ground behind you.
     *
     * Paid for out of the player's own seeds rather than free, which is what keeps it a convenience
     * rather than an infinite farm - the seed comes off the harvest that just dropped, so a field
     * breaks even and a bad harvest still costs you. Requires the hoe: the node is about working a
     * field properly, and a player punching wheat is not doing that.
     */
    private static void hoeReplant(ServerLevel level, ServerPlayer player, BlockPos pos,
                                   BlockState state, CropBlock crop) {
        if (!(player.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof HoeItem)) return;

        // Whatever this crop is planted from - seeds for wheat and beetroot, the vegetable itself for
        // carrots and potatoes. Asked of the block rather than listed here, so any crop the game or a
        // mod adds is handled without an entry.
        ItemStack seed = crop.getCloneItemStack(level, pos, state);
        if (seed.isEmpty()) return;

        int slot = findSeed(player, seed);
        if (slot < 0) return;

        player.getInventory().removeItem(slot, 1);
        replace(level, pos, crop.defaultBlockState());
    }

    private static int findSeed(ServerPlayer player, ItemStack seed) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (ItemStack.isSameItem(player.getInventory().getItem(slot), seed)) return slot;
        }
        return -1;
    }

    /**
     * Puts a block back where one is about to be broken.
     *
     * Queued for the next tick because the break that triggered this has not actually happened yet -
     * anything placed now is simply the block that is about to be removed.
     */
    private static void replace(ServerLevel level, BlockPos pos, BlockState state) {
        level.getServer().execute(() -> {
            if (level.getBlockState(pos).isAir() && state.canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, state);
            }
        });
    }

    /** Direct Harvest and the doubling in SkillGatheringBonuses both want this shape. */
    private static void harvest(ServerLevel level, ServerPlayer player, BlockPos pos,
                                BlockState state, ItemStack tool) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
        boolean direct = SkillBonuses.has(player, ModSkillBonuses.DIRECT_HARVEST);

        for (ItemStack stack : drops) {
            if (direct && player.getInventory().add(stack)) continue;
            Block.popResource(level, pos, stack);
        }
    }

    // ---- Standing among growing things ----

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % GROWTH_INTERVAL_TICKS != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        double aura = SkillBonuses.get(player, ModSkillBonuses.GROWTH_AURA);
        if (aura <= 0.0) return;

        applyGrowthAura(level, player, aura);
    }

    /**
     * Green Thumb: things grow better where this player stands.
     *
     * Expressed as extra random ticks handed to blocks that already know how to grow, rather than as
     * bone meal - bone meal skips a crop straight to ripe, which would make a Foraging node into an
     * unlimited farm. This nudges the clock instead, and only for blocks that agree they are growable.
     */
    private static void applyGrowthAura(ServerLevel level, ServerPlayer player, double aura) {
        int attempts = (int) Math.max(1, Math.round(aura * 8));
        BlockPos centre = player.blockPosition();

        for (int i = 0; i < attempts; i++) {
            BlockPos pos = centre.offset(
                    level.random.nextInt(GROWTH_RADIUS * 2 + 1) - GROWTH_RADIUS,
                    level.random.nextInt(3) - 1,
                    level.random.nextInt(GROWTH_RADIUS * 2 + 1) - GROWTH_RADIUS);

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BonemealableBlock growable)) continue;
            if (!growable.isValidBonemealTarget(level, pos, state, false)) continue;

            state.randomTick(level, pos, level.random);
        }
    }

    // ---- Eating ----

    /**
     * Wild Larder and Bountiful, both of which happen at the moment something is eaten.
     *
     * Scoped to what a forager actually gathers rather than to food in general: a node about living
     * off the land should not be quietly improving a golden carrot bought from a villager.
     */
    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack food = event.getItem();
        if (!food.isEdible() || !isForaged(food)) return;

        double extra = SkillBonuses.get(player, ModSkillBonuses.FORAGE_NUTRITION);
        var properties = food.getFoodProperties(player);
        if (extra > 0.0 && properties != null) {
            player.getFoodData().eat(
                    (int) Math.round(properties.getNutrition() * extra),
                    (float) (properties.getSaturationModifier() * extra));
        }

        if (SkillBonuses.roll(player, ModSkillBonuses.FOOD_CONSERVATION)) {
            // Handed back after vanilla has already taken it, which is the only point the count is
            // settled - returning a copy of the stack as the result puts the item back untouched.
            ItemStack returned = event.getResultStack();
            returned.grow(1);
            event.setResultStack(returned);
        }
    }

    /** Whether this is something you pick rather than something you buy or butcher. */
    private static boolean isForaged(ItemStack food) {
        return food.is(ItemTags.LEAVES) || food.is(Items.APPLE) || food.is(Items.SWEET_BERRIES)
                || food.is(Items.GLOW_BERRIES) || food.is(Items.CARROT) || food.is(Items.POTATO)
                || food.is(Items.BAKED_POTATO) || food.is(Items.BEETROOT) || food.is(Items.MELON_SLICE)
                || food.is(Items.CHORUS_FRUIT) || food.is(Items.DRIED_KELP) || food.is(Items.BREAD)
                || food.is(Items.COOKIE) || food.is(Items.PUMPKIN_PIE) || food.is(Items.BEETROOT_SOUP)
                || food.is(Items.MUSHROOM_STEW) || food.is(Items.SUSPICIOUS_STEW);
    }
}
