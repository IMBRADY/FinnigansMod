package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.event.SkillArcheryBonuses;
import net.finnigan.tommemod.skill.event.SkillDefenseBonuses;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;

/**
 * Keeps the two mobility nodes out of the player's field of view.
 *
 * Steady Hands and Lighter Shields both work by adding movement speed back, because vanilla slows a
 * drawing or blocking player through a movement multiplier that cannot be cancelled - see
 * {@code SkillArcheryBonuses#applyDrawMobility}. Vanilla's FOV is a function of that same attribute,
 * so compensating for the slowdown also punched the view outward every time a bow came up: the node
 * that was supposed to stop a shot feeling sluggish made aiming one visibly worse.
 *
 * What is fixed here is only the view. The speed the modifiers grant is untouched, so a player with
 * either node still moves at full pace; the camera simply stops being told about it. The FOV is
 * recomputed from vanilla's own formula with those two modifiers taken back out, rather than being
 * clamped or overridden, so sprinting, flying, Speed potions and the bow's own zoom all still read
 * exactly as they normally do.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SkillFovHandler {

    /** The speed modifiers the camera is not to see. */
    private static final Set<UUID> HIDDEN_FROM_FOV = Set.of(
            SkillArcheryBonuses.DRAW_MOBILITY_MODIFIER,
            SkillDefenseBonuses.BLOCK_MOBILITY_MODIFIER);

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || !anyHiddenModifier(speed)) return;

        float walking = player.getAbilities().getWalkingSpeed();
        if (walking <= 0.0F) return;

        double with = speed.getValue();
        double without = valueWithoutHidden(speed);

        // Vanilla's factor is (speed / walkingSpeed + 1) / 2. Dividing the one out and multiplying the
        // other in leaves every other term of the formula - the bow's zoom especially - intact.
        double factorWith = (with / walking + 1.0) / 2.0;
        double factorWithout = (without / walking + 1.0) / 2.0;
        if (factorWith <= 0.0) return;

        float corrected = (float) (event.getFovModifier() * (factorWithout / factorWith));
        if (Float.isNaN(corrected) || Float.isInfinite(corrected)) return;

        // Re-applies the interpolation the event's constructor does, so the FOV Effects slider keeps
        // meaning what it means everywhere else.
        event.setNewFovModifier((float) Mth.lerp(
                Minecraft.getInstance().options.fovEffectScale().get(), 1.0F, corrected));
    }

    private static boolean anyHiddenModifier(AttributeInstance speed) {
        for (UUID id : HIDDEN_FROM_FOV) {
            if (speed.getModifier(id) != null) return true;
        }
        return false;
    }

    /**
     * What movement speed would be without the two mobility modifiers.
     *
     * Vanilla's own order of operations, repeated rather than approximated: additions first, then each
     * multiply_base as a share of that subtotal, then multiply_total compounding on the result. Simply
     * dividing the granted amount out would be wrong the moment a Speed potion is also running.
     */
    private static double valueWithoutHidden(AttributeInstance speed) {
        double base = speed.getBaseValue();
        for (AttributeModifier modifier : speed.getModifiers(AttributeModifier.Operation.ADDITION)) {
            base += modifier.getAmount();
        }

        double value = base;
        for (AttributeModifier modifier : speed.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) {
            if (HIDDEN_FROM_FOV.contains(modifier.getId())) continue;
            value += base * modifier.getAmount();
        }
        for (AttributeModifier modifier : speed.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL)) {
            if (HIDDEN_FROM_FOV.contains(modifier.getId())) continue;
            value *= 1.0 + modifier.getAmount();
        }
        return value;
    }
}
