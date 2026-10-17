package vazkii.botania.ae2.mixins;

import vazkii.botania.ae2.ABBlocks;
import vazkii.botania.ae2.block.FluixPoolBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;

@Mixin(
   value = {BotaniaBlockEntities.class},
   remap = false
)
public abstract class BotaniaBlockEntitiesMixin {
   @ModifyVariable(
      method = {"type(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier;[Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/world/level/block/entity/BlockEntityType;"},
      at = @At("HEAD"),
      index = 1,
      argsOnly = true
   )
   private static <T extends BlockEntity> BlockEntitySupplier<T> k(
      BlockEntitySupplier<T> factory, ResourceLocation id, BlockEntitySupplier<T> $factory, Block[] blocks
   ) {
      return BotaniaAPI.botaniaRL("mana_pool").equals(id)
         ? (blockPos, blockState) -> (T)(blockState.is(ABBlocks.FLUIX_MANA_POOL.get())
               ? new FluixPoolBlockEntity(blockPos, blockState)
               : factory.create(blockPos, blockState))
         : factory;
   }

   @ModifyVariable(
      method = {"type(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier;[Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/world/level/block/entity/BlockEntityType;"},
      at = @At("HEAD"),
      index = 2,
      argsOnly = true
   )
   private static <T extends BlockEntity> Block[] t(Block[] blocks, ResourceLocation id, BlockEntitySupplier<T> $factory, Block[] $blocks) {
      return BotaniaAPI.botaniaRL("mana_pool").equals(id) ? (Block[])ArrayUtils.add(blocks, ABBlocks.FLUIX_MANA_POOL.get()) : blocks;
   }
}
