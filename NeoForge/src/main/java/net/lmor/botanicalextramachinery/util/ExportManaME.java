package net.lmor.botanicalextramachinery.util;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

import com.google.common.primitives.Ints;

import vazkii.botania.ae2.ae2.ManaKey;

import org.jetbrains.annotations.Nullable;

/**
 * Exports greenhouse mana into an Applied Energistics 2 ME network as the
 * "mana" AE key.
 *
 * <p>{@link ManaKey} and {@link ManaKeyType} ship inside this jar (Applied
 * Botanics was merged into BotaniaReforged), and AE2 is a hard dependency of the
 * mod, so there is no configuration in which the export is unavailable. The
 * insert is a plain powered insert against the grid's storage service, the same
 * one {@code FluixPoolBlockEntity} uses for its own mana.
 *
 * <p>AE2's {@link ManaKeyType#getAmountPerByte()} quantises transfers, so a
 * small buffer often cannot move at all; the caller is expected to retry rather
 * than treat a zero return as an error.
 *
 * @see net.lmor.botanicalextramachinery.blocks.tiles.BlockEntityGreenhouse
 */
public class ExportManaME {

    /**
     * @param amount  mana to push into the grid
     * @param node    the greenhouse's managed node, used to reach the grid and
     *                to attribute the transaction to this machine
     * @return the negative amount actually exported, which the caller's
     *         {@code receiveMana} then treats as a delta, so the greenhouse
     *         gives up exactly what the network accepted
     */
    public int exportManaME(int amount, @Nullable IManagedGridNode node) {
        if (node == null || amount <= 0 || node.getGrid() == null) {
            return 0;
        }

        MEStorage storage = node.getGrid().getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(node::getNode);
        long inserted = StorageHelper.poweredInsert(node.getGrid().getEnergyService(), storage,
                ManaKey.KEY, amount, source, Actionable.MODULATE);
        if (inserted <= 0L) {
            return 0;
        }

        return -Ints.saturatedCast(inserted);
    }
}