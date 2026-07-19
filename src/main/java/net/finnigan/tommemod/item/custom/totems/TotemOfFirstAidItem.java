package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class TotemOfFirstAidItem extends Item implements ITotemEffect {

    private static final double RADIUS = 20.0;
    private static final float HEAL_AMOUNT = 2.0F; // 1 heart

    public TotemOfFirstAidItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        Level level = player.level();
        if (level.isClientSide) return;
        if (level.getGameTime() % 30 != 0) return; // every 1.5s (30 ticks)

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