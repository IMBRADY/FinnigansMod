package net.finnigan.tommemod.client;

import net.finnigan.tommemod.client.renderer.GiantSwordRenderer;
import net.finnigan.tommemod.client.renderer.MusicNoteRenderer;
import net.finnigan.tommemod.entity.ModEntityTypes;
import net.finnigan.tommemod.item.custom.LongbowItem;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

@Mod.EventBusSubscriber(modid = "tommemod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
// NOTE: It is very likely that we can merge this file with ClientModEvents, taking note that some functions are bus events and others are subscriber events - brady
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.DYNAMITE.get(),
                ThrownItemRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.MUSIC_NOTE.get(),
                MusicNoteRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.GIANT_SWORD.get(),
                GiantSwordRenderer::new);
    }
}