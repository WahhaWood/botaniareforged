package com.jykito.voltany.registry;

import com.jykito.voltany.VoltanyInit;
import com.jykito.voltany.block.entity.ManaGeneratorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
			.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, VoltanyInit.MODID);

	public static final Supplier<BlockEntityType<ManaGeneratorBlockEntity>> MANA_GENERATOR = BLOCK_ENTITIES
			.register("mana_generator", () -> BlockEntityType.Builder
					.of(ManaGeneratorBlockEntity::new, ModBlocks.MANA_GENERATOR.get()).build(null));

	private ModBlockEntities() {}
}
