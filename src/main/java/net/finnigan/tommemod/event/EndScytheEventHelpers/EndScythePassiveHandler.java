package net.finnigan.tommemod.event.EndScytheEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * End Scythe passive: while in the End dimension, 1.2x MAX_HEALTH and 1.2x MOVEMENT_SPEED
 * (dimension-gated only, mirroring how WarFlammerPassiveHandler treats its Nether health/speed buff —
 * not tied to actually holding the sword). The spec's "mana" mention is a no-op: this mod has no mana
 * system anywhere, so it's treated as leftover spec text with nothing to implement.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EndScythePassiveHandler {

    private static final UUID END_SPEED_UUID = UUID.fromString("9d3f1c20-5555-4b3f-8a1d-000000000060");
    private static final UUID END_HEALTH_UUID = UUID.fromString("9d3f1c20-5555-4b3f-8a1d-000000000061");
    private static final double END_BONUS = 0.2; // net 1.2x, MULTIPLY_TOTAL

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;

        boolean inEnd = level.dimension() == Level.END;
        applyOrRemove(player, Attributes.MOVEMENT_SPEED, END_SPEED_UUID, END_BONUS, inEnd);
        applyOrRemove(player, Attributes.MAX_HEALTH, END_HEALTH_UUID, END_BONUS, inEnd);
    }

    private static void applyOrRemove(Player player, Attribute attribute,
                                       UUID uuid, double amount, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        if (shouldHave) {
            if (instance.getModifier(uuid) == null) {
                instance.addTransientModifier(new AttributeModifier(uuid, "End Scythe end buff",
                        amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            if (instance.getModifier(uuid) != null) {
                instance.removeModifier(uuid);
            }
        }
    }
}
