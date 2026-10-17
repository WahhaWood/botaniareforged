package vazkii.botania.ae2.mixins;

import vazkii.botania.ae2.ae2.SafeMana;
import appeng.api.config.Actionable;
import org.spongepowered.asm.mixin.Mixin;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

@Mixin({ManaPoolBlockEntity.class})
class Pool implements SafeMana {
   @Override
   public int insert(int amount, Actionable mode) {
      ManaPoolBlockEntity be = (ManaPoolBlockEntity)(Object)this;
      return SafeMana.insertExcess(be, amount, mode);
   }

   @Override
   public int extract(int amount, Actionable mode) {
      ManaPoolBlockEntity be = (ManaPoolBlockEntity)(Object)this;
      amount = Math.min(amount, be.getCurrentMana());
      if (mode == Actionable.MODULATE) {
         be.receiveMana(-amount);
      }

      return amount;
   }
}
