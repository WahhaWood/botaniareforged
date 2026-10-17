package de.melanx.botanicalmachinery.helper;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nonnull;

/**
 * Re-created Botanical Machinery slot (Apache-2.0) for BotaniaReforged:
 * an output slot that allows unrestricted pickup but no insertion.
 */
public class UnrestrictedOutputSlot extends SlotItemHandler {

    public UnrestrictedOutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public boolean mayPlace(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(@Nonnull Player player) {
        return true;
    }
}
