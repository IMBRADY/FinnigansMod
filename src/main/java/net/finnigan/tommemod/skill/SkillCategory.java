package net.finnigan.tommemod.skill;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * A heading in the skill list down the left of the screen - GENERAL, CLASS-BASED and so on.
 *
 * Data rather than an enum so that a datapack adding a fifteenth skill can put it under a heading of
 * its own without touching the mod.
 *
 * @param exclusive whether picking one skill under this heading closes off the others. Class-Based is
 *                  the only one that sets it, and it is what makes a class a commitment rather than a
 *                  shopping list - see {@code SkillService.classLock}. Categories default to false, so
 *                  a datapack heading behaves like General until it says otherwise.
 */
public record SkillCategory(ResourceLocation id, String displayName, int sortOrder, boolean exclusive) {

    public static SkillCategory parse(ResourceLocation id, JsonObject json) {
        return new SkillCategory(id,
                GsonHelper.getAsString(json, "display_name", id.getPath()),
                GsonHelper.getAsInt(json, "sort_order", 0),
                GsonHelper.getAsBoolean(json, "exclusive", false));
    }
}
