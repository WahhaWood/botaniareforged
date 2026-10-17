package vazkii.botania.ae2;

import vazkii.botania.ae2.block.FluixPool;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;

public class ABBlocks {
   private static final Blocks BLOCKS = DeferredRegister.createBlocks("botania");
   public static final DeferredBlock<Block> FLUIX_MANA_POOL = BLOCKS.register("fluix_mana_pool", FluixPool::new);

   public static void initialize(IEventBus bus) {
      BLOCKS.register(bus);
   }
}
