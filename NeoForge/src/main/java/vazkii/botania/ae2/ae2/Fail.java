package vazkii.botania.ae2.ae2;

import appeng.api.config.Actionable;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vazkii.botania.api.mana.ManaReceiver;

class Fail {
   private static final Logger LOGGER = LoggerFactory.getLogger(SafeMana.class);
   private static final Set<String> KNOWN = new HashSet<>();

   static SafeMana make(final ManaReceiver be) {
      if (KNOWN.add(be.getClass().getName())) {
         LOGGER.error(
            "Applied Botanics does not know how to insert and extract Mana out of {} ({}). No extraction will be permitted, and insertions could be widely lossy.",
            be,
            be.getClass().getName()
         );
      }

      return new SafeMana() {
         @Override
         public int insert(int amount, Actionable mode) {
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
      };
   }
}
