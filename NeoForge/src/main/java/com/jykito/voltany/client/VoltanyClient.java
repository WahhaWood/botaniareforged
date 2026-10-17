package com.jykito.voltany.client;

import com.jykito.voltany.block.entity.ManaGeneratorBlockEntity;
import com.jykito.voltany.client.screen.ManaGeneratorScreen;
import com.jykito.voltany.registry.ModBlockEntities;
import com.jykito.voltany.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

@EventBusSubscriber(modid = BotaniaAPI.MODID, value = Dist.CLIENT)
public final class VoltanyClient {
	private VoltanyClient() {}

	@SubscribeEvent
	public static void registerScreens(RegisterMenuScreensEvent e) {
		e.register(ModMenus.MANA_GENERATOR.get(), ManaGeneratorScreen::new);
	}

	@SubscribeEvent
	public static void registerClientCapabilities(RegisterCapabilitiesEvent e) {
		e.registerBlockEntity(
				BotaniaNeoForgeCapabilities.getBlockApiLookupById(WandHUD.BLOCK_LOOKUP),
				ModBlockEntities.MANA_GENERATOR.get(),
				(be, side) -> new ManaGeneratorWandHud((ManaGeneratorBlockEntity) be));
	}
}
