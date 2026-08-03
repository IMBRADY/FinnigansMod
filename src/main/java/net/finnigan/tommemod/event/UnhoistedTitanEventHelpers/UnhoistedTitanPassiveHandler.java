package net.finnigan.tommemod.event.UnhoistedTitanEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.item.custom.FireKatanaItem;
import net.finnigan.tommemod.item.custom.UnhoistedTitanItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Unhoisted Titan passive: while held (main or offhand), full knockback resistance, a permanent swim
 * speed boost, and bonus armor that scales with how many enemies are crowding the wielder - one point
 * per {@link #ENEMIES_PER_ARMOR_POINT} enemies, capped at {@link #MAX_BONUS_ARMOR}, and further
 * clamped so the wielder's total never exceeds {@link #ARMOR_HARD_CAP}.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class UnhoistedTitanPassiveHandler {

    private static final UUID KNOCKBACK_UUID = UUID.fromString("9d3f1c20-6666-4b3f-8a1d-000000000060");
    private static final UUID SWIM_SPEED_UUID = UUID.fromString("9d3f1c20-6666-4b3f-8a1d-000000000061");
    private static final UUID ARMOR_UUID = UUID.fromString("9d3f1c20-6666-4b3f-8a1d-000000000062");

    private static final int ENEMIES_PER_ARMOR_POINT = 5;
    private static final double MAX_BONUS_ARMOR = 10.0;
    private static final double ARMOR_HARD_CAP = 24.0;

    // The enemy headcount only matters at human reaction speed, and it is the one part of this
    // passive that costs an entity scan, so it deliberately lags the other two modifiers.
    private static final int ARMOR_RECHECK_INTERVAL_TICKS = 10;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        boolean held = UnhoistedTitanItem.isHeldBy(player);

        applyOrRemove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_UUID, "Unhoisted Titan knockback resistance",
                held ? 1.0 : 0.0, AttributeModifier.Operation.ADDITION, held);
        applyOrRemove(player, ForgeMod.SWIM_SPEED.get(), SWIM_SPEED_UUID, "Unhoisted Titan swim speed",
                ModConfig.TITAN_SWIM_SPEED_BONUS.get(), AttributeModifier.Operation.MULTIPLY_TOTAL, held);

        if (!held) {
            applyOrRemove(player, Attributes.ARMOR, ARMOR_UUID, "Unhoisted Titan crowd armor",
                    0.0, AttributeModifier.Operation.ADDITION, false);
            return;
        }
        if (player.tickCount % ARMOR_RECHECK_INTERVAL_TICKS == 0) {
            updateCrowdArmor(player);
        }
    }

    private static void updateCrowdArmor(Player player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor == null) return;

        // Read the wielder's armor with our own contribution taken back out, so the hard cap is
        // measured against their real gear rather than against last tick's bonus.
        armor.removeModifier(ARMOR_UUID);
        double baseArmor = armor.getValue();

        double radius = ModConfig.TITAN_ARMOR_SCAN_RADIUS_BLOCKS.get();
        List<LivingEntity> enemies = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e.isAlive() && FireKatanaItem.isValidFireTarget(e) && e.distanceTo(player) <= radius);

        double bonus = Math.min(MAX_BONUS_ARMOR, enemies.size() / ENEMIES_PER_ARMOR_POINT);
        bonus = Math.max(0.0, Math.min(bonus, ARMOR_HARD_CAP - baseArmor));

        if (bonus > 0) {
            armor.addTransientModifier(new AttributeModifier(
                    ARMOR_UUID, "Unhoisted Titan crowd armor", bonus, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void applyOrRemove(Player player, Attribute attribute, UUID id, String name,
                                      double amount, AttributeModifier.Operation operation, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        if (shouldHave) {
            AttributeModifier existing = instance.getModifier(id);
            if (existing != null && existing.getAmount() == amount) return;
            if (existing != null) instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        } else if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }
}
