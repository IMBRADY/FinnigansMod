package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.skill.event.SkillMarksmanshipBonuses;
import net.finnigan.tommemod.skill.event.SkillUniqueBonuses;
import net.finnigan.tommemod.util.CooldownOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Uniques' Infinity Paradox: a unique's ability comes back sooner.
 *
 * Shortened where the cooldown is written rather than in each weapon, which is the whole point - the
 * mod has twenty-odd uniques, more than twenty of which set their own cooldown with their own number,
 * and one node has to cover all of them plus any added later. Intercepting {@code addCooldown} means no
 * unique needs to know the tree exists.
 *
 * Runs on both sides deliberately. Cooldowns are tracked separately on the client and the server, and
 * the client refuses to send a use packet while its own copy is still running - shortening only the
 * server's would change nothing a player could feel.
 */
@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin implements CooldownOwner {

    @Unique
    @Nullable
    private Player tommemod$owner;

    @Override
    @Nullable
    public Player tommemod$getCooldownOwner() {
        return this.tommemod$owner;
    }

    @Override
    public void tommemod$setCooldownOwner(Player player) {
        this.tommemod$owner = player;
    }

    @ModifyVariable(method = "addCooldown", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int tommemod$shortenUniqueCooldown(int ticks, Item item) {
        Player owner = this.tommemod$owner;
        if (owner == null) return ticks;

        ItemStack stack = new ItemStack(item);
        // Two trees shorten cooldowns, and they cover different items - Uniques the ability cooldown on a
        // unique weapon, Marksmanship the reload a musket puts on itself. Both are written through this
        // one call, so both are answered here.
        int afterUnique = SkillUniqueBonuses.shortenCooldown(owner, stack, ticks);
        return SkillMarksmanshipBonuses.shortenReload(owner, stack, afterUnique);
    }
}
