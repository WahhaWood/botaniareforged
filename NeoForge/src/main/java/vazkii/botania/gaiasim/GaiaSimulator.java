package vazkii.botania.gaiasim;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.BotaniaRegistries;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;
import vazkii.botania.gaiasim.client.GaiaSimulatorScreen;

/**
 * Registry entries of the Gaia Simulator: a multiblock that replays a hard-mode Gaia Guardian fight for mana
 * and FE and hands out the same loot (see {@link GaiaSimulatorBlockEntity}).
 */
public final class GaiaSimulator {
	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BotaniaAPI.MODID);
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BotaniaAPI.MODID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BotaniaAPI.MODID);
	private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BotaniaAPI.MODID);

	public static final DeferredBlock<GaiaSimulatorBlock> BLOCK = BLOCKS.register("gaia_simulator",
			() -> new GaiaSimulatorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(5.0F, 1200.0F)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)
					.lightLevel(state -> state.getValue(GaiaSimulatorBlock.LIT) ? 10 : 0)));
	public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK, new Item.Properties().rarity(Rarity.RARE));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GaiaSimulatorBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register(
			"gaia_simulator", () -> BlockEntityType.Builder.of(GaiaSimulatorBlockEntity::new, BLOCK.get()).build(null));
	public static final DeferredHolder<MenuType<?>, MenuType<GaiaSimulatorMenu>> MENU = MENUS.register(
			"gaia_simulator", () -> IMenuTypeExtension.create(GaiaSimulatorMenu::new));

	private GaiaSimulator() {}

	public static void initialize(IEventBus bus) {
		BLOCKS.register(bus);
		ITEMS.register(bus);
		BLOCK_ENTITIES.register(bus);
		MENUS.register(bus);
		bus.addListener(GaiaSimulator::registerCapabilities);
		bus.addListener(GaiaSimulator::addToCreativeTab);
		if (FMLEnvironment.dist.isClient()) {
			bus.addListener((RegisterMenuScreensEvent event) -> event.register(MENU.get(), GaiaSimulatorScreen::new));
		}
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP),
				BLOCK_ENTITY.get(), (be, side) -> be);
		event.registerBlockEntity(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaSparkAttachable.LOOKUP),
				BLOCK_ENTITY.get(), (be, side) -> be);
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BLOCK_ENTITY.get(), (be, side) -> be.getEnergy());
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(), (be, side) -> be.getOutput());
	}

	private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == BotaniaRegistries.BOTANIA_TAB_KEY) {
			event.accept(ITEM.get());
		}
	}
}
