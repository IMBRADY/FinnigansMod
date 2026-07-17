package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.entity.custom.JellyfishEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class JellyfishModel extends DefaultedEntityGeoModel<JellyfishEntity> {
        public JellyfishModel() {
            super(new ResourceLocation("tommemod", "jellyfish"));
        }
}