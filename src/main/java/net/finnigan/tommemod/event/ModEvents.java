package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.JellyfishRenderer;
import net.finnigan.tommemod.client.renderer.layer.AccessoryElytraLayer;
import net.finnigan.tommemod.client.renderer.layer.AccessoryHeadLayer;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.entity.custom.JellyfishEntity;
import net.finnigan.tommemod.item.custom.LongbowItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tommemod")
public class ModEvents {

    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return; // only touch server-side entity

        if (event.getEntity() instanceof AbstractArrow arrow
                && arrow.getOwner() instanceof Player player) {
            ItemStack bow = player.getUseItem();
            if (bow.getItem() instanceof LongbowItem) {
                arrow.setBaseDamage(arrow.getBaseDamage() * 2.0); // Damage
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(2.0)); // Speed
            }
        }
    }
}
