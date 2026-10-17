package com.jykito.voltany;

import com.jykito.voltany.block.entity.ManaGeneratorBlockEntity;
import com.jykito.voltany.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

/**
 * Registers the generator's Botania/NeoForge capabilities: it presents itself
 * as a ManaReceiver/ManaPool (for passive distribution) and ManaSparkAttachable
 * (for spark-resonator mode), plus an FE energy view for automation.
 */
public final class VoltanyCapabilities {
	private VoltanyCapabilities() {}

	public static void registerCapabilities(RegisterCapabilitiesEvent e) {
		e.registerBlockEntity(
				BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP),
				ModBlockEntities.MANA_GENERATOR.get(),
				(be, side) -> (ManaReceiver) be);
		e.registerBlockEntity(
				BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaSparkAttachable.LOOKUP),
				ModBlockEntities.MANA_GENERATOR.get(),
				(be, side) -> (ManaSparkAttachable) be);
		e.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.MANA_GENERATOR.get(),
				(be, side) -> be.getEnergy());
	}
}
