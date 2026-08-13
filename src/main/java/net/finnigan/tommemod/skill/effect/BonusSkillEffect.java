package net.finnigan.tommemod.skill.effect;

import com.google.gson.JsonObject;
import net.finnigan.tommemod.skill.RankedValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Raises a named bonus - a number some handler elsewhere reads back and acts on.
 *
 * This is the escape hatch for everything the attribute system cannot express: extra jump height,
 * a chance at doubled ore, softer landings, faster bow draws. The key is the whole contract. A
 * handler asks {@code SkillBonuses.get(player, ORE_DOUBLE_DROP_CHANCE)} and gets a number; it neither
 * knows nor cares which node, or how many nodes, put it there. Adding a second node that feeds an
 * existing key is a pure data change.
 *
 * <pre>
 *   {"type": "tommemod:bonus", "key": "tommemod:ore_double_drop_chance",
 *    "amount_per_rank": 0.05, "description": "%s chance to double ore drops"}
 * </pre>
 */
public record BonusSkillEffect(ResourceLocation key, RankedValue amount, ValueFormat format,
                               String descriptionOverride) implements SkillEffect {

    @Override
    public void contribute(SkillEffectSink sink, int rank) {
        sink.addBonus(key, amount.at(rank));
    }

    @Override
    public Component describe(int rank) {
        String value = format.format(amount.at(Math.max(rank, 1)));
        String text = descriptionOverride.isEmpty()
                ? value + " " + key.getPath().replace('_', ' ')
                : descriptionOverride.replace("%s", value);
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    public static SkillEffect parse(JsonObject json) {
        return new BonusSkillEffect(
                new ResourceLocation(GsonHelper.getAsString(json, "key")),
                RankedValue.parseAmount(json),
                ValueFormat.byName(GsonHelper.getAsString(json, "format", ""), ValueFormat.PERCENT),
                GsonHelper.getAsString(json, "description", ""));
    }
}
