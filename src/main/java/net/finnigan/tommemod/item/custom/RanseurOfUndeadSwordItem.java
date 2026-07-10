package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.SoulSummoner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class RanseurOfUndeadSwordItem extends SwordItem {

    public static final int MAX_SOULS = 3;
    private static final int COOLDOWN_TICKS = 20; // 1 second

    public RanseurOfUndeadSwordItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    public static int getSoulCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Souls")) return 0;
        return tag.getList("Souls", 10).size();
    }

    public static boolean hasRoom(ItemStack stack) {
        return getSoulCount(stack) < MAX_SOULS;
    }

    public static boolean addSoul(ItemStack stack, CompoundTag soulData) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag souls = tag.getList("Souls", 10);
        if (souls.size() >= MAX_SOULS) return false;
        souls.add(soulData);
        tag.put("Souls", souls);
        return true;
    }

    public static ListTag getSouls(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new ListTag() : tag.getList("Souls", 10);
    }

    public static void clearSouls(ItemStack stack) {
        stack.getOrCreateTag().put("Souls", new ListTag());
    }

    // ---- Active summon tracking ----

    private static ListTag getActiveSummons(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new ListTag() : tag.getList("ActiveSummons", 10);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ListTag souls = getSouls(stack);
        if (souls.isEmpty()) {
            tooltip.add(Component.literal("No souls captured").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Captured Souls (" + souls.size() + "/" + MAX_SOULS + "):")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            for (int i = 0; i < souls.size(); i++) {
                tooltip.add(Component.literal(" - " + soulName(souls.getCompound(i)))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        int active = getActiveSummons(stack).size();
        if (active > 0) {
            tooltip.add(Component.literal(active + " soul(s) currently summoned")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String soulName(CompoundTag soul) {
        ResourceLocation id = ResourceLocation.tryParse(soul.getString("EntityType"));
        return id != null ? BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString() : "Unknown";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown() && ModifierKeyTracker.isHeld(player.getUUID())) {
            if (!level.isClientSide) {
                int count = getSoulCount(stack);
                clearSouls(stack);
                if (count > 0) {
                    player.displayClientMessage(Component.literal("The blade's captured souls have been released.")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
            return InteractionResultHolder.success(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ListTag activeSummons = getActiveSummons(stack);

        if (!activeSummons.isEmpty()) {
            recall(stack, serverLevel, player);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            return InteractionResultHolder.success(stack);
        }

        ListTag souls = getSouls(stack);
        if (souls.isEmpty()) {
            player.displayClientMessage(Component.literal("The blade holds no souls to summon.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.pass(stack);
        }

        List<PathfinderMob> summoned = SoulSummoner.summonAll(level, player, souls);

        ListTag newActive = new ListTag();
        for (int i = 0; i < summoned.size(); i++) {
            CompoundTag entry = souls.getCompound(i).copy();
            entry.putUUID("SummonId", summoned.get(i).getUUID());
            newActive.add(entry);
        }
        stack.getOrCreateTag().put("ActiveSummons", newActive);
        clearSouls(stack);

        player.displayClientMessage(Component.literal("The blade unleashes " + summoned.size() + " soul(s)!")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
    }

    private void recall(ItemStack stack, ServerLevel level, Player player) {
        ListTag activeSummons = getActiveSummons(stack);
        int recovered = 0;
        int lost = 0;

        for (int i = 0; i < activeSummons.size(); i++) {
            CompoundTag entry = activeSummons.getCompound(i);
            Entity entity = level.getEntity(entry.getUUID("SummonId"));

            if (entity != null && entity.isAlive()) {
                entity.discard();
                CompoundTag soulData = entry.copy();
                soulData.remove("SummonId");
                if (hasRoom(stack)) {
                    addSoul(stack, soulData);
                    recovered++;
                }
            } else {
                lost++;
            }
        }

        stack.getOrCreateTag().put("ActiveSummons", new ListTag());

        if (lost > 0) {
            player.displayClientMessage(Component.literal(lost + " soul(s) were lost in battle.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        if (recovered > 0) {
            player.displayClientMessage(Component.literal("Recalled " + recovered + " soul(s) into the blade.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }
}