package net.finnigan.tommemod.menu;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.entity.OvenBlockEntity;
import net.finnigan.tommemod.block.entity.OvenMenu;
import net.minecraft.core.BlockPos;
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
}