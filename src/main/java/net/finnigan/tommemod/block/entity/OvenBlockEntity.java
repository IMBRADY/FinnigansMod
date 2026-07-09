package net.finnigan.tommemod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraft.core.Direction;
import javax.annotation.Nullable;
import java.util.Optional;

public class OvenBlockEntity extends BlockEntity implements MenuProvider {

    private static final int INPUT_SLOTS = 4;
    private static final int OUTPUT_SLOTS = 4;
    private static final int FUEL_SLOT = 8;
    public static final int COOK_TIME = 100;

    private int comboProgress = 0;
    private static final int COMBO_COOK_TIME = 200;

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

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public OvenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVEN.get(), pos, state);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, OvenBlockEntity entity) {
        if (level.isClientSide()) return;

        boolean dirty = false;

        // --- Determine fuel need: check single-slot AND combo recipes ---
        boolean hasValidInput = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack input = entity.itemHandler.getStackInSlot(i);
            ItemStack output = entity.itemHandler.getStackInSlot(INPUT_SLOTS + i);
            Optional<net.minecraft.world.item.crafting.SmokingRecipe> recipeOpt = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMOKING, new net.minecraft.world.SimpleContainer(input), level);
            if (!input.isEmpty() && recipeOpt.isPresent()
                    && canInsertResult(output, recipeOpt.get().getResultItem(level.registryAccess()))) {
                hasValidInput = true;
                break;
            }
        }
        // combo also counts as valid fuel-consuming input
        int comboSlotA = -1, comboSlotB = -1;
        net.finnigan.tommemod.recipe.CombiningRecipe activeCombo = null;
        outerFind:
        for (int i = 0; i < INPUT_SLOTS; i++) {
            for (int j = i + 1; j < INPUT_SLOTS; j++) {
                ItemStack a = entity.itemHandler.getStackInSlot(i);
                ItemStack b = entity.itemHandler.getStackInSlot(j);
                if (a.isEmpty() || b.isEmpty()) continue;
                net.minecraft.world.SimpleContainer testContainer = new net.minecraft.world.SimpleContainer(a, b);
                Optional<net.finnigan.tommemod.recipe.CombiningRecipe> comboOpt = level.getRecipeManager()
                        .getRecipeFor(net.finnigan.tommemod.recipe.ModRecipes.COMBINING_TYPE.get(), testContainer, level);
                if (comboOpt.isPresent()) {
                    comboSlotA = i;
                    comboSlotB = j;
                    activeCombo = comboOpt.get();
                    hasValidInput = true;
                    break outerFind;
                }
            }
        }

        if (hasValidInput && entity.fuelTicksLeft <= 0) {
            ItemStack fuel = entity.itemHandler.getStackInSlot(FUEL_SLOT);
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(fuel, null);
            if (burnTime > 0) {
                entity.fuelTicksLeft = burnTime;
                entity.fuelTicksTotal = burnTime;
                fuel.shrink(1);
                dirty = true;
            }
        }

        boolean isLit = entity.fuelTicksLeft > 0;
        if (isLit) {
            entity.fuelTicksLeft--;
            dirty = true;
        }

        // --- Single-slot smoking, SKIPPING any slot reserved by an active combo ---
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (i == comboSlotA || i == comboSlotB) {
                entity.cookProgress[i] = 0;
                continue;
            }

            ItemStack input = entity.itemHandler.getStackInSlot(i);
            ItemStack output = entity.itemHandler.getStackInSlot(INPUT_SLOTS + i);

            Optional<net.minecraft.world.item.crafting.SmokingRecipe> recipeOpt = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMOKING, new net.minecraft.world.SimpleContainer(input), level);

            boolean canCook = !input.isEmpty() && recipeOpt.isPresent()
                    && canInsertResult(output, recipeOpt.get().getResultItem(level.registryAccess()));

            if (isLit && canCook) {
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
                }
                dirty = true;
            } else {
                entity.cookProgress[i] = 0;
            }
        }

        // --- Combo cooking ---
        if (activeCombo != null) {
            ItemStack stackA = entity.itemHandler.getStackInSlot(comboSlotA);
            ItemStack stackB = entity.itemHandler.getStackInSlot(comboSlotB);
            ItemStack comboResult = activeCombo.getResultItem(level.registryAccess());

            for (int o = 0; o < OUTPUT_SLOTS; o++) {
                ItemStack existingOutput = entity.itemHandler.getStackInSlot(INPUT_SLOTS + o);
                if (canInsertResult(existingOutput, comboResult)) {
                    if (isLit) {
                        entity.comboProgress++;
                        if (entity.comboProgress >= COMBO_COOK_TIME) {
                            entity.comboProgress = 0;
                            ItemStack finalResult = comboResult.copy();
                            if (existingOutput.isEmpty()) {
                                entity.itemHandler.setStackInSlot(INPUT_SLOTS + o, finalResult);
                            } else {
                                existingOutput.grow(finalResult.getCount());
                            }
                            stackA.shrink(1);
                            stackB.shrink(1);
                        }
                        dirty = true;
                    }
                    break;
                }
            }
        } else {
            entity.comboProgress = 0;
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
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
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

    // ---- Persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());
        tag.putIntArray("cook_progress", cookProgress);
        tag.putInt("fuel_ticks_left", fuelTicksLeft);
        tag.putInt("fuel_ticks_total", fuelTicksTotal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("inventory"));
        if (tag.contains("cook_progress")) {
            System.arraycopy(tag.getIntArray("cook_progress"), 0, cookProgress, 0, INPUT_SLOTS);
        }
        fuelTicksLeft = tag.getInt("fuel_ticks_left");
        fuelTicksTotal = tag.getInt("fuel_ticks_total");
    }

    // ---- Client sync ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}