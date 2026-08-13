package net.finnigan.tommemod.skill.xp;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;

/**
 * The complete vocabulary of things the skill system can hear about.
 *
 * Fixed on purpose. A tree file may only draw experience from an action on this list, and each one is
 * posted by exactly one handler in {@code skill.event}. Adding a genuinely new kind of player action
 * means adding a constant here and a handler to post it; re-pointing an existing action at a
 * different skill means editing JSON and nothing else.
 */
public final class ModSkillActions {

    private ModSkillActions() {
    }

    // ---- Movement. Amount is distance in blocks, except JUMP which is one per jump. ----

    public static final ResourceLocation WALK = id("distance/walk");
    public static final ResourceLocation SPRINT = id("distance/sprint");
    public static final ResourceLocation SWIM = id("distance/swim");
    public static final ResourceLocation CLIMB = id("distance/climb");
    public static final ResourceLocation FALL = id("distance/fall");
    public static final ResourceLocation GLIDE = id("distance/glide");
    public static final ResourceLocation SAIL = id("distance/sail");
    public static final ResourceLocation RIDE = id("distance/ride");
    public static final ResourceLocation MINECART = id("distance/minecart");
    public static final ResourceLocation JUMP = id("jump");

    // ---- Gathering. Amount is 1; the block and the tool ride along for filtering. ----

    public static final ResourceLocation BLOCK_BROKEN = id("block_broken");

    // ---- Combat. Amount is damage in half-hearts unless noted. ----

    public static final ResourceLocation DAMAGE_DEALT_MELEE = id("damage_dealt/melee");
    public static final ResourceLocation DAMAGE_DEALT_UNARMED = id("damage_dealt/unarmed");
    public static final ResourceLocation DAMAGE_DEALT_BOW = id("damage_dealt/bow");
    public static final ResourceLocation DAMAGE_DEALT_CROSSBOW = id("damage_dealt/crossbow");
    public static final ResourceLocation DAMAGE_DEALT_THROWN = id("damage_dealt/thrown");
    public static final ResourceLocation DAMAGE_BLOCKED = id("damage_blocked");
    public static final ResourceLocation DAMAGE_TAKEN = id("damage_taken");
    /** One per kill; the victim rides along so a tree can pay more for tougher marks. */
    public static final ResourceLocation KILL = id("kill");
    /** One per projectile that connects. Amount is the distance it flew, for precision trees. */
    public static final ResourceLocation PROJECTILE_HIT = id("projectile_hit");

    // ---- Husbandry. Amount is 1; the animal rides along. ----

    public static final ResourceLocation ANIMAL_BRED = id("animal_bred");
    public static final ResourceLocation ANIMAL_TAMED = id("animal_tamed");
    public static final ResourceLocation ANIMAL_FED = id("animal_fed");

    // ---- Smithing. Amount is the work done: durability mended, levels spent, or stack size. ----

    public static final ResourceLocation ITEM_REPAIRED = id("item_repaired");
    public static final ResourceLocation ITEM_ENCHANTED = id("item_enchanted");
    public static final ResourceLocation ITEM_CRAFTED = id("item_crafted");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(TommeMod.MOD_ID, path);
    }
}
