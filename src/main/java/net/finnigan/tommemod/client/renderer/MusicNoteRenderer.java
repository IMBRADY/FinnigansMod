package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.MusicNoteEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

public class MusicNoteRenderer extends ThrownItemRenderer<MusicNoteEntity> {
    public MusicNoteRenderer(EntityRendererProvider.Context context) {
        super(context, 2.5F, true); // size
    }
}