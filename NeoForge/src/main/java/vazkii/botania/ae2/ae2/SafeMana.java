package vazkii.botania.ae2.ae2;

import appeng.api.config.Actionable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;

public interface SafeMana {
   int insert(int var1, Actionable var2);

   int extract(int var1, Actionable var2);

   static <R extends ManaReceiver & ManaSparkAttachable> int insertExcess(R be, int amount, Actionable mode) {
      amount = Math.min(amount, be.getAvailableSpaceForMana());
      if (mode == Actionable.MODULATE) {
         be.receiveMana(amount);
      }

      return amount;
   }

   @Contract("null->null; !null->!null")
   @Nullable
   static SafeMana conv(@Nullable ManaReceiver be) {
      if (be == null) {
         return null;
      } else {
         return be instanceof SafeMana ? (SafeMana)be : Fail.make(be);
      }
   }
}
