package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Mining tree's own behaviour, as opposed to the plain doubling every gathering skill shares.
 *
 * The theme these are all written against is that a Miner's advantage is in the rock rather than in
 * the pickaxe: what the vein does when it is struck, what the dark does to somebody used to working
 * in it, what a tool does at the end of its life. Where two of these could have been the same number
 * with a different name - a second ore drop, and a second ore drop that is bigger - they are kept
 * apart by when they fire rather than by how much they give.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillMiningBonuses {

    private static final UUID DARKNESS_SPEED_MODIFIER = UUID.fromString("a17e35c9-06b4-4d82-91f7-3e5c8a2b60d4");

    /** At or below this light level a miner counts as working in the dark. */
    private static final int DARK_LIGHT_LEVEL = 3;
    /** Most blocks one strike may follow a vein through, so a chunk-wide seam is not one click. */
    private static final int VEIN_LIMIT = 12;
    /** Depth at which the deep-ore bonus is paying out in full. */
    private static final int FULL_DEPTH = -50;
    /** Depth at which it starts counting for anything at all. */
    private static final int SURFACE_DEPTH = 40;
    /** How many times over a mother lode pays. */
    private static final int MOTHERLODE_MULTIPLIER = 4;
    /** How often dropped items are swept up. Four times a second is smooth without being a scan loop. */
    private static final int MAGNET_INTERVAL_TICKS = 5;

    // ---- Steady Stance: the rock does not care that you are swimming ----

    /**
     * Vanilla quarters a break for being in water without Aqua Affinity and quarters it again for
     * having no floor, which between them is what makes mining out a flooded shaft take all evening.
     * Multiplied back rather than recalculated, so whatever else has already spoken for the speed -
     * haste, the tool, Mining's own bonus - is left as it was.
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        double ignore = SkillBonuses.reduction(player, ModSkillBonuses.MINING_PENALTY_IGNORE);
        if (ignore <= 0.0) return;

        boolean underwater = player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                && EnchantmentHelper.getEnchantmentLevel(Enchantments.AQUA_AFFINITY, player) == 0;
        boolean airborne = !player.onGround();
        if (!underwater && !airborne) return;

        // Each penalty vanilla applied is a division by five; giving back a fraction of one means
        // multiplying by up to five again.
        float recovered = 1.0F;
        if (underwater) recovered *= (float) (1.0 + 4.0 * ignore);
        if (airborne) recovered *= (float) (1.0 + 4.0 * ignore);
        event.setNewSpeed(event.getNewSpeed() * recovered);
    }

    // ---- What happens when an ore is struck ----

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.isCanceled() || player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(Tags.Blocks.ORES)) return;

        BlockPos origin = event.getPos();
        ItemStack tool = player.getItemBySlot(EquipmentSlot.MAINHAND);

        if (rollDepthBonus(player, origin)) {
            dropAgain(level, origin, event.getState(), player, tool, 1);
        }
        if (SkillBonuses.roll(player, ModSkillBonuses.MOTHERLODE_CHANCE)) {
            dropAgain(level, origin, event.getState(), player, tool, MOTHERLODE_MULTIPLIER);
            level.levelEvent(2001, origin, Block.getId(event.getState()));
        }
        if (SkillBonuses.roll(player, ModSkillBonuses.VEIN_CHANCE)) {
            followVein(level, player, origin, event.getState(), tool);
        }
    }

    /**
     * Deep Veins, which is worth nothing at the surface and everything at the bottom.
     *
     * A depth bonus rather than a flat one because the node is about where you are working, and
     * because a Mining tree whose every ore node is another few percent everywhere is the thing this
     * was rewritten to stop being.
     */
    private static boolean rollDepthBonus(ServerPlayer player, BlockPos pos) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.DEPTH_BONUS);
        if (bonus <= 0.0) return false;

        double depth = (SURFACE_DEPTH - pos.getY()) / (double) (SURFACE_DEPTH - FULL_DEPTH);
        double scaled = bonus * Math.max(0.0, Math.min(1.0, depth));
        return scaled > 0.0 && player.getRandom().nextDouble() < scaled;
    }

    /**
     * Re-rolls the block's own loot table and drops it again, optionally several times over.
     *
     * Going back through the loot table rather than copying what already dropped is what keeps silk
     * touch and fortune honest - a silk-touched ore doubles into two ore blocks, not into raw metal
     * the tool was never going to give.
     */
    private static void dropAgain(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                  ItemStack tool, int times) {
        for (int i = 0; i < times; i++) {
            Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool)
                    .forEach(stack -> popSmelted(level, pos, player, stack, tool));
        }
    }

    /**
     * Kiln Grip: ore that comes out of the ground already smelted.
     *
     * Left alone entirely under Silk Touch, where the player has gone out of their way to ask for the
     * block itself and a furnace would be destroying exactly what they wanted.
     */
    private static void popSmelted(ServerLevel level, BlockPos pos, ServerPlayer player,
                                   ItemStack stack, ItemStack tool) {
        boolean silkTouched = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0;
        if (!silkTouched && SkillBonuses.roll(player, ModSkillBonuses.AUTO_SMELT)) {
            ItemStack smelted = smeltingResult(level, stack);
            if (!smelted.isEmpty()) {
                smelted.setCount(stack.getCount());
                Block.popResource(level, pos, smelted);
                return;
            }
        }
        Block.popResource(level, pos, stack);
    }

    private static ItemStack smeltingResult(ServerLevel level, ItemStack input) {
        return level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), level)
                .map(recipe -> recipe.getResultItem(level.registryAccess()).copy())
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Follow the Vein: one strike takes the seam with it.
     *
     * Breadth-first from the block that was struck and stopping at {@link #VEIN_LIMIT}, so a lucky
     * swing in a large deepslate seam is a good haul rather than the entire seam at once. Only ores of
     * the same kind are followed - a diamond next to the iron you hit stays where it is.
     */
    private static void followVein(ServerLevel level, ServerPlayer player, BlockPos origin,
                                   BlockState struck, ItemStack tool) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        int taken = 0;

        while (!queue.isEmpty() && taken < VEIN_LIMIT) {
            BlockPos current = queue.poll();
            for (BlockPos neighbour : around(current)) {
                if (taken >= VEIN_LIMIT || !seen.add(neighbour)) continue;

                BlockState state = level.getBlockState(neighbour);
                if (!state.is(struck.getBlock())) continue;

                dropAgain(level, neighbour, state, player, tool, 1);
                level.removeBlock(neighbour, false);
                level.levelEvent(2001, neighbour, Block.getId(state));
                queue.add(neighbour);
                taken++;
            }
        }
    }

    private static List<BlockPos> around(BlockPos centre) {
        List<BlockPos> neighbours = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) neighbours.add(centre.offset(dx, dy, dz));
                }
            }
        }
        return neighbours;
    }

    // ---- Being underground ----

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;
        Player player = event.player;

        applyDarknessSpeed(player);
        if (player.tickCount % MAGNET_INTERVAL_TICKS == 0) applyItemMagnet(player);
    }

    /** Miner's Lamp: somebody who works in the dark stops being slowed by it. */
    private static void applyDarknessSpeed(Player player) {
        double bonus = SkillBonuses.get(player, ModSkillBonuses.DARKNESS_SPEED);
        boolean dark = bonus > 0.0
                && player.level().getMaxLocalRawBrightness(player.blockPosition()) <= DARK_LIGHT_LEVEL;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(DARKNESS_SPEED_MODIFIER);
        if (!dark) {
            if (existing != null) speed.removeModifier(DARKNESS_SPEED_MODIFIER);
            return;
        }
        if (existing != null && existing.getAmount() == bonus) return;

        if (existing != null) speed.removeModifier(DARKNESS_SPEED_MODIFIER);
        speed.addTransientModifier(new AttributeModifier(DARKNESS_SPEED_MODIFIER, "Skill darkness speed",
                bonus, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /**
     * Long Reach: drops come to the miner rather than the miner going round the floor after them.
     *
     * Nudged toward the player rather than teleported, so an item still has to travel and a hopper or
     * a lava flow in between still gets its say.
     */
    private static void applyItemMagnet(Player player) {
        double radius = SkillBonuses.get(player, ModSkillBonuses.ITEM_MAGNET);
        if (radius <= 0.0) return;

        AABB reach = player.getBoundingBox().inflate(radius);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, reach)) {
            if (item.hasPickUpDelay() || !item.isAlive()) continue;

            Vec3 toPlayer = player.position().add(0.0, 0.5, 0.0).subtract(item.position());
            if (toPlayer.lengthSqr() < 0.5) continue;
            item.setDeltaMovement(item.getDeltaMovement().add(toPlayer.normalize().scale(0.12)));
        }
    }

    /** Braced: the two ways a tunnel kills somebody who is not paying attention. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getSource().is(DamageTypes.IN_WALL) && !event.getSource().is(DamageTypes.FALLING_BLOCK)) {
            return;
        }

        double reduction = SkillBonuses.reduction(player, ModSkillBonuses.SPELUNKER_GUARD);
        if (reduction <= 0.0) return;

        event.setAmount(event.getAmount() * (float) (1.0 - reduction));
    }
}
