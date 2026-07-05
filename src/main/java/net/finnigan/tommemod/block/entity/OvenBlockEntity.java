package net.finnigan.tommemod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import java.util.Optional;

public class OvenBlockEntity extends BlockEntity implements MenuProvider {
// slots 0-3: input, 4-7: output, 8 = fuel
    private static final int INPUT_SLOTS = 4;
    private static final int OUTPUT_SLOTS = 4;
    private static final int FUEL_SLOT = 8; // index 8, after 4 inputs + 4 outputs
    public static final int COOK_TIME = 100; // matches smoker speed

    private final ItemStackHandler itemHandler = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<ItemStackHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    private final int[] cookProgress = new int[INPUT_SLOTS];
    private int fuelTicksLeft = 0;
    private int fuelTicksTotal = 0;

    public OvenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVEN.get(), pos, state); // you'll create ModBlockEntities
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, OvenBlockEntity entity) {
        if (level.isClientSide()) return;

        boolean dirty = false;
        boolean anyCooking = false;

        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack input = entity.itemHandler.getStackInSlot(i);
            ItemStack output = entity.itemHandler.getStackInSlot(INPUT_SLOTS + i);

            Optional<net.minecraft.world.item.crafting.SmokingRecipe> recipeOpt = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMOKING, new net.minecraft.world.SimpleContainer(input), level);

            boolean canCook = !input.isEmpty() && recipeOpt.isPresent()
                    && canInsertResult(output, recipeOpt.get().getResultItem(level.registryAccess()));

            if (canCook && entity.fuelTicksLeft > 0) {
                anyCooking = true;
                entity.cookProgress[i]++;
                if (entity.cookProgress[i] >= COOK_TIME) {
                    entity.cookProgress[i] = 0;
                    ItemStack result = recipeOpt.get().getResultItem(level.registryAccess()).copy();
                    if (output.isEmpty()) {
                        entity.itemHandler.setStackInSlot(INPUT_SLOTS + i, result);
                    } else {
                        output.grow(result.getCount());
                    }
                    input.shrink(1);
                    dirty = true;
                }
            } else {
                entity.cookProgress[i] = 0;
            }
        }

        // fuel consumption — only burns fuel while at least one slot is actively cooking
        if (anyCooking && entity.fuelTicksLeft <= 0) {
            ItemStack fuel = entity.itemHandler.getStackInSlot(FUEL_SLOT);
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(fuel, null);
            if (burnTime > 0) {
                entity.fuelTicksLeft = burnTime;
                entity.fuelTicksTotal = burnTime;
                fuel.shrink(1);
                dirty = true;
            }
        } else if (anyCooking) {
            entity.fuelTicksLeft--;
        }

        if (dirty) entity.setChanged();
    }

    private static boolean canInsertResult(ItemStack output, ItemStack result) {
        if (output.isEmpty()) return true;
        if (!ItemStack.isSameItemSameTags(output, result)) return false;
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tommemod.oven");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new OvenMenu(id, playerInv, this);
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    public LazyOptional<net.minecraftforge.items.IItemHandler> getCapability(net.minecraftforge.common.capabilities.Capability<net.minecraftforge.items.IItemHandler> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    public int getCookProgress(int slot) { return cookProgress[slot]; }
    public int getFuelTicksLeft() { return fuelTicksLeft; }
    public int getFuelTicksTotal() { return fuelTicksTotal; }
}