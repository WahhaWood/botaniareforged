package vazkii.botania.ae2.item.cell;

import vazkii.botania.ae2.AppliedBotanicsForge;
import vazkii.botania.ae2.ae2.ManaKey;
import vazkii.botania.ae2.ae2.ManaKeyType;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ManaCellInventory implements StorageCell {
   private final IManaCellItem cellType;
   private final ItemStack i;
   @Nullable
   private final ISaveProvider container;
   private final boolean hasVoidUpgrade;
   private long storedMana;
   private boolean isPersisted = true;

   public ManaCellInventory(IManaCellItem cellType, ItemStack o, @Nullable ISaveProvider container) {
      this.cellType = cellType;
      this.i = o;
      this.container = container;
      this.storedMana = this.i.getOrDefault(AppliedBotanicsForge.AE_MANA.get(), Long.valueOf(0L));
      this.hasVoidUpgrade = cellType.getUpgrades(o).isInstalled(AEItems.VOID_CARD);
   }

   @Override
   public CellState getStatus() {
      if (this.storedMana == 0L) {
         return CellState.EMPTY;
      } else if (this.storedMana == this.getMaxMana()) {
         return CellState.FULL;
      } else {
         return this.storedMana > this.getMaxMana() / 2L ? CellState.TYPES_FULL : CellState.NOT_EMPTY;
      }
   }

   @Override
   public double getIdleDrain() {
      return this.cellType.getIdleDrain();
   }

   private long getMaxMana() {
      return this.cellType.getTotalBytes() * (long)ManaKeyType.TYPE.getAmountPerByte();
   }

   protected long getTotalBytes() {
      return this.cellType.getTotalBytes();
   }

   protected long getUsedBytes() {
      int amountPerByte = ManaKeyType.TYPE.getAmountPerByte();
      return (this.storedMana + (long)amountPerByte - 1L) / (long)amountPerByte;
   }

   protected void saveChanges() {
      this.isPersisted = false;
      if (this.container != null) {
         this.container.saveChanges();
      } else {
         this.persist();
      }
   }

   @Override
   public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
      if (!(what instanceof ManaKey)) {
         return 0L;
      } else {
         long inserted = Math.min(this.getMaxMana() - this.storedMana, amount);
         if (mode == Actionable.MODULATE) {
            this.storedMana += inserted;
            this.saveChanges();
         }

         return this.hasVoidUpgrade ? amount : inserted;
      }
   }

   @Override
   public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
      if (!(what instanceof ManaKey)) {
         return 0L;
      } else {
         long extracted = Math.min(this.storedMana, amount);
         if (mode == Actionable.MODULATE) {
            this.storedMana -= extracted;
            this.saveChanges();
         }

         return extracted;
      }
   }

   @Override
   public void persist() {
      if (!this.isPersisted) {
         this.i.set(AppliedBotanicsForge.AE_MANA, this.storedMana);
         this.isPersisted = true;
      }
   }

   @Override
   public void getAvailableStacks(KeyCounter out) {
      if (this.storedMana > 0L) {
         out.add(ManaKey.KEY, this.storedMana);
      }
   }

   @Override
   public Component getDescription() {
      return this.i.getHoverName();
   }
}
