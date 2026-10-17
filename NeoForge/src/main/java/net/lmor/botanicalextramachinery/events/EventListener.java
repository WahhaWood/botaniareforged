package net.lmor.botanicalextramachinery.events;

import net.lmor.botanicalextramachinery.blocks.flowersGreenhouse.GenFlowers;
import net.lmor.botanicalextramachinery.blocks.flowersGreenhouse.flowers.*;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolUltimate;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolUpgraded;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import vazkii.botania.common.block.BotaniaBlocks;

/** Registered manually from {@code ExtraMachinery}, see {@link TooltipHandler}. */
public class EventListener {

	public static void register() {
		NeoForge.EVENT_BUS.addListener(EventListener::resourcesReload);
		NeoForge.EVENT_BUS.addListener(EventListener::flowerInit);
	}

	@SubscribeEvent
	public static void resourcesReload(OnDatapackSyncEvent event) {
		BlockEntityManaPoolBase.invalidateCatalysts();
		BlockEntityManaPoolUpgraded.invalidateCatalysts();
		BlockEntityManaPoolAdvanced.invalidateCatalysts();
		BlockEntityManaPoolUltimate.invalidateCatalysts();
	}

	@SubscribeEvent
	public static void flowerInit(OnDatapackSyncEvent event) {
		initGeneratorFlowers();
	}

	/**
	 * Fills {@link GenFlowers} with the flowers the greenhouse knows how to grow.
	 * <p>
	 * This used to run only on {@link OnDatapackSyncEvent}, which a dedicated
	 * server fires when a player joins. Until then — and on a server that never
	 * sees a player, including the game test server — the table stayed empty:
	 * {@code GenFlowers.isFlower} rejected every flower the greenhouse was
	 * offered, so its fuel lookup found nothing and it never produced mana
	 * despite having energy, a flower and fuel. Called from common setup now,
	 * with this event kept as a refresh after a datapack reload.
	 */
	public static void initGeneratorFlowers() {
		GenFlowers.addAllGenFlowers(BotaniaBlocks.ENDOFLAME.asItem(), new Endoflame());
		GenFlowers.addAllGenFlowers(BotaniaBlocks.ENTROPINNYUM.asItem(), new Entropinnyum());
		GenFlowers.addAllGenFlowers(BotaniaBlocks.GOURMARYLLIS.asItem(), new Gourmaryllis());
		GenFlowers.addAllGenFlowers(BotaniaBlocks.KEKIMURUS.asItem(), new Kekimurus());
		GenFlowers.addAllGenFlowers(BotaniaBlocks.MUNCHDEW.asItem(), new Munchdew());
		GenFlowers.addAllGenFlowers(BotaniaBlocks.NARSLIMMUS.asItem(), new Narslimmus());
		GenFlowers.addAllGenFlowers(BotaniaBlocks.RAFFLOWSIA.asItem(), new Rafflowsia());
		GenFlowers.addAllGenFlowers(BotaniaBlocks.ROSA_ARCANA.asItem(), new RosaArcana());

		// TODO(BotaniaReforged): MythicBotany integration (witherAconite) pending a 1.21.1 MythicBotany.

	}
}
