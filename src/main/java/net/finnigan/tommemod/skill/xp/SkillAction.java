package net.finnigan.tommemod.skill.xp;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Something a player just did, in the only vocabulary the skill system understands.
 *
 * This is the seam between code and data. Java's whole responsibility is to notice events and post
 * them as actions from the fixed list in {@link ModSkillActions}; which skill that feeds, at what
 * rate, and under what conditions is decided entirely by the tree files. Nowhere does a handler name
 * a skill, so wiring "breaking ores also feeds Excavation" is a one-line data change.
 *
 * @param id     what happened - one of the ModSkillActions constants
 * @param amount how much of it: blocks travelled, hearts of damage, or simply 1 for a one-off
 * @param block  the block involved, for breaking
 * @param tool   what was in the player's hand
 * @param entity the other party, for combat and animal handling
 * @param item   the item acted upon, for repairing and enchanting
 */
public record SkillAction(ResourceLocation id, double amount,
                          @Nullable BlockState block, @Nullable ItemStack tool,
                          @Nullable Entity entity, @Nullable ItemStack item) {

    public static SkillAction of(ResourceLocation id, double amount) {
        return new SkillAction(id, amount, null, null, null, null);
    }

    public static SkillAction once(ResourceLocation id) {
        return of(id, 1.0);
    }

    public SkillAction withBlock(BlockState block) {
        return new SkillAction(id, amount, block, tool, entity, item);
    }

    public SkillAction withTool(ItemStack tool) {
        return new SkillAction(id, amount, block, tool, entity, item);
    }

    public SkillAction withEntity(Entity entity) {
        return new SkillAction(id, amount, block, tool, entity, item);
    }

    public SkillAction withItem(ItemStack item) {
        return new SkillAction(id, amount, block, tool, entity, item);
    }
}
