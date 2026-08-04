package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.enchantment.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Post Mortem: dying in an enchanted chestplate keeps the inventory.
 *
 * There is no per-player equivalent of the keepInventory gamerule to flip, so instead the inventory
 * is serialised and emptied while the death is still being processed - Player#dropEquipment then has
 * nothing left to scatter - and reloaded once the respawned player exists. The stash lives in the
 * player's Forge persistent data, which ServerPlayer#restoreFrom carries across the respawn and which
 * is written to the player file, so a disconnect (or server restart) between dying and respawning
 * doesn't lose the items.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class PostMortemHandler {

    private static final String STASH_KEY = "tommemod_post_mortem_inventory";

    // HIGH so the inventory is banked before anything else that reacts to the death can consume it.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide || player.isSpectator()) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        if (EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.POST_MORTEM.get(), player.getItemBySlot(EquipmentSlot.CHEST)) <= 0) {
            return;
        }

        ListTag saved = player.getInventory().save(new ListTag());
        player.getInventory().clearContent();
        persistentData(player, true).put(STASH_KEY, saved);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        CompoundTag persisted = persistentData(player, false);
        if (!persisted.contains(STASH_KEY, Tag.TAG_LIST)) return;

        player.getInventory().load(persisted.getList(STASH_KEY, Tag.TAG_COMPOUND));
        persisted.remove(STASH_KEY);
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * True between the death and the respawn of a player whose inventory Post Mortem is holding.
     * The accessory slots live in a capability rather than the inventory, so they have to opt out of
     * their own death-drop and opt in to the respawn copy separately - see AccessoryEffectEvents and
     * CapabilityHandler, which both treat this exactly like the keepInventory gamerule.
     */
    public static boolean isHoldingInventory(Player player) {
        return persistentData(player, false).contains(STASH_KEY, Tag.TAG_LIST);
    }

    /**
     * Forge's PlayerPersisted sub-tag - the only entity data that survives the respawn clone. Read
     * paths pass create=false so merely asking the question never writes an empty tag to the save.
     */
    private static CompoundTag persistentData(Player player, boolean create) {
        CompoundTag root = player.getPersistentData();
        if (create && !root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
