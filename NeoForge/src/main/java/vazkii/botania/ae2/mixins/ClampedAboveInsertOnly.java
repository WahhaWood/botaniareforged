package vazkii.botania.ae2.mixins;

import vazkii.botania.ae2.ae2.ManaHelper;
import vazkii.botania.ae2.ae2.SafeMana;
import appeng.api.config.Actionable;
import org.spongepowered.asm.mixin.Mixin;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.block.block_entity.AvatarBlockEntity;
import vazkii.botania.common.block.block_entity.BotanicalBreweryBlockEntity;
import vazkii.botania.common.block.block_entity.LifeImbuerBlockEntity;
import vazkii.botania.common.block.block_entity.ManaEnchanterBlockEntity;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaSpreaderBlockEntity;
import vazkii.botania.common.block.block_entity.mana.PowerGeneratorBlockEntity;

@Mixin({ManaSpreaderBlockEntity.class, PowerGeneratorBlockEntity.class, LifeImbuerBlockEntity.class, RunicAltarBlockEntity.class, AvatarBlockEntity.class, BotanicalBreweryBlockEntity.class, ManaEnchanterBlockEntity.class})
class ClampedAboveInsertOnly implements SafeMana {
   @Override
   public int insert(int amount, Actionable mode) {
      ManaReceiver be = (ManaReceiver)this;
      if (mode == Actionable.SIMULATE) {
         return Math.min(amount, ManaHelper.getCapacity(be) - be.getCurrentMana());
      } else {
         int old = be.getCurrentMana();
         be.receiveMana(amount);
         return be.getCurrentMana() - old;
      }
   }

   @Override
   public int extract(int amount, Actionable mode) {
      return 0;
   }
}
