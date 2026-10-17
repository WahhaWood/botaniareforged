package vazkii.botania.ae2.ae2;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.storage.MEStorage;
import appeng.core.AELog;
import com.google.common.primitives.Ints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;
import vazkii.botania.api.mana.ManaReceiver;

public class ManaStorageImportStrategy implements StackImportStrategy {
   private final BlockCapabilityCache<ManaReceiver, Direction> apiCache;

   public ManaStorageImportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
      this.apiCache = BlockCapabilityCache.create(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP), level, fromPos, fromSide);
   }

   @Override
   public boolean transfer(StackTransferContext context) {
      if (!context.isKeyTypeEnabled(ManaKeyType.TYPE)) {
         return false;
      } else if (context.isInFilter(ManaKey.KEY) == context.isInverted()) {
         return false;
      } else {
         SafeMana receiver = SafeMana.conv(this.apiCache.getCapability());
         if (receiver == null) {
            return false;
         } else {
            int amountPerOperation = ManaKeyType.TYPE.getAmountPerOperation();
            long maxTransfer = (long)context.getOperationsRemaining() * (long)amountPerOperation;
            if (maxTransfer <= 0L) {
               return false;
            } else {
               MEStorage inventory = context.getInternalStorage().getInventory();
               long simulate = inventory.insert(ManaKey.KEY, maxTransfer, Actionable.SIMULATE, context.getActionSource());
               int extracted = receiver.extract(Ints.saturatedCast(simulate), Actionable.MODULATE);
               long inserted = inventory.insert(ManaKey.KEY, (long)extracted, Actionable.MODULATE, context.getActionSource());
               if (inserted < (long)extracted) {
                  long difference = (long)extracted - inserted;
                  difference -= (long)receiver.insert(Ints.saturatedCast(difference), Actionable.MODULATE);
                  if (difference != 0L) {
                     AELog.warn("Extracted %d Mana from adjacent storage and voided it because network refused insert");
                  }
               }

               context.reduceOperationsRemaining((inserted + (long)amountPerOperation - 1L) / (long)amountPerOperation);
               return false;
            }
         }
      }
   }
}
