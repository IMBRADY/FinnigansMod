package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.Skill;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The experience readout in the top left: what you are earning, right now, and how much of it.
 *
 * Every line is one skill, and a skill only ever has one line - a player sprinting through shallow
 * water is earning Agility from two sources at once and wants one Agility total, not two lines
 * fighting over the same row. Several skills earning at once stack downward in the order they
 * started, so a line already being read does not get shoved around by a new one arriving.
 *
 * Colour is by skill, grouped by category: every Movement skill is a green, every Combat skill a red,
 * and within a category each skill takes its own slice of that hue so Gliding and Riding are still
 * told apart at a glance. Derived rather than listed, so a datapack adding a fifteenth skill gets a
 * colour that belongs to its category without anybody assigning one.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SkillXpOverlay {

    private static final int LEFT_MARGIN = 4;
    private static final int TOP_MARGIN = 4;
    private static final int LINE_HEIGHT = 10;

    /** The fraction of a line's life spent at full strength before it starts fading out. */
    private static final float FADE_BEGINS_AT = 0.7F;
    /** Never fade below this: at very low alpha the font renderer stops drawing rather than dimming. */
    private static final int MINIMUM_ALPHA = 16;

    /**
     * Where each category sits on the colour wheel. Movement greens, Gathering ambers, Combat reds -
     * anything else is placed by its id so it is at least stable and distinct from these three.
     */
    private static final Map<String, Float> CATEGORY_HUES = Map.of(
            "movement", 120.0F,
            "gathering", 40.0F,
            "combat", 0.0F);

    /** How far either side of its category's hue the outermost skill in that category is pushed. */
    private static final float HUE_SPREAD = 26.0F;

    private static final Map<ResourceLocation, Integer> COLOUR_CACHE = new HashMap<>();
    /** What the cache was built against, so a datapack reload rebuilds it rather than going stale. */
    private static int cachedSkillCount = -1;

    private SkillXpOverlay() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("skill_xp", overlay());
    }

    private static IGuiOverlay overlay() {
        return (forgeGui, graphics, partialTick, screenWidth, screenHeight) -> {
            Minecraft minecraft = Minecraft.getInstance();
            // The debug screen owns this corner, and hideGui means the player asked for nothing at all.
            if (minecraft.options.hideGui || minecraft.options.renderDebug) return;

            List<SkillXpFeed.Entry> entries = SkillXpFeed.live();
            if (entries.isEmpty()) return;

            long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
            int y = TOP_MARGIN;
            for (SkillXpFeed.Entry entry : entries) {
                drawLine(graphics, entry, now, y);
                y += LINE_HEIGHT;
            }
        };
    }

    private static void drawLine(GuiGraphics graphics, SkillXpFeed.Entry entry, long now, int y) {
        Skill skill = SkillTreeManager.skill(entry.skill());
        if (skill == null) return;

        String text = skill.displayName() + " +" + format(entry.xp()) + " XP";
        int colour = (alphaFor(entry.age(now)) << 24) | (colourOf(skill) & 0x00FFFFFF);

        graphics.drawString(Minecraft.getInstance().font, text, LEFT_MARGIN, y, colour, true);
    }

    /**
     * Whole numbers once there are enough of them to be worth reading as one, and a single decimal
     * below that - a tenth of a point of Agility per stride is the normal case, and rounding it to
     * zero would show a line announcing nothing.
     */
    private static String format(double xp) {
        return xp >= 10.0
                ? String.valueOf(Math.round(xp))
                : String.format(Locale.ROOT, "%.1f", xp);
    }

    private static int alphaFor(float age) {
        if (age <= FADE_BEGINS_AT) return 255;
        float remaining = 1.0F - (age - FADE_BEGINS_AT) / (1.0F - FADE_BEGINS_AT);
        return Math.max(MINIMUM_ALPHA, (int) (255 * remaining));
    }

    /** This skill's colour: its category's hue, offset by where it sits among that category's skills. */
    private static int colourOf(Skill skill) {
        rebuildIfStale();
        return COLOUR_CACHE.getOrDefault(skill.id(), 0xFFFFFF);
    }

    private static void rebuildIfStale() {
        int count = SkillTreeManager.skills().size();
        if (count == cachedSkillCount && !COLOUR_CACHE.isEmpty()) return;

        COLOUR_CACHE.clear();
        cachedSkillCount = count;

        // Grouped by category and walked in the trees' own sort order, so a skill's colour depends on
        // where it sits in its category rather than on load order, and is the same every session.
        for (var category : SkillTreeManager.orderedCategories()) {
            List<Skill> members = SkillTreeManager.orderedSkills().stream()
                    .filter(skill -> skill.category().equals(category.id()))
                    .toList();
            if (members.isEmpty()) continue;

            float base = CATEGORY_HUES.getOrDefault(category.id().getPath(), fallbackHue(category.id()));
            for (int i = 0; i < members.size(); i++) {
                // Spread evenly across the band and centred on the category's own hue, so a category
                // of one sits exactly on it and a category of five fans out either side.
                float offset = members.size() == 1
                        ? 0.0F
                        : (i / (float) (members.size() - 1) - 0.5F) * 2.0F * HUE_SPREAD;
                // Alternating brightness on top of the hue split: two adjacent greens separated by
                // thirteen degrees alone are a harder read than they need to be.
                float value = i % 2 == 0 ? 1.0F : 0.82F;
                COLOUR_CACHE.put(members.get(i).id(), hsvToRgb(base + offset, 0.72F, value));
            }
        }
    }

    /** A stable hue for a category nobody assigned one to. Spread around the wheel by name. */
    private static float fallbackHue(ResourceLocation category) {
        return Math.floorMod(category.hashCode(), 360);
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue % 360.0F + 360.0F) % 360.0F / 60.0F;
        int sector = (int) h;
        float fraction = h - sector;

        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));

        return switch (sector) {
            case 0 -> pack(value, t, p);
            case 1 -> pack(q, value, p);
            case 2 -> pack(p, value, t);
            case 3 -> pack(p, q, value);
            case 4 -> pack(t, p, value);
            default -> pack(value, p, q);
        };
    }

    private static int pack(float red, float green, float blue) {
        return ((int) (red * 255) << 16) | ((int) (green * 255) << 8) | (int) (blue * 255);
    }
}
