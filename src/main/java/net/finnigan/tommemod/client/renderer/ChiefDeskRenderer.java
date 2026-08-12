package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.block.entity.ChiefDeskBlockEntity;
import net.finnigan.tommemod.client.model.ChiefDeskModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class ChiefDeskRenderer extends GeoBlockRenderer<ChiefDeskBlockEntity> {

    public ChiefDeskRenderer(BlockEntityRendererProvider.Context context) {
        super(new ChiefDeskModel<>());
    }
}
