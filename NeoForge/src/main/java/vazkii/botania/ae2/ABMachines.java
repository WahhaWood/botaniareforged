package vazkii.botania.ae2;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import vazkii.botania.ae2.machine.MachineBlock;
import vazkii.botania.ae2.machine.MachineBlockEntity;
import vazkii.botania.ae2.machine.MachineKind;
import vazkii.botania.ae2.machine.MachineMenu;
import vazkii.botania.ae2.machine.MachinePattern;
import vazkii.botania.ae2.machine.ToggleRecipePayload;
import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.blockentity.AEBaseBlockEntity;

/**
 * Registry entries of the recipe-providing ME machines (see {@link vazkii.botania.ae2.machine}): one block, block
 * item and block entity type per {@link MachineKind}, their shared menu, and the hidden item and data
 * component that give machine patterns an identity.
 */
public final class ABMachines {
	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AppliedBotanics.MOD_ID);
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedBotanics.MOD_ID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AppliedBotanics.MOD_ID);
	private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AppliedBotanics.MOD_ID);
	private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AppliedBotanics.MOD_ID);

	/** Recipe id inside the definition of a machine pattern. */
	public static final Supplier<DataComponentType<ResourceLocation>> PATTERN_RECIPE = COMPONENTS.registerComponentType(
			"machine_pattern_recipe", builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
	/** Hidden item that only serves as the definition of machine patterns (not in any tab, no recipe). */
	public static final DeferredItem<Item> RECIPE_PATTERN = ITEMS.registerSimpleItem("machine_recipe_pattern", new Item.Properties().stacksTo(1));

	public static final Map<MachineKind, DeferredBlock<MachineBlock>> BLOCKS_BY_KIND = new EnumMap<>(MachineKind.class);
	public static final Map<MachineKind, DeferredItem<BlockItem>> ITEMS_BY_KIND = new EnumMap<>(MachineKind.class);
	public static final Map<MachineKind, DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>>> TYPES_BY_KIND = new EnumMap<>(MachineKind.class);

	public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MACHINE_MENU = MENUS.register(
			"machine", () -> IMenuTypeExtension.create(MachineMenu::new));

	static {
		for (MachineKind kind : MachineKind.values()) {
			DeferredBlock<MachineBlock> block = BLOCKS.register(kind.id, () -> new MachineBlock(TYPES_BY_KIND.get(kind)));
			BLOCKS_BY_KIND.put(kind, block);
			ITEMS_BY_KIND.put(kind, ITEMS.registerSimpleBlockItem(block,
					kind == MachineKind.ALFHEIM_MARKET ? new Item.Properties().rarity(Rarity.UNCOMMON) : new Item.Properties()));
			TYPES_BY_KIND.put(kind, BLOCK_ENTITIES.register(kind.id,
					() -> BlockEntityType.Builder.of((pos, state) -> new MachineBlockEntity(kind, TYPES_BY_KIND.get(kind).get(), pos, state),
							block.get()).build(null)));
		}
	}

	private ABMachines() {}

	public static void initialize(IEventBus bus) {
		BLOCKS.register(bus);
		ITEMS.register(bus);
		BLOCK_ENTITIES.register(bus);
		MENUS.register(bus);
		COMPONENTS.register(bus);
		bus.addListener(ABMachines::registerCapabilities);
		bus.addListener(ABMachines::commonSetup);
		bus.addListener(ABMachines::registerPayloads);
		NeoForge.EVENT_BUS.addListener(ABMachines::onDatapackSync);
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		// AE2 finds grid nodes in the world through this capability: without it cables never connect
		for (MachineKind kind : MachineKind.values()) {
			event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, TYPES_BY_KIND.get(kind).get(), (machine, context) -> machine);
		}
	}

	private static void commonSetup(FMLCommonSetupEvent event) {
		PatternDetailsHelper.registerDecoder(new MachinePattern.Decoder());
		event.enqueueWork(() -> {
			// the grid node shows this item and the machine is named after it
			for (MachineKind kind : MachineKind.values()) {
				AEBaseBlockEntity.registerBlockEntityItem(TYPES_BY_KIND.get(kind).get(), ITEMS_BY_KIND.get(kind).get());
			}
		});
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("1").playToServer(ToggleRecipePayload.TYPE, ToggleRecipePayload.CODEC, ToggleRecipePayload::handle);
	}

	private static void onDatapackSync(OnDatapackSyncEvent event) {
		// null player = /reload: recipes may have changed, every machine re-offers its patterns
		if (event.getPlayer() == null) {
			MachineBlockEntity.refreshAll();
		}
	}

	/** Items that belong in the creative tab (everything but the hidden pattern definition). */
	public static Iterable<? extends Item> tabItems() {
		return ITEMS_BY_KIND.values().stream().map(DeferredItem::get).toList();
	}
}
