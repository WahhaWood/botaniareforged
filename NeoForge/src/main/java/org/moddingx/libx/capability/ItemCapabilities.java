package org.moddingx.libx.capability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.moddingx.libx.inventory.IAdvancedItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Re-created shim (LibX 5 API shape) for BotaniaReforged: wraps an item
 * handler supplier with slot-based extract/insert restrictions. In LibX 5
 * this returned a LazyOptional; on NeoForge 1.21.1 the plain handler is
 * returned and registered through RegisterCapabilitiesEvent instead.
 */
public final class ItemCapabilities {

    private ItemCapabilities() {}

    @Nullable
    public static IAdvancedItemHandlerModifiable create(Supplier<IItemHandlerModifiable> inventory,
                                                        @Nullable java.util.function.Predicate<Integer> extract,
                                                        @Nullable BiPredicate<Integer, ItemStack> insert) {
        return new Restricted(inventory, extract, insert);
    }

    @Nullable
    public static IAdvancedItemHandlerModifiable create(IItemHandlerModifiable inventory) {
        return new Restricted(() -> inventory, null, null);
    }

    @Nullable
    public static IAdvancedItemHandlerModifiable create(IItemHandlerModifiable inventory,
                                                        @Nullable java.util.function.Predicate<Integer> extract,
                                                        @Nullable BiPredicate<Integer, ItemStack> insert) {
        return new Restricted(() -> inventory, extract, insert);
    }

    private record Restricted(Supplier<IItemHandlerModifiable> inventory,
                              @Nullable java.util.function.Predicate<Integer> extract,
                              @Nullable BiPredicate<Integer, ItemStack> insert) implements IAdvancedItemHandlerModifiable {

        private IItemHandlerModifiable base() {
            return this.inventory.get();
        }

        @Override
        public int getSlots() {
            return this.base().getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.base().getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (this.insert != null && !this.insert.test(slot, stack)) {
                return stack;
            }
            return this.base().insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (this.extract != null && !this.extract.test(slot)) {
                return ItemStack.EMPTY;
            }
            return this.base().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.base().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (this.insert != null && !this.insert.test(slot, stack)) {
                return false;
            }
            return this.base().isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            this.base().setStackInSlot(slot, stack);
        }
    }
}
