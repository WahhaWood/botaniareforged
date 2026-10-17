package vazkii.botania.ae2.ae2;

import com.google.common.primitives.Ints;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaItem;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.StorageCell;
import appeng.items.tools.powered.AbstractPortableCell;

public class MEStorageManaItem implements ManaItem {
	private final MEStorage storage;
	private final IEnergySource energy;
	private final IActionSource source;

	public MEStorageManaItem(MEStorage storage, IEnergySource energy, IActionSource source) {
		this.storage = storage;
		this.energy = energy;
		this.source = source;
	}

	@Nullable
	public static ManaItem forItem(ItemStack stack) {
		if (stack.getItem() instanceof AbstractPortableCell item) {
			StorageCell storage = StorageCells.getCellInventory(stack, null);
			return storage == null
					? null
					: new MEStorageManaItem(
							storage,
							(amount, mode, multiplier) -> {
								amount = multiplier.multiply(amount);
								return mode == Actionable.SIMULATE
										? multiplier.divide(Math.min(amount, item.getAECurrentPower(stack)))
										: multiplier.divide(item.extractAEPower(stack, amount, Actionable.MODULATE));
							},
							IActionSource.empty()
					);
		} else {
			return null;
		}
	}

	@Override
	public int getMana() {
		return (int) StorageHelper.poweredExtraction(this.energy, this.storage, ManaKey.KEY, 2147483647L, this.source, Actionable.SIMULATE);
	}

	@Override
	public int getMaxMana() {
		return Ints.saturatedCast(
				StorageHelper.poweredExtraction(this.energy, this.storage, ManaKey.KEY, 2147483647L, this.source, Actionable.SIMULATE)
						+ StorageHelper.poweredInsert(this.energy, this.storage, ManaKey.KEY, 2147483647L, this.source, Actionable.SIMULATE)
		);
	}

	@Override
	public void addMana(int mana) {
		if (mana > 0) {
			StorageHelper.poweredInsert(this.energy, this.storage, ManaKey.KEY, (long) mana, this.source);
		} else {
			StorageHelper.poweredExtraction(this.energy, this.storage, ManaKey.KEY, (long) (-mana), this.source);
		}
	}

	@Override
	public boolean canReceiveManaFromPool(BlockEntity pool) {
		return true;
	}

	@Override
	public boolean acceptDispatchedManaFromItem(ItemStack otherStack) {
		return true;
	}

	@Override
	public boolean refuseRequestedManaFromItem(ItemStack otherStack) {
		return false;
	}

	@Override
	public boolean canDrainManaToPool(BlockEntity pool) {
		return true;
	}

	@Override
	public boolean canSendRequestedManaToItem(ItemStack otherStack) {
		return true;
	}

	@Override
	public boolean isNoExport() {
		return false;
	}
}
