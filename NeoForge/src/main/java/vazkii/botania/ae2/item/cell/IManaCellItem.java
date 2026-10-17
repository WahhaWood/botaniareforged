package vazkii.botania.ae2.item.cell;

import vazkii.botania.ae2.ae2.ManaKey;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.core.AEConfig;
import appeng.items.storage.StorageCellTooltipComponent;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public interface IManaCellItem extends ICellWorkbenchItem {
   long getTotalBytes();

   double getIdleDrain();

   @Override
   default FuzzyMode getFuzzyMode(ItemStack is) {
      return FuzzyMode.IGNORE_ALL;
   }

   @Override
   default void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
   }

   default void addCellInformationToTooltip(ItemStack is, List<Component> lines) {
      Preconditions.checkArgument(is.getItem() == this);
      ManaCellHandler.INSTANCE.addCellInformationToTooltip(is, lines);
   }

   default Optional<TooltipComponent> getCellTooltipImage(ItemStack is) {
      ArrayList<ItemStack> upgradeStacks = new ArrayList<>();
      if (AEConfig.instance().isTooltipShowCellUpgrades()) {
         for (ItemStack upgrade : this.getUpgrades(is)) {
            upgradeStacks.add(upgrade);
         }
      }

      List<GenericStack> content = List.of();
      if (AEConfig.instance().isTooltipShowCellContent()) {
         ManaCellInventory handler = ManaCellHandler.INSTANCE.getCellInventory(is, null);
         long amount = 0L;
         if (handler != null) {
            amount = handler.extract(ManaKey.KEY, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
         }

         if (amount > 0L) {
            content = List.of(new GenericStack(ManaKey.KEY, amount));
         }
      }

      return Optional.of(new StorageCellTooltipComponent(upgradeStacks, content, false, true));
   }
}
