package net.finnigan.tommemod.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.finnigan.tommemod.TommeMod; // your MODID holder, adjust to actual class

public class ModTags {
    public static class Items {
        public static final TagKey<Item> HEAD_ACCESSORIES = tag("head_accessories");
        public static final TagKey<Item> ELYTRA_LIKE = tag("elytra_like");
        public static final TagKey<Item> TOTEM_ACCESSORIES = tag("totem_accessories");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, new ResourceLocation(TommeMod.MOD_ID, name));
        }
    }
}