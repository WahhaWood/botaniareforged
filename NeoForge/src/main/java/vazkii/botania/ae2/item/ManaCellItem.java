package vazkii.botania.ae2.item;

import vazkii.botania.ae2.ABItems;
import vazkii.botania.ae2.item.cell.IManaCellItem;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.localization.PlayerMessages;
import appeng.hooks.AEToolItem;
import appeng.util.InteractionUtil;
import java.util.List;
import java.util.Optional;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class ManaCellItem extends Item implements IManaCellItem, AEToolItem {
   private final ItemLike coreItem;
   private final int totalBytes;
   private final double idleDrain;

   public ManaCellItem(Properties properties, ItemLike coreItem, int kilobytes, double idleDrain) {
      super(properties.stacksTo(1));
      this.coreItem = coreItem;
      this.totalBytes = kilobytes * 1000;
      this.idleDrain = idleDrain;
   }

   @Override
   public IUpgradeInventory getUpgrades(ItemStack is) {
      return UpgradeInventories.forItem(is, 1);
   }

   @Override
   public long getTotalBytes() {
      return (long)this.totalBytes;
   }

   @Override
   public double getIdleDrain() {
      return this.idleDrain;
   }

   @ParametersAreNonnullByDefault
   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      this.disassembleDrive(player.getItemInHand(hand), level, player);
      return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), player.getItemInHand(hand));
   }

   private boolean disassembleDrive(ItemStack stack, Level level, Player player) {
      if (InteractionUtil.isInAlternateUseMode(player)) {
         if (level.isClientSide()) {
            return false;
         }

         Inventory playerInventory = player.getInventory();
         StorageCell inv = StorageCells.getCellInventory(stack, null);
         if (inv != null && playerInventory.getSelected() == stack) {
            KeyCounter list = inv.getAvailableStacks();
            if (list.isEmpty()) {
               playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);
               playerInventory.placeItemBackInInventory(new ItemStack(this.coreItem));

               for (ItemStack upgrade : this.getUpgrades(stack)) {
                  playerInventory.placeItemBackInInventory(upgrade);
               }

               playerInventory.placeItemBackInInventory(new ItemStack(ABItems.MANA_CELL_HOUSING.get()));
               return true;
            }

            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
         }
      }

      return false;
   }

   @Override
   public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
      return this.disassembleDrive(stack, context.getLevel(), context.getPlayer())
         ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
         : InteractionResult.PASS;
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      this.addCellInformationToTooltip(stack, tooltipComponents);
   }

   @Override
   public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
      return this.getCellTooltipImage(stack);
   }
}
