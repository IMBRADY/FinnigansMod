package net.finnigan.tommemod.util;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Lets an {@code ItemCooldowns} say whose it is.
 *
 * Vanilla's cooldown tracker deliberately knows nothing about its owner - a weapon calls
 * {@code player.getCooldowns().addCooldown(item, ticks)} and the tracker only ever sees the item and
 * the number. That is fine until something wants to shorten the number *for a particular player*,
 * which is exactly what Uniques' Infinity Paradox is. The server's subclass happens to hold a player,
 * but the client's does not, and a cooldown the two sides disagree about is a weapon the client refuses
 * to fire.
 *
 * So the owner is stamped on at construction, identically on both sides, and read back where the
 * cooldown is set. Implemented by a mixin on ItemCooldowns; populated by a mixin on Player.
 */
public interface CooldownOwner {

    @Nullable
    Player tommemod$getCooldownOwner();

    void tommemod$setCooldownOwner(Player player);
}
