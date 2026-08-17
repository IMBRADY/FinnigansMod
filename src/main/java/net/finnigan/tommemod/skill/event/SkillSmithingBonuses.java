package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.item.custom.PikeItem;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.core.BlockPos;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Smithing tree: the anvil, the heat, and what a trained hand gets out of both.
 *
 * Every one of these hangs off an event that carries the player who caused it. That is not incidental
 * - a smithing bonus is a property of the smith, and the things it wants to modify (a furnace, an
 * anvil, a workbench) are shared world state that any number of players can be stood at. Hooking
 * "this player took this item out" rather than "this furnace is running" is what makes the answer to
 * "whose bonus applies?" unambiguous on a server with more than one person on it.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillSmithingBonuses {

    /** How far a heat source will reach to keep a smith's gear in repair. */
    private static final int HEAT_RADIUS = 2;
    /** How often that is checked. Heat mending is quoted per second, so this is the second. */
    private static final int HEAT_INTERVAL_TICKS = 20;

    // ---- Bellows: standing at the heat is itself maintenance ----

    /**
     * Mends whatever the smith is holding while they stand at a fire.
     *
     * Deliberately about the player's own held item rather than about the furnace: a furnace is world
     * state that ticks whether anybody is near it and that several players can share, so anything
     * phrased as "furnaces near you run better" has no answer to whose bonus applies, and stutters as
     * people walk past. The heat here is only a condition on a bonus that is applied to one player's
     * own inventory, which is a question with exactly one answer.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;

        Player player = event.player;
        if (player.tickCount % HEAT_INTERVAL_TICKS != 0) return;

        double perSecond = SkillBonuses.get(player, ModSkillBonuses.HEAT_MENDING);
        if (perSecond <= 0.0) return;

        ItemStack held = player.getMainHandItem();
        if (!held.isDamaged()) return;
        if (!isNearHeat(player)) return;

        int mended = (int) perSecond;
        if (player.getRandom().nextDouble() < perSecond - mended) mended++;
        if (mended > 0) held.setDamageValue(Math.max(0, held.getDamageValue() - mended));
    }

    private static boolean isNearHeat(Player player) {
        BlockPos centre = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-HEAT_RADIUS, -HEAT_RADIUS, -HEAT_RADIUS),
                centre.offset(HEAT_RADIUS, HEAT_RADIUS, HEAT_RADIUS))) {
            if (isHeatSource(player.level().getBlockState(pos))) return true;
        }
        return false;
    }

    private static boolean isHeatSource(BlockState state) {
        if (state.is(Blocks.LAVA) || state.is(ModBlocks.OVEN.get())) return true;
        if (state.getBlock() instanceof AbstractFurnaceBlock || state.getBlock() instanceof CampfireBlock) {
            return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
        }
        return false;
    }

    // ---- The anvil ----

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        Player player = event.getEntity();
        ItemStack output = event.getOutput();

        // Master Repair: the anvil stops paying for the smith's work with its own face.
        double preserve = SkillBonuses.reduction(player, ModSkillBonuses.ANVIL_PRESERVE);
        if (preserve > 0.0) event.setBreakChance(event.getBreakChance() * (float) (1.0 - preserve));

        // Forge Sense: vanilla doubles an item's prior-work cost every time it is worked, which is
        // what eventually makes a favourite tool cost more levels than the game will charge and
        // retires it. Rolled back rather than removed - the penalty still grows, just slower.
        double slower = SkillBonuses.reduction(player, ModSkillBonuses.PRIOR_WORK_REDUCTION);
        if (slower > 0.0 && output.getBaseRepairCost() > 0) {
            output.setRepairCost((int) Math.round(output.getBaseRepairCost() * (1.0 - slower)));
        }

        if (SkillBonuses.roll(player, ModSkillBonuses.REFORGE_CHANCE)) reforge(player, output);
    }

    /**
     * Reforging: now and again the work comes out better than it went in.
     *
     * Only ever raises something already on the item, and only within that enchantment's own maximum,
     * so this is a smith finishing a job well rather than an enchanting table with extra steps. An
     * item with nothing on it gets nothing.
     */
    private static void reforge(Player player, ItemStack output) {
        Map<Enchantment, Integer> present = EnchantmentHelper.getEnchantments(output);
        List<Enchantment> raisable = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : present.entrySet()) {
            if (entry.getValue() < entry.getKey().getMaxLevel()) raisable.add(entry.getKey());
        }
        if (raisable.isEmpty()) return;

        Enchantment chosen = raisable.get(player.getRandom().nextInt(raisable.size()));
        present.put(chosen, present.get(chosen) + 1);
        EnchantmentHelper.setEnchantments(present, output);
    }

    // ---- The workbench and the furnace ----

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        // Everlasting: the second one comes off the same effort. Handed to the player rather than
        // written into the result slot, which vanilla has already finished with by this point.
        //
        // Restricted to finished equipment, and that restriction is the whole point rather than
        // flavour: on any recipe at all this is an infinite resource duplicator, because a great many
        // craftables are losslessly reversible. Ingot to block to ingot returns nine for nine, so a
        // chance of a tenth compounds every cycle. Equipment is a dead end - nothing uncrafts a
        // netherite chestplate back into its parts - so a spare is a spare and nothing more.
        if (isFinishedEquipment(crafted) && SkillBonuses.roll(player, ModSkillBonuses.CRAFT_DOUBLE_CHANCE)) {
            give(player, crafted.copy());
        }

        // Edgecraft: a maker's mark. Only on things that can carry one and are not carrying one yet,
        // so this never quietly overwrites an enchantment somebody else put there.
        if (crafted.isEnchantable() && !crafted.isEnchanted()
                && SkillBonuses.roll(player, ModSkillBonuses.CRAFT_ENCHANT_CHANCE)) {
            EnchantmentHelper.enchantItem(player.getRandom(), crafted, 5, false);
        }
    }

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (!SkillBonuses.roll(player, ModSkillBonuses.SMELT_BONUS)) return;

        ItemStack extra = event.getSmelting().copy();
        extra.setCount(1);
        give(player, extra);
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /**
     * Whether something is a finished piece of kit rather than a material on its way to being one.
     *
     * Deliberately narrow. Anything that stacks is a material as far as this is concerned, which rules
     * out the ingot and block recipes that make duplication compound, and what is left - tools,
     * weapons, armor, shields, pikes - is the smith's actual output.
     */
    private static boolean isFinishedEquipment(ItemStack stack) {
        if (stack.getMaxStackSize() > 1) return false;

        Item item = stack.getItem();
        return item instanceof TieredItem || item instanceof ArmorItem
                || item instanceof ShieldItem || item instanceof PikeItem;
    }

    // ---- Hardening ----

    /**
     * How much of a target's armor a cleaver in this player's hand goes through.
     *
     * The cleaver and nothing else. Gated on a tag rather than a class because cleavers are plain
     * {@code ModdedSwordItem} registrations sharing that class with every other modded sword - there is
     * nothing to test with {@code instanceof} that would not also catch the katanas. The tag means a
     * fifth cleaver is one line of JSON rather than a code change.
     */
    public static double armorPierceFor(Player player, ItemStack weapon) {
        if (!weapon.is(ModTags.Items.CLEAVERS)) return 0.0;
        return SkillBonuses.reduction(player, ModSkillBonuses.ARMOR_PIERCE);
    }
}
