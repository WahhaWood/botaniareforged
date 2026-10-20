package net.lmor.botanicalextramachinery.core;

import net.lmor.botanicalextramachinery.ExtraMachinery;
import net.lmor.botanicalextramachinery.ae2.MeOutputNode;
import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityApothecaryPattern;
import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityDaisyPattern;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

/**
 * Replaces the LibX annotation-processor generated registration glue
 * (ExtraMachinery$ of the original mod). The order mirrors the original
 * generated code exactly. Menu types, block entity types and block items
 * are expanded automatically by the vendored LibX Registerable machinery.
 */
public final class BemRegistration {

	private BemRegistration() {}

	public static void registerAll(ExtraMachinery mod) {
		mod.register(Registries.BLOCK, "malachite_dragonstone_block", ModBlocks.malachiteDragonstoneBlock);
		mod.register(Registries.BLOCK, "malachite_ingot_block", ModBlocks.malachiteIngotBlock);
		mod.register(Registries.BLOCK, "saffron_dragonstone_block", ModBlocks.saffronDragonstoneBlock);
		mod.register(Registries.BLOCK, "saffron_ingot_block", ModBlocks.saffronIngotBlock);
		mod.register(Registries.BLOCK, "shadow_dragonstone_block", ModBlocks.shadowDragonstoneBlock);
		mod.register(Registries.BLOCK, "shadow_ingot_block", ModBlocks.shadowIngotBlock);
		mod.register(Registries.BLOCK, "crimson_dragonstone_block", ModBlocks.crimsonDragonstoneBlock);
		mod.register(Registries.BLOCK, "crimson_ingot_block", ModBlocks.crimsonIngotBlock);
		mod.register(Registries.BLOCK, "aureate_dragonstone_block", ModBlocks.aureateDragonstoneBlock);
		mod.register(Registries.BLOCK, "aureate_ingot_block", ModBlocks.aureateIngotBlock);
		mod.register(Registries.BLOCK, "mazarine_dragonstone_block", ModBlocks.mazarineDragonstoneBlock);
		mod.register(Registries.BLOCK, "mazarine_ingot_block", ModBlocks.mazarineIngotBlock);
		mod.register(Registries.BLOCK, "crystal_dragonstone_block", ModBlocks.crystalDragonstoneBlock);
		mod.register(Registries.BLOCK, "crystal_ingot_block", ModBlocks.crystalIngotBlock);
		mod.register(Registries.BLOCK, "base_mana_pool", ModBlocks.baseManaPool);
		mod.register(Registries.BLOCK, "upgraded_mana_pool", ModBlocks.upgradedManaPool);
		mod.register(Registries.BLOCK, "advanced_mana_pool", ModBlocks.advancedManaPool);
		mod.register(Registries.BLOCK, "ultimate_mana_pool", ModBlocks.ultimateManaPool);
		mod.register(Registries.BLOCK, "base_runic_altar", ModBlocks.baseRunicAltar);
		mod.register(Registries.BLOCK, "upgraded_runic_altar", ModBlocks.upgradedRunicAltar);
		mod.register(Registries.BLOCK, "advanced_runic_altar", ModBlocks.advancedRunicAltar);
		mod.register(Registries.BLOCK, "ultimate_runic_altar", ModBlocks.ultimateRunicAltar);
		mod.register(Registries.BLOCK, "base_daisy", ModBlocks.baseDaisy);
		mod.register(Registries.BLOCK, "upgraded_daisy", ModBlocks.upgradedDaisy);
		mod.register(Registries.BLOCK, "advanced_daisy", ModBlocks.advancedDaisy);
		mod.register(Registries.BLOCK, "ultimate_daisy", ModBlocks.ultimateDaisy);
		mod.register(Registries.BLOCK, "base_apothecary", ModBlocks.baseApothecary);
		mod.register(Registries.BLOCK, "upgraded_apothecary", ModBlocks.upgradedApothecary);
		mod.register(Registries.BLOCK, "advanced_apothecary", ModBlocks.advancedApothecary);
		mod.register(Registries.BLOCK, "ultimate_apothecary", ModBlocks.ultimateApothecary);
		mod.register(Registries.BLOCK, "base_industrial_agglomeration_factory", ModBlocks.baseIndustrialAgglomerationFactory);
		mod.register(Registries.BLOCK, "upgraded_industrial_agglomeration_factory", ModBlocks.upgradedIndustrialAgglomerationFactory);
		mod.register(Registries.BLOCK, "advanced_industrial_agglomeration_factory", ModBlocks.advancedIndustrialAgglomerationFactory);
		mod.register(Registries.BLOCK, "ultimate_industrial_agglomeration_factory", ModBlocks.ultimateIndustrialAgglomerationFactory);
		mod.register(Registries.BLOCK, "base_alfheim_market", ModBlocks.baseAlfheimMarket);
		mod.register(Registries.BLOCK, "upgraded_alfheim_market", ModBlocks.upgradedAlfheimMarket);
		mod.register(Registries.BLOCK, "advanced_alfheim_market", ModBlocks.advancedAlfheimMarket);
		mod.register(Registries.BLOCK, "ultimate_alfheim_market", ModBlocks.ultimateAlfheimMarket);
		mod.register(Registries.BLOCK, "base_orechid", ModBlocks.baseOrechid);
		mod.register(Registries.BLOCK, "upgraded_orechid", ModBlocks.upgradedOrechid);
		mod.register(Registries.BLOCK, "advanced_orechid", ModBlocks.advancedOrechid);
		mod.register(Registries.BLOCK, "ultimate_orechid", ModBlocks.ultimateOrechid);
		mod.register(Registries.BLOCK, "jaded_amaranthus", ModBlocks.jadedAmaranthus);
		mod.register(Registries.BLOCK, "greenhouse", ModBlocks.greenhouse);
		mod.register(Registries.BLOCK, "base_mana_infuser", ModBlocks.baseManaInfuser);
		mod.register(Registries.BLOCK, "upgraded_mana_infuser", ModBlocks.upgradedManaInfuser);
		mod.register(Registries.BLOCK, "advanced_mana_infuser", ModBlocks.advancedManaInfuser);
		mod.register(Registries.BLOCK, "ultimate_mana_infuser", ModBlocks.ultimateManaInfuser);
		mod.register(Registries.ITEM, "malachite_dragonstone", ModItems.malachiteDragonstone);
		mod.register(Registries.ITEM, "malachite_ingot", ModItems.malachiteIngot);
		mod.register(Registries.ITEM, "saffron_dragonstone", ModItems.saffronDragonstone);
		mod.register(Registries.ITEM, "saffron_ingot", ModItems.saffronIngot);
		mod.register(Registries.ITEM, "shadow_dragonstone", ModItems.shadowDragonstone);
		mod.register(Registries.ITEM, "shadow_ingot", ModItems.shadowIngot);
		mod.register(Registries.ITEM, "crimson_dragonstone", ModItems.crimsonDragonstone);
		mod.register(Registries.ITEM, "crimson_ingot", ModItems.crimsonIngot);
		mod.register(Registries.ITEM, "crystal_dragonstone", ModItems.crystalDragonstone);
		mod.register(Registries.ITEM, "crystal_ingot", ModItems.crystalIngot);
		mod.register(Registries.ITEM, "aureate_dragonstone", ModItems.aureateDragonstone);
		mod.register(Registries.ITEM, "aureate_ingot", ModItems.aureateIngot);
		mod.register(Registries.ITEM, "mazarine_dragonstone", ModItems.mazarineDragonstone);
		mod.register(Registries.ITEM, "mazarine_ingot", ModItems.mazarineIngot);
		mod.register(Registries.ITEM, "catalyst_pattern", ModItems.catalystPattern);
		mod.register(Registries.ITEM, "catalyst_mana_infinity", ModItems.catalystManaInfinity);
		mod.register(Registries.ITEM, "catalyst_living_rock_infinity", ModItems.catalystLivingRockInfinity);
		mod.register(Registries.ITEM, "catalyst_water_infinity", ModItems.catalystWaterInfinity);
		mod.register(Registries.ITEM, "catalyst_seed_infinity", ModItems.catalystSeedInfinity);
		mod.register(Registries.ITEM, "catalyst_speed", ModItems.catalystSpeed);
		mod.register(Registries.ITEM, "catalyst_stone_infinity", ModItems.catalystStoneInfinity);
		mod.register(Registries.ITEM, "catalyst_wood_infinity", ModItems.catalystWoodInfinity);
		mod.register(Registries.ITEM, "catalyst_petal", ModItems.catalystPetal);
		mod.register(Registries.ITEM, "catalyst_petal_block", ModItems.catalystPetalBlock);
		mod.register(Registries.ITEM, "base_spark", ModItems.baseSpark);
		mod.register(Registries.ITEM, "malachite_spark", ModItems.malachiteSpark);
		mod.register(Registries.ITEM, "saffron_spark", ModItems.saffronSpark);
		mod.register(Registries.ITEM, "shadow_spark", ModItems.shadowSpark);
		mod.register(Registries.ITEM, "crimson_spark", ModItems.crimsonSpark);
		mod.register(Registries.ITEM, "aureate_spark", ModItems.aureateSpark);
		mod.register(Registries.ITEM, "mazarine_spark", ModItems.mazarineSpark);
		mod.register(Registries.ITEM, "upgrade_pattern", ModItems.upgradePattern);
		mod.register(Registries.ITEM, "upgrade_cost_energy", ModItems.upgradeCostEnergy);
		mod.register(Registries.ITEM, "upgrade_flower_4x", ModItems.upgradeFlower_4x);
		mod.register(Registries.ITEM, "upgrade_flower_16x", ModItems.upgradeFlower_16x);
		mod.register(Registries.ITEM, "upgrade_flower_32x", ModItems.upgradeFlower_32x);
		mod.register(Registries.ITEM, "upgrade_flower_64x", ModItems.upgradeFlower_64x);
		mod.register(Registries.ITEM, "upgrade_gen_mana", ModItems.upgradeGenMana);
		mod.register(Registries.ITEM, "upgrade_heat_greenhouse", ModItems.upgradeHeatGreenhouse);
		mod.register(Registries.ITEM, "upgrade_slot_add", ModItems.upgradeSlotAdd);
		mod.register(Registries.ITEM, "upgrade_storage_mana_1", ModItems.upgradeStorageMana_1);
		mod.register(Registries.ITEM, "upgrade_storage_mana_2", ModItems.upgradeStorageMana_2);
		mod.register(Registries.ITEM, "upgrade_storage_mana_3", ModItems.upgradeStorageMana_3);
		mod.register(Registries.ITEM, "upgrade_storage_energy_1", ModItems.upgradeStorageEnergy_1);
		mod.register(Registries.ITEM, "upgrade_storage_energy_2", ModItems.upgradeStorageEnergy_2);
		mod.register(Registries.ITEM, "upgrade_storage_energy_3", ModItems.upgradeStorageEnergy_3);
		mod.register(Registries.ITEM, "upgrade_tick_gen_mana_1", ModItems.upgradeTickGenMana_1);
		mod.register(Registries.ITEM, "upgrade_tick_gen_mana_2", ModItems.upgradeTickGenMana_2);
	}

