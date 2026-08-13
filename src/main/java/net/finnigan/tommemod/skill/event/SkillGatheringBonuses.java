package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The Mining, Excavation and Foraging bonuses.
 *
 * The three double-drop keys are separate rather than one shared key because they are three different
 * skills' rewards, and a player who has poured everything into Mining should not find themselves
 * doubling logs. Which blocks each one covers is decided by tag here rather than in the tree files,
 * because it is the same question the vanilla tool tags already answer.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillGatheringBonuses {

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        double bonus = SkillBonuses.get(event.getEntity(), ModSkillBonuses.MINING_SPEED);
        if (bonus > 0.0) event.setNewSpeed(event.getNewSpeed() * (float) (1.0 + bonus));
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.isCanceled() || player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        double xpBonus = SkillBonuses.get(player, ModSkillBonuses.BLOCK_XP_BONUS);
        if (xpBonus > 0.0 && event.getExpToDrop() > 0) {
            event.setExpToDrop((int) Math.round(event.getExpToDrop() * (1.0 + xpBonus)));
        }

        ResourceLocation key = doubleDropKey(event.getState());
        if (key == null || !SkillBonuses.roll(player, key)) return;

        // Silk touch and fortune are already baked into what getDrops returns, so rolling the loot
        // table a second time gives a doubling that respects the tool rather than one that quietly
        // launders a silk-touched ore into two raw ones.
        ItemStack tool = player.getItemBySlot(EquipmentSlot.MAINHAND);
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        Block.getDrops(event.getState(), level, pos, blockEntity, player, tool)
                .forEach(stack -> Block.popResource(level, pos, stack));
    }

    /** Which skill's doubling, if any, covers this block. */
    @Nullable
    private static ResourceLocation doubleDropKey(BlockState state) {
        if (state.is(Tags.Blocks.ORES)) return ModSkillBonuses.ORE_DOUBLE_DROP;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.CROPS)) {
            return ModSkillBonuses.FORAGING_DOUBLE_DROP;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return ModSkillBonuses.EXCAVATION_DOUBLE_DROP;
        // Everything else - plain stone, wool, glass - is deliberately not doubled. These bonuses are
        // meant to reward gathering the things worth gathering, not to multiply cobblestone.
        return null;
    }
}
