package net.lmor.botanicalextramachinery.client;

import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.blocks.screens.ScreenGreenhouse;
import net.lmor.botanicalextramachinery.blocks.screens.ScreenJadedAmaranthus;
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
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalIndustrialAgglomerationFactory.ScreenIndustrialAgglomerationFactoryUpgraded;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaInfuser.ScreenManaInfuserAdvanced;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaInfuser.ScreenManaInfuserBase;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaInfuser.ScreenManaInfuserUltimate;
import net.lmor.botanicalextramachinery.blocks.screens.mechanicalManaInfuser.ScreenManaInfuserUpgraded;
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
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Binds every BEM container to its screen.
 *
 * <p>On Forge 1.20.1 each machine block did this from its own {@code registerClient} hook via
 * {@code MenuScreens.register(...)}. 1.21.1 removed that method entirely — screens are now wired
 * through {@link RegisterMenuScreensEvent} — so the per-block calls were dropped during the port
 * and the mappings collected here instead. Without this, right-clicking a machine opens the
 * container server-side but leaves the player with no screen ("Failed to create screen for menu
 * type" in the log).
 */
public final class BemMenuScreens {

    private BemMenuScreens() {}

    public static void register(RegisterMenuScreensEvent event) {
        // Jaded Amaranthus & Greenhouse
        event.register(ModBlocks.jadedAmaranthus.menu, ScreenJadedAmaranthus::new);
        event.register(ModBlocks.greenhouse.menu, ScreenGreenhouse::new);

        // Mana Pool
        event.register(ModBlocks.baseManaPool.menu, ScreenManaPoolBase::new);
        event.register(ModBlocks.upgradedManaPool.menu, ScreenManaPoolUpgraded::new);
        event.register(ModBlocks.advancedManaPool.menu, ScreenManaPoolAdvanced::new);
        event.register(ModBlocks.ultimateManaPool.menu, ScreenManaPoolUltimate::new);

        // Runic Altar
        event.register(ModBlocks.baseRunicAltar.menu, ScreenRunicAltarBase::new);
        event.register(ModBlocks.upgradedRunicAltar.menu, ScreenRunicAltarUpgraded::new);
        event.register(ModBlocks.advancedRunicAltar.menu, ScreenRunicAltarAdvanced::new);
        event.register(ModBlocks.ultimateRunicAltar.menu, ScreenRunicAltarUltimate::new);

        // Daisy
        event.register(ModBlocks.baseDaisy.menu, ScreenDaisyBase::new);
        event.register(ModBlocks.upgradedDaisy.menu, ScreenDaisyUpgraded::new);
        event.register(ModBlocks.advancedDaisy.menu, ScreenDaisyAdvanced::new);
        event.register(ModBlocks.ultimateDaisy.menu, ScreenDaisyUltimate::new);

        // Orechid
        event.register(ModBlocks.baseOrechid.menu, ScreenOrechidBase::new);
        event.register(ModBlocks.upgradedOrechid.menu, ScreenOrechidUpgraded::new);
        event.register(ModBlocks.advancedOrechid.menu, ScreenOrechidAdvanced::new);
        event.register(ModBlocks.ultimateOrechid.menu, ScreenOrechidUltimate::new);

        // Alfheim Market
        event.register(ModBlocks.baseAlfheimMarket.menu, ScreenAlfheimMarketBase::new);
        event.register(ModBlocks.upgradedAlfheimMarket.menu, ScreenAlfheimMarketUpgraded::new);
        event.register(ModBlocks.advancedAlfheimMarket.menu, ScreenAlfheimMarketAdvanced::new);
        event.register(ModBlocks.ultimateAlfheimMarket.menu, ScreenAlfheimMarketUltimate::new);

        // Apothecary
        event.register(ModBlocks.baseApothecary.menu, ScreenApothecaryBase::new);
        event.register(ModBlocks.upgradedApothecary.menu, ScreenApothecaryUpgraded::new);
        event.register(ModBlocks.advancedApothecary.menu, ScreenApothecaryAdvanced::new);
        event.register(ModBlocks.ultimateApothecary.menu, ScreenApothecaryUltimate::new);

        // Industrial Agglomeration Factory
        event.register(ModBlocks.baseIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryBase::new);
        event.register(ModBlocks.upgradedIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryUpgraded::new);
        event.register(ModBlocks.advancedIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryAdvanced::new);
        event.register(ModBlocks.ultimateIndustrialAgglomerationFactory.menu, ScreenIndustrialAgglomerationFactoryUltimate::new);

        // Mana Infuser
        event.register(ModBlocks.baseManaInfuser.menu, ScreenManaInfuserBase::new);
        event.register(ModBlocks.upgradedManaInfuser.menu, ScreenManaInfuserUpgraded::new);
        event.register(ModBlocks.advancedManaInfuser.menu, ScreenManaInfuserAdvanced::new);
        event.register(ModBlocks.ultimateManaInfuser.menu, ScreenManaInfuserUltimate::new);
    }
}