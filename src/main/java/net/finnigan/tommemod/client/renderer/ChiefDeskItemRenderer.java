package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.client.model.ChiefDeskModel;
import net.finnigan.tommemod.item.custom.ChiefDeskBlockItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class ChiefDeskItemRenderer extends GeoItemRenderer<ChiefDeskBlockItem> {

    public ChiefDeskItemRenderer() {
        super(new ChiefDeskModel<>());
    }
}
