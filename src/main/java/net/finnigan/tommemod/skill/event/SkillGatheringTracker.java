package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.SkillService;
import net.finnigan.tommemod.skill.xp.ModSkillActions;
import net.finnigan.tommemod.skill.xp.SkillAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns breaking blocks into skill actions.
 *
 * One action for every break, carrying the block and the tool, and it is the tree files that decide
 * which of Mining, Excavation and Foraging - or all three - want to hear about any given one. That is
 * why there is no per-skill filtering here: sorting stone from dirt from logs in Java would put the
 * one judgement call that ought to be a tag in a datapack into the middle of an event handler.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillGatheringTracker {

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.isCanceled() || player.isCreative()) return;

        SkillService.award(player, SkillAction.once(ModSkillActions.BLOCK_BROKEN)
                .withBlock(event.getState())
                .withTool(player.getItemBySlot(EquipmentSlot.MAINHAND)));
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Amount is the stack size, so crafting a stack of something is worth more than one of it.
        SkillService.award(player, SkillAction.of(ModSkillActions.ITEM_CRAFTED, event.getCrafting().getCount())
                .withItem(event.getCrafting()));
    }
}
