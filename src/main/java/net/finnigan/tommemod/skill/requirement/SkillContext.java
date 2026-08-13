package net.finnigan.tommemod.skill.requirement;

import net.finnigan.tommemod.skill.SkillProgressView;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * What a requirement gets to look at: the player's standing everywhere, and which tree is asking.
 *
 * The second half is what lets a requirement leave its {@code skill} field out and mean "this one",
 * so the overwhelmingly common case - a node in the Agility tree needing Agility levels - is written
 * without repeating the skill's own name on every line. Naming a skill explicitly is what makes a
 * requirement cross-tree.
 *
 * {@code nodeId} is carried for the requirements that have to talk about themselves - {@code all_nodes}
 * needs to know which node to leave out of "every other node", or it could never be satisfied.
 */
public record SkillContext(SkillProgressView progress, ResourceLocation skillId, String nodeId) {

    /** Resolves a requirement's optional skill field: absent means the tree being evaluated. */
    public ResourceLocation resolve(@Nullable ResourceLocation declared) {
        return declared != null ? declared : skillId;
    }
}
