package vazkii.botania.ae2.ae2;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.google.common.primitives.Ints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;
import vazkii.botania.api.mana.ManaReceiver;

public class ManaStorageExportStrategy implements StackExportStrategy {
   private static final Logger LOGGER = LoggerFactory.getLogger(ManaStorageExportStrategy.class);
   private final BlockCapabilityCache<ManaReceiver, Direction> apiCache;

   public ManaStorageExportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
      this.apiCache = BlockCapabilityCache.create(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP), level, fromPos, fromSide);
   }

   @Override
   public long transfer(StackTransferContext context, AEKey what, long amount) {
      if (!(what instanceof ManaKey)) {
         return 0L;
      } else {
         SafeMana receiver = SafeMana.conv(this.apiCache.getCapability());
         if (receiver == null) {
            return 0L;
         } else {
            IStorageService inv = context.getInternalStorage();
            long extracted = StorageHelper.poweredExtraction(
               context.getEnergySource(), inv.getInventory(), what, amount, context.getActionSource(), Actionable.SIMULATE
            );
            long wasInserted = (long)receiver.insert(Ints.saturatedCast(extracted), Actionable.SIMULATE);
            if (wasInserted > 0L) {
               extracted = StorageHelper.poweredExtraction(
                  context.getEnergySource(), inv.getInventory(), what, wasInserted, context.getActionSource(), Actionable.MODULATE
               );
               wasInserted = (long)receiver.insert(Ints.saturatedCast(extracted), Actionable.MODULATE);
               if (wasInserted < extracted) {
                  long leftover = extracted - wasInserted;
                  leftover -= inv.getInventory().insert(what, leftover, Actionable.MODULATE, context.getActionSource());
                  if (leftover > 0L) {
                     LOGGER.error("Storage export: adjacent block unexpectedly refused insert, voided {} Mana", leftover);
                  }
               }
            }

            return wasInserted;
         }
      }
   }

   @Override
   public long push(AEKey what, long amount, Actionable mode) {
      if (!(what instanceof ManaKey)) {
         return 0L;
      } else {
         SafeMana receiver = SafeMana.conv(this.apiCache.getCapability());
         return receiver == null ? 0L : (long)receiver.insert(Ints.saturatedCast(amount), mode);
      }
   }
}
