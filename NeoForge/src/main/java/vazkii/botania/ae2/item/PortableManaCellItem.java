package vazkii.botania.ae2.item;

import vazkii.botania.ae2.ABMenus;
import vazkii.botania.ae2.AppliedBotanics;
import vazkii.botania.ae2.item.cell.IManaCellItem;
import appeng.api.upgrades.Upgrades;
import appeng.items.tools.powered.AbstractPortableCell;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;

public class PortableManaCellItem extends AbstractPortableCell implements IManaCellItem {
   private final int totalBytes;
   private final double idleDrain;

   public PortableManaCellItem(Properties props, int kilobytes, double idleDrain) {
      super(ABMenus.PORTABLE_MANA_CELL_TYPE, props, 6797806);
      this.totalBytes = kilobytes * 1000;
      this.idleDrain = idleDrain;
   }

   @Override
   public long getTotalBytes() {
      return (long)this.totalBytes;
   }

   @Override
   public double getIdleDrain() {
      return this.idleDrain;
   }

   @Override
   public ResourceLocation getRecipeId() {
      return AppliedBotanics.id(Objects.requireNonNull(this.getRegistryName()).getPath());
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag advancedTooltips) {
      super.appendHoverText(stack, context, lines, advancedTooltips);
      this.addCellInformationToTooltip(stack, lines);
   }

   @Override
   public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
      return this.getCellTooltipImage(stack);
   }

   @Override
   public double getChargeRate(ItemStack stack) {
      return 80.0 + 80.0 * (double)Upgrades.getEnergyCardMultiplier(this.getUpgrades(stack));
   }
}
