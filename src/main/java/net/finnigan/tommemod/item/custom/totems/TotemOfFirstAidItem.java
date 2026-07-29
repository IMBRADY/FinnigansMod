package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TotemOfFirstAidItem extends Item implements ITotemEffect {

    private static final double RADIUS = 20.0;
    private static final float HEAL_AMOUNT = 2.0F; // 1 heart
    private static final long HEAL_INTERVAL_TICKS = 30; // 1.5s

    // Per-player cooldown tracked by elapsed game time, not a modulo check - the dispatcher that calls
    // onPlayerTick isn't itself aligned to level.getGameTime(), so a "% 30" check here could line up
    // with every call (healing far too often) or never (healing not at all) depending on world state.
    private static final Map<UUID, Long> lastHealGameTime = new HashMap<>();

    public TotemOfFirstAidItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("You and allies in close proximity recieve passive healing").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        Level level = player.level();
        if (level.isClientSide) return;

        long now = level.getGameTime();
        Long last = lastHealGameTime.get(player.getUUID());
        if (last != null && now - last < HEAL_INTERVAL_TICKS) return;
        lastHealGameTime.put(player.getUUID(), now);

        AABB range = player.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, range,
                TotemOfFirstAidItem::isHealTarget);

        for (LivingEntity entity : nearby) {
            entity.heal(HEAL_AMOUNT);
        }
        player.heal(HEAL_AMOUNT); // ensure the wearer heals even if somehow excluded from the scan
    }

    private static boolean isHealTarget(LivingEntity entity) {
        if (entity instanceof Player) return true;
        if (entity instanceof Villager) return true;
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) return true;
        return entity.getTags().contains(SoulSummoner.SOUL_ALLY_TAG);
    }
}