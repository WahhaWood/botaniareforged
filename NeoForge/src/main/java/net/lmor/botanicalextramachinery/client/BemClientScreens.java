package net.lmor.botanicalextramachinery.client;

import net.lmor.botanicalextramachinery.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import net.lmor.botanicalextramachinery.blocks.screens.mechanicalAlfheimMarket.ScreenAlfheimMarketAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalAlfheimMarket.ScreenAlfheimMarketBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalAlfheimMarket.ScreenAlfheimMarketUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalAlfheimMarket.ScreenAlfheimMarketUpgraded;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalApothecary.ScreenApothecaryAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalApothecary.ScreenApothecaryBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalApothecary.ScreenApothecaryUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalApothecary.ScreenApothecaryUpgraded;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalDaisy.ScreenDaisyAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalDaisy.ScreenDaisyBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalDaisy.ScreenDaisyUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalDaisy.ScreenDaisyUpgraded;
import net.lmor.botanicalextramachinery.blocks.screens.ScreenGreenhouse;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryUpgraded;
import net.lmor.botanicalextramachinery.blocks.screens.ScreenJadedAmaranthus;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaPool.ScreenManaPoolAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaPool.ScreenManaPoolBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaPool.ScreenManaPoolUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaPool.ScreenManaPoolUpgraded;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalOrechid.ScreenOrechidAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalOrechid.ScreenOrechidBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalOrechid.ScreenOrechidUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalOrechid.ScreenOrechidUpgraded;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalRunicAltar.ScreenRunicAltarAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalRunicAltar.ScreenRunicAltarBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalRunicAltar.ScreenRunicAltarUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalRunicAltar.ScreenRunicAltarUpgraded;

/**
 * Replaces the per-block MenuScreens.register calls of the original mod
 * (private API in 1.21.1): all machine screens are registered centrally.
 */
@EventBusSubscriber(modid = "botanicalextramachinery", value = Dist.CLIENT)
public final class BemClientScreens {

	private BemClientScreens() {}

	@SubscribeEvent
	public static void registerScreens(RegisterMenuScreensEvent e) {
		e.register(ModBlocks.advancedAlfheimMarket.menu, ScreenAlfheimMarketAdvanced::new);
		e.register(ModBlocks.baseAlfheimMarket.menu, ScreenAlfheimMarketBase::new);
		e.register(ModBlocks.ultimateAlfheimMarket.menu, ScreenAlfheimMarketUltimate::new);
		e.register(ModBlocks.upgradedAlfheimMarket.menu, ScreenAlfheimMarketUpgraded::new);
		e.register(ModBlocks.advancedApothecary.menu, ScreenApothecaryAdvanced::new);
		e.register(ModBlocks.baseApothecary.menu, ScreenApothecaryBase::new);
		e.register(ModBlocks.ultimateApothecary.menu, ScreenApothecaryUltimate::new);
		e.register(ModBlocks.upgradedApothecary.menu, ScreenApothecaryUpgraded::new);
		e.register(ModBlocks.advancedDaisy.menu, ScreenDaisyAdvanced::new);
		e.register(ModBlocks.baseDaisy.menu, ScreenDaisyBase::new);
		e.register(ModBlocks.ultimateDaisy.menu, ScreenDaisyUltimate::new);
		e.register(ModBlocks.upgradedDaisy.menu, ScreenDaisyUpgraded::new);
		e.register(ModBlocks.greenhouse.menu, ScreenGreenhouse::new);
		e.register(ModBlocks.advancedIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryAdvanced::new);
		e.register(ModBlocks.baseIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryBase::new);
		e.register(ModBlocks.ultimateIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryUltimate::new);
		e.register(ModBlocks.upgradedIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryUpgraded::new);
		e.register(ModBlocks.jadedAmaranthus.menu, ScreenJadedAmaranthus::new);
		e.register(ModBlocks.advancedManaPool.menu, ScreenManaPoolAdvanced::new);
		e.register(ModBlocks.baseManaPool.menu, ScreenManaPoolBase::new);
		e.register(ModBlocks.ultimateManaPool.menu, ScreenManaPoolUltimate::new);
		e.register(ModBlocks.upgradedManaPool.menu, ScreenManaPoolUpgraded::new);
		e.register(ModBlocks.advancedOrechid.menu, ScreenOrechidAdvanced::new);
		e.register(ModBlocks.baseOrechid.menu, ScreenOrechidBase::new);
		e.register(ModBlocks.ultimateOrechid.menu, ScreenOrechidUltimate::new);
		e.register(ModBlocks.upgradedOrechid.menu, ScreenOrechidUpgraded::new);
		e.register(ModBlocks.advancedRunicAltar.menu, ScreenRunicAltarAdvanced::new);
		e.register(ModBlocks.baseRunicAltar.menu, ScreenRunicAltarBase::new);
		e.register(ModBlocks.ultimateRunicAltar.menu, ScreenRunicAltarUltimate::new);
		e.register(ModBlocks.upgradedRunicAltar.menu, ScreenRunicAltarUpgraded::new);
	}
}
