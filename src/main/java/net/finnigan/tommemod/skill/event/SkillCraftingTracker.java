package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.SkillService;
import net.finnigan.tommemod.skill.xp.ModSkillActions;
import net.finnigan.tommemod.skill.xp.SkillAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns the workbench-and-pasture half of the game into skill actions: breeding, taming, feeding and
 * repairing. Enchanting is posted from PlayerEnchantmentMixin, which is the only exact hook for it.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillCraftingTracker {

    @SubscribeEvent
    public static void onBred(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;

        SkillService.award(player, SkillAction.once(ModSkillActions.ANIMAL_BRED)
                .withEntity(event.getParentA()));
    }

    @SubscribeEvent
    public static void onTamed(AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer player)) return;

        SkillService.award(player, SkillAction.once(ModSkillActions.ANIMAL_TAMED)
                .withEntity(event.getAnimal()));
    }

    /**
     * Feeding an animal, whether or not it was ready to breed.
     *
     * Worth its own action because breeding alone is a poor teacher: it only pays on the tick two
     * animals happen to pair off, which makes the early levels of Husbandry turn on luck rather than
     * on the work of raising a herd.
     */
    @SubscribeEvent
    public static void onAnimalFed(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof Animal animal)) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !animal.isFood(held)) return;

        SkillService.award(player, SkillAction.once(ModSkillActions.ANIMAL_FED).withEntity(animal));
    }

    @SubscribeEvent
    public static void onRepaired(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Paid by how much of the item was actually mended, so topping up a nearly-full tool is worth
        // far less than bringing a ruined one back. That is both the behaviour the skill should
        // reward and what closes the "repair one point, repeat forever" loop.
        int mended = event.getLeft().getDamageValue() - event.getOutput().getDamageValue();
        SkillService.award(player, SkillAction.of(ModSkillActions.ITEM_REPAIRED, Math.max(1, mended))
                .withItem(event.getOutput()));
    }

    /** Posted from PlayerEnchantmentMixin, which has the item and the level cost the table charged. */
    public static void onEnchanted(ServerPlayer player, ItemStack enchanted, int levelCost) {
        SkillService.award(player, SkillAction.of(ModSkillActions.ITEM_ENCHANTED, Math.max(1, levelCost))
                .withItem(enchanted));
    }
}
