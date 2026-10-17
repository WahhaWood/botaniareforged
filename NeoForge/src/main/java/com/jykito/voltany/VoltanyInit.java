package com.jykito.voltany;

import com.jykito.voltany.registry.ModBlockEntities;
import com.jykito.voltany.registry.ModBlocks;
import com.jykito.voltany.registry.ModCreativeTab;
import com.jykito.voltany.registry.ModItems;
import com.jykito.voltany.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;

/**
 * Voltany (by jykito), merged into BotaniaReforged. Original: standalone
 * Forge 1.20.1 mod; registry namespaces are preserved ("voltany:").
 */
public final class VoltanyInit {
	public static final String MODID = "voltany";
	public static final Logger LOGGER = LogUtils.getLogger();

	private VoltanyInit() {}

	public static void init(IEventBus modBus) {
		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModBlockEntities.BLOCK_ENTITIES.register(modBus);
		ModMenus.MENUS.register(modBus);
		ModCreativeTab.TABS.register(modBus);
		modBus.addListener(VoltanyCapabilities::registerCapabilities);
		LOGGER.info("Voltany merged into BotaniaReforged");
	}
}
