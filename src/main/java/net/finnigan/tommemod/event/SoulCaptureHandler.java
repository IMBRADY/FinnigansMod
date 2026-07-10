package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.RanseurOfUndeadSwordItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SoulCaptureHandler {

    private static final Random RANDOM = new Random();
    private static final double REFERENCE_HEALTH = 40;
    private static final double BASE_CHANCE = 0.5;
    private static final double MIN_CHANCE = 0.01;
    private static final double MAX_CHANCE = 0.9;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (victim.level().isClientSide) return;
        if (victim.getMobType() != MobType.UNDEAD) return;

        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof RanseurOfUndeadSwordItem)) return;
        if (victim.getAttribute(Attributes.ATTACK_DAMAGE) == null) return;
        if (!RanseurOfUndeadSwordItem.hasRoom(weapon)) {
            return;
        }

        double maxHealth = victim.getMaxHealth();
        double chance = Math.min(MAX_CHANCE, Math.max(MIN_CHANCE, BASE_CHANCE * (REFERENCE_HEALTH / maxHealth))); // If we ever have a mob with more than 1000 health, fix this because it fixes the chance to 1%

        if (RANDOM.nextDouble() > chance) return;

        CompoundTag soulData = new CompoundTag();
        soulData.putString("EntityType", BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString());
        if (victim.hasCustomName()) {
            soulData.putString("CustomName", victim.getCustomName().getString());
        }

        CompoundTag equipment = new CompoundTag();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = victim.getItemBySlot(slot);
            if (!item.isEmpty()) {
                equipment.put(slot.getName(), item.save(new CompoundTag()));
                victim.setItemSlot(slot, ItemStack.EMPTY); // Prevent victim items from dropping on the ground (Maybe change this later because this means mobs wont drop gear?)
            }
        }
        soulData.put("Equipment", equipment);

        RanseurOfUndeadSwordItem.addSoul(weapon, soulData);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "The blade has captured a soul!"), true);
    }
}