package net.finnigan.tommemod.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.finnigan.tommemod.TommeMod; // your MODID holder, adjust to actual class

public class ModTags {
    public static class Items {
        public static final TagKey<Item> HEAD_ACCESSORIES = tag("head_accessories");
        public static final TagKey<Item> ELYTRA_LIKE = tag("elytra_like");
        public static final TagKey<Item> TOTEM_ACCESSORIES = tag("totem_accessories");
        public static final TagKey<Item> LIFESTEAL_WEAPONS = tag("lifesteal_weapons");
        public static final TagKey<Item> POISON_WEAPONS = tag("poison_weapons");
        public static final TagKey<Item> UNIQUE = tag("unique");
        /** Cleavers, which are plain ModdedSwordItem registrations with no class of their own. */
        public static final TagKey<Item> CLEAVERS = tag("cleavers");
        /**
         * What Melee's Piercing Strike works with: blades, daggers and uniques.
         *
         * A tag rather than a class test because the three do not share one - DaggerItem deliberately
         * extends Item rather than SwordItem, to escape SwordItem's hardcoded sweep.
         */
        public static final TagKey<Item> PIERCING_WEAPONS = tag("piercing_weapons");
        /**
         * What Heroblade may be put on: blades, cleavers, daggers and uniques.
         *
         * Kept apart from {@link #PIERCING_WEAPONS} rather than reused despite the near-identical
         * membership, because the two answer different questions - one is "what can pierce armor",
         * the other "what counts as a hero's weapon" - and a cleaver belongs to the second only.
         */
        public static final TagKey<Item> HEROBLADE_WEAPONS = tag("heroblade_weapons");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, new ResourceLocation(TommeMod.MOD_ID, name));
        }
    }

    public static class EntityTypes {
        /**
         * Things that ought to be immune to crowd control.
         *
         * Stagger, Shield Bash and Assassin all trivialise a fight they can interrupt or skip, and a
         * boss fight is the one place that matters. Kept as data so a boss added later is covered by
         * one line of JSON rather than by remembering to edit three handlers.
         */
        public static final TagKey<EntityType<?>> BOSSES = tag("bosses");

        private static TagKey<EntityType<?>> tag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(TommeMod.MOD_ID, name));
        }
    }
}