package net.finnigan.tommemod.menu;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.entity.BuilderHubBlockEntity;
import net.finnigan.tommemod.block.entity.MonolithBlockEntity;
import net.finnigan.tommemod.block.entity.OvenBlockEntity;
import net.finnigan.tommemod.block.entity.OvenMenu;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TommeMod.MOD_ID);

    public static final RegistryObject<MenuType<OvenMenu>> OVEN_MENU =
            MENUS.register("oven_menu", () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof OvenBlockEntity oven) {
                    return new OvenMenu(windowId, inv, oven);
                }
                throw new IllegalStateException("Block entity at " + pos + " is not an OvenBlockEntity");
            }));

    public static final RegistryObject<MenuType<WarriorVillagerMenu>> WARRIOR_VILLAGER_MENU =
            MENUS.register("warrior_villager_menu", () -> IForgeMenuType.create((windowId, inv, data) -> {
                int entityId = data.readInt();
                Entity entity = inv.player.level().getEntity(entityId);
                if (entity instanceof WarriorVillagerEntity warrior) {
                    return new WarriorVillagerMenu(windowId, inv, warrior);
                }
                throw new IllegalStateException("Entity " + entityId + " is not a WarriorVillagerEntity");
            }));

    public static final RegistryObject<MenuType<MonolithMenu>> MONOLITH_MENU =
            MENUS.register("monolith_menu", () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof MonolithBlockEntity monolith) {
                    return new MonolithMenu(windowId, inv, monolith);
                }
                throw new IllegalStateException("Block entity at " + pos + " is not a MonolithBlockEntity");
            }));

    public static final RegistryObject<MenuType<BuilderHubMenu>> BUILDER_HUB_MENU =
            MENUS.register("builder_hub_menu", () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof BuilderHubBlockEntity hub) {
                    return new BuilderHubMenu(windowId, inv, hub);
                }
                throw new IllegalStateException("Block entity at " + pos + " is not a BuilderHubBlockEntity");
            }));
}