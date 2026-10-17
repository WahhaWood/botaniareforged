package vazkii.botania.ae2.item.cell;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.core.localization.Tooltips;

public class ManaCellHandler implements ICellHandler {
	public static final ManaCellHandler INSTANCE = new ManaCellHandler();

	@Override
	public boolean isCell(ItemStack is) {
		return !is.isEmpty() && is.getItem() instanceof IManaCellItem;
	}

	@Nullable
	@Override
	public ManaCellInventory getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
		return this.isCell(is) ? new ManaCellInventory((IManaCellItem) is.getItem(), is, host) : null;
	}

	public void addCellInformationToTooltip(ItemStack is, List<Component> lines) {
		ManaCellInventory handler = this.getCellInventory(is, null);
		if (handler != null) {
			lines.add(Tooltips.bytesUsed(handler.getUsedBytes(), handler.getTotalBytes()));
		}
	}
}
