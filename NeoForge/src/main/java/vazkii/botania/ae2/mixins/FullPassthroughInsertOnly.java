package vazkii.botania.ae2.mixins;

import vazkii.botania.ae2.ae2.ManaHelper;
import vazkii.botania.ae2.ae2.SafeMana;
import appeng.api.config.Actionable;
import org.spongepowered.asm.mixin.Mixin;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.block.block_entity.mana.ManaSplitterBlockEntity;
import vazkii.botania.common.block.mana.ManaVoidBlock.ManaReceiverImpl;

@Mixin({ManaSplitterBlockEntity.class, ManaReceiverImpl.class})
class FullPassthroughInsertOnly implements SafeMana {
   @Override
   public int insert(int amount, Actionable mode) {
      ManaReceiver be = (ManaReceiver)this;
      if (mode == Actionable.SIMULATE) {
         return Math.min(amount, ManaHelper.getCapacity(be) - be.getCurrentMana());
      } else if (be.isFull()) {
         return 0;
      } else {
         be.receiveMana(amount);
         return amount;
      }
   }

   @Override
   public int extract(int amount, Actionable mode) {
      return 0;
   }
}
