package com.jykito.voltany.registry;

import com.jykito.voltany.VoltanyInit;
import com.jykito.voltany.block.ManaGeneratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VoltanyInit.MODID);

	public static final DeferredBlock<ManaGeneratorBlock> MANA_GENERATOR = BLOCKS.register("mana_generator",
			() -> new ManaGeneratorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(3.5F, 6.0F)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> state.getValue(ManaGeneratorBlock.LIT) ? 7 : 0)));

	private ModBlocks() {}
}