	/**
	 * Register NeoForge capabilities for all BEM block entities.
	 * Called from the mod event bus.
	 */
	public static void registerCapabilities(RegisterCapabilitiesEvent evt) {
		// ManaReceiver + ManaSparkAttachable + WandHUD for all machine blocks
		registerMachineCapabilities(evt, ModBlocks.baseManaPool);
		registerMachineCapabilities(evt, ModBlocks.upgradedManaPool);
		registerMachineCapabilities(evt, ModBlocks.advancedManaPool);
		registerMachineCapabilities(evt, ModBlocks.ultimateManaPool);
		registerMachineCapabilities(evt, ModBlocks.baseRunicAltar);
		registerMachineCapabilities(evt, ModBlocks.upgradedRunicAltar);
		registerMachineCapabilities(evt, ModBlocks.advancedRunicAltar);
		registerMachineCapabilities(evt, ModBlocks.ultimateRunicAltar);
		registerMachineCapabilities(evt, ModBlocks.baseDaisy);
		registerMachineCapabilities(evt, ModBlocks.upgradedDaisy);
		registerMachineCapabilities(evt, ModBlocks.advancedDaisy);
		registerMachineCapabilities(evt, ModBlocks.ultimateDaisy);
		registerMachineCapabilities(evt, ModBlocks.baseApothecary);
		registerMachineCapabilities(evt, ModBlocks.upgradedApothecary);
		registerMachineCapabilities(evt, ModBlocks.advancedApothecary);
		registerMachineCapabilities(evt, ModBlocks.ultimateApothecary);
		registerMachineCapabilities(evt, ModBlocks.baseIndustrialAgglomerationFactory);
		registerMachineCapabilities(evt, ModBlocks.upgradedIndustrialAgglomerationFactory);
		registerMachineCapabilities(evt, ModBlocks.advancedIndustrialAgglomerationFactory);
		registerMachineCapabilities(evt, ModBlocks.ultimateIndustrialAgglomerationFactory);
		registerMachineCapabilities(evt, ModBlocks.baseAlfheimMarket);
		registerMachineCapabilities(evt, ModBlocks.upgradedAlfheimMarket);
		registerMachineCapabilities(evt, ModBlocks.advancedAlfheimMarket);
		registerMachineCapabilities(evt, ModBlocks.ultimateAlfheimMarket);
		registerMachineCapabilities(evt, ModBlocks.baseOrechid);
		registerMachineCapabilities(evt, ModBlocks.upgradedOrechid);
		registerMachineCapabilities(evt, ModBlocks.advancedOrechid);
		registerMachineCapabilities(evt, ModBlocks.ultimateOrechid);
		registerMachineCapabilities(evt, ModBlocks.jadedAmaranthus);
		registerMachineCapabilities(evt, ModBlocks.greenhouse);
		registerMachineCapabilities(evt, ModBlocks.baseManaInfuser);
		registerMachineCapabilities(evt, ModBlocks.upgradedManaInfuser);
		registerMachineCapabilities(evt, ModBlocks.advancedManaInfuser);
		registerMachineCapabilities(evt, ModBlocks.ultimateManaInfuser);

		// Fluid input. Without these the tanks the machines build in their own
		// constructors are unreachable from outside: nothing can fill them, so
		// the apothecary's canMatchRecipes() is always false and the daisy's
		// water-to-snow branch never runs.
		registerFluidHandler(evt, ModBlocks.baseApothecary, ApothecaryTank::of);
		registerFluidHandler(evt, ModBlocks.upgradedApothecary, ApothecaryTank::of);
		registerFluidHandler(evt, ModBlocks.advancedApothecary, ApothecaryTank::of);
		registerFluidHandler(evt, ModBlocks.ultimateApothecary, ApothecaryTank::of);
		registerFluidHandler(evt, ModBlocks.baseDaisy, DaisyTank::of);
		registerFluidHandler(evt, ModBlocks.upgradedDaisy, DaisyTank::of);
		registerFluidHandler(evt, ModBlocks.advancedDaisy, DaisyTank::of);
		registerFluidHandler(evt, ModBlocks.ultimateDaisy, DaisyTank::of);

		// The greenhouse runs on Forge energy. It implemented IEnergyStorage all
		// along but never had the capability registered, so its tick() bailed out
		// on the energy check and it never produced mana.
		evt.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlocks.greenhouse.getBlockEntityType(),
				(be, side) -> (IEnergyStorage) be);
	}

	/** How a block entity hands out the tank behind its FluidHandler capability. */
	private interface TankSource {
		IFluidHandler of(Object blockEntity);
	}

	private static final TankSource ApothecaryTank =
			be -> ((BlockEntityApothecaryPattern) be).getFluidInventory();
	private static final TankSource DaisyTank =
			be -> ((BlockEntityDaisyPattern) be).getTankCap();

	private static void registerFluidHandler(RegisterCapabilitiesEvent evt,
			org.moddingx.libx.base.tile.MenuBlockBE<?, ?> block, TankSource source) {
		evt.registerBlockEntity(Capabilities.FluidHandler.BLOCK, block.getBlockEntityType(),
				(be, side) -> source.of(be));
	}

	private static void registerMachineCapabilities(RegisterCapabilitiesEvent evt, org.moddingx.libx.base.tile.MenuBlockBE<?, ?> block) {
		var beType = block.getBlockEntityType();
		// ManaReceiver (acts as mana pool)
		evt.registerBlockEntity(
				BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP),
				beType,
				(be, side) -> (ManaReceiver) be);
		// ManaSparkAttachable
		evt.registerBlockEntity(
				BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaSparkAttachable.LOOKUP),
				beType,
				(be, side) -> (ManaSparkAttachable) be);
		// WandHUD: the lookup is registered by ClientBlockCapabilities only, because the wand HUD
		// is a client-only feature. On a dedicated server getBlockApiLookupById returns null and
		// registerBlockEntity would NPE, so skip it when it is not there.
		var wandHudLookup = BotaniaNeoForgeCapabilities.getBlockApiLookupById(WandHUD.BLOCK_LOOKUP);
		if (wandHudLookup != null) {
			evt.registerBlockEntity(
					wandHudLookup,
					beType,
					(be, side) -> (WandHUD) be);
		}
		// AE2 finds an in-world grid node only through this capability. Without it
		// cables never connect and the ME export of every machine stays dead.
		if (MeOutput.AE2) {
			MeOutputNode.registerCapability(evt, beType);
		}
		// Item handler
		evt.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				beType,
				(be, side) -> ((net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalTile) be).getItemHandlerCap());
	}
}
