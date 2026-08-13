package net.finnigan.tommemod.skill;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * A heading in the skill list down the left of the screen - MOVEMENT, COMBAT and so on.
 *
 * Data rather than an enum so that a datapack adding a fifteenth skill can put it under a heading of
 * its own without touching the mod.
 */
public record SkillCategory(ResourceLocation id, String displayName, int sortOrder) {

    public static SkillCategory parse(ResourceLocation id, JsonObject json) {
        return new SkillCategory(id,
                GsonHelper.getAsString(json, "display_name", id.getPath()),
                GsonHelper.getAsInt(json, "sort_order", 0));
    }
}
