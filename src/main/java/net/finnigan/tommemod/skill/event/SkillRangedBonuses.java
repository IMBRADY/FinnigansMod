package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Archery and Marksmanship bonuses that act on the shot itself rather than on its damage.
 *
 * Arrows are adjusted as they enter the world rather than at the moment of firing, because that is
 * the one place every way of launching one converges - a bow, a crossbow, a dispenser-fed
 * multishot - so nothing has to be special-cased per weapon.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillRangedBonuses {

    @SubscribeEvent
    public static void onArrowSpawned(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player)) return;

        double velocity = SkillBonuses.get(player, ModSkillBonuses.ARROW_VELOCITY);
        if (velocity > 0.0) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.0 + velocity));
        }

        // Steadiness is a flatter arc, not a floating one: even at full rank the arrow still falls,
        // it just falls less. Cancelling gravity outright would make a bow a hitscan weapon.
        double steadiness = SkillBonuses.reduction(player, ModSkillBonuses.ARROW_STEADINESS);
        if (steadiness > 0.0) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().add(0.0, steadiness * 0.05, 0.0));
        }
    }

    /**
     * Faster bow draws and crossbow reloads.
     *
     * Both are measured by how long the item has been in use, so skipping ticks off the countdown is
     * what "faster" means here. Guarded to leave at least one tick, since a use duration that reaches
     * its target on the first tick is indistinguishable from a click.
     */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;

        var item = event.getItem().getItem();
        if (!(item instanceof BowItem) && !(item instanceof CrossbowItem)) return;

        double speed = SkillBonuses.reduction(player, ModSkillBonuses.DRAW_SPEED);
        if (speed <= 0.0) return;

        // Consume an extra tick of the draw some fraction of the time - at 0.25, one tick in four
        // counts double, which is a 25% faster draw without ever skipping past the release point.
        if (player.getRandom().nextDouble() < speed && event.getDuration() > 1) {
            event.setDuration(event.getDuration() - 1);
        }
    }
}
