package vazkii.botania.ae2.mixins;

import vazkii.botania.ae2.ae2.SafeMana;
import appeng.api.config.Actionable;
import org.spongepowered.asm.mixin.Mixin;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;

@Mixin({TerrestrialAgglomerationPlateBlockEntity.class})
class TAPBE implements SafeMana {
   @Override
   public int insert(int amount, Actionable mode) {
      TerrestrialAgglomerationPlateBlockEntity be = (TerrestrialAgglomerationPlateBlockEntity)(Object)this;
      return SafeMana.insertExcess(be, amount, mode);
   }

   @Override
   public int extract(int amount, Actionable mode) {
      return 0;
   }
}
