package vazkii.botania.ae2;

import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import vazkii.botania.common.item.BotaniaItems;

import vazkii.botania.ae2.ae2.ManaP2PTunnelPart;
import vazkii.botania.ae2.item.ManaCellItem;
import vazkii.botania.ae2.item.PortableManaCellItem;
import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.parts.PartModels;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;

import java.util.Locale;

public class ABItems {
	private static final Items ITEMS = DeferredRegister.createItems("botania");
	private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), "botania");
	public static final DeferredItem<Item> FLUIX_MANA_POOL = ITEMS.register(
			"fluix_mana_pool", () -> new BlockItem(ABBlocks.FLUIX_MANA_POOL.get(), BotaniaItems.defaultBuilder())
	);
	public static final DeferredItem<Item> MANA_CELL_HOUSING = ITEMS.register("mana_cell_housing", () -> new Item(new Properties().rarity(Rarity.RARE)));
	public static final DeferredItem<Item> MANA_CELL_1K = ITEMS.register(
			"mana_cell_1k", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), AEItems.CELL_COMPONENT_1K, 1, 0.5)
	);
	public static final DeferredItem<Item> MANA_CELL_4K = ITEMS.register(
			"mana_cell_4k", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), AEItems.CELL_COMPONENT_4K, 4, 1.0)
	);
	public static final DeferredItem<Item> MANA_CELL_16K = ITEMS.register(
			"mana_cell_16k", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), AEItems.CELL_COMPONENT_16K, 16, 1.5)
	);
	public static final DeferredItem<Item> MANA_CELL_64K = ITEMS.register(
			"mana_cell_64k", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), AEItems.CELL_COMPONENT_64K, 64, 2.0)
	);
	public static final DeferredItem<Item> MANA_CELL_256K = ITEMS.register(
			"mana_cell_256k", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), AEItems.CELL_COMPONENT_256K, 256, 2.5)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_1K = ITEMS.register(
			"portable_mana_cell_1k", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 1, 0.5)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_4K = ITEMS.register(
			"portable_mana_cell_4k", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 4, 1.0)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_16K = ITEMS.register(
			"portable_mana_cell_16k", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 16, 1.5)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_64K = ITEMS.register(
			"portable_mana_cell_64k", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 64, 2.0)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_256K = ITEMS.register(
			"portable_mana_cell_256k", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 256, 2.5)
	);
	// MEGA Cells tiers: crafted from MEGA components (recipes only load with MEGA Cells); MEGA's own mana cells
	// only appear next to the standalone Applied Botanics, which is merged into this mod
	public static final DeferredItem<Item> MANA_CELL_1M = ITEMS.register(
			"mana_cell_1m", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), megaComponent("1m"), 1000, 3.0)
	);
	public static final DeferredItem<Item> MANA_CELL_4M = ITEMS.register(
			"mana_cell_4m", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), megaComponent("4m"), 4000, 3.5)
	);
	public static final DeferredItem<Item> MANA_CELL_16M = ITEMS.register(
			"mana_cell_16m", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), megaComponent("16m"), 16000, 4.0)
	);
	public static final DeferredItem<Item> MANA_CELL_64M = ITEMS.register(
			"mana_cell_64m", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), megaComponent("64m"), 64000, 4.5)
	);
	public static final DeferredItem<Item> MANA_CELL_256M = ITEMS.register(
			"mana_cell_256m", () -> new ManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), megaComponent("256m"), 256000, 5.0)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_1M = ITEMS.register(
			"portable_mana_cell_1m", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 1000, 3.0)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_4M = ITEMS.register(
			"portable_mana_cell_4m", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 4000, 3.5)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_16M = ITEMS.register(
			"portable_mana_cell_16m", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 16000, 4.0)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_64M = ITEMS.register(
			"portable_mana_cell_64m", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 64000, 4.5)
	);
	public static final DeferredItem<Item> PORTABLE_MANA_CELL_256M = ITEMS.register(
			"portable_mana_cell_256m", () -> new PortableManaCellItem(new Properties().stacksTo(1).rarity(Rarity.RARE).component(AppliedBotanicsForge.AE_MANA, 0L), 256000, 5.0)
	);
	public static final DeferredItem<PartItem<ManaP2PTunnelPart>> MANA_P2P_TUNNEL = Util.make(() -> {
		PartModels.registerModels(PartModelsHelper.createModels(ManaP2PTunnelPart.class));
		return ITEMS.register("mana_p2p_tunnel", () -> new PartItem<>(new Properties(), ManaP2PTunnelPart.class, ManaP2PTunnelPart::new));
	});
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register(
			"tab",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.botania.tab"))
					.icon(() -> new ItemStack(FLUIX_MANA_POOL.get()))
					.displayItems((context, entries) -> {
						ABMachines.tabItems().forEach(entries::accept);
						for (DeferredHolder<Item, ? extends Item> holder : ITEMS.getEntries()) {
							Item item = holder.get();
							entries.accept(item);
							if (item instanceof IAEItemPowerStorage storage) {
								ItemStack stack = new ItemStack(item);
								storage.injectAEPower(stack, storage.getAEMaxPower(stack), Actionable.MODULATE);
								entries.accept(stack);
							}
						}
					})
					.build()
	);

	public static void initialize(IEventBus bus) {
		ITEMS.register(bus);
		CREATIVE_MODE_TABS.register(bus);
	}

	public static DeferredItem<Item> get(ABItems.Tier tier) {
		return switch (tier) {
			case _1K -> MANA_CELL_1K;
			case _4K -> MANA_CELL_4K;
			case _16K -> MANA_CELL_16K;
			case _64K -> MANA_CELL_64K;
			case _256K -> MANA_CELL_256K;
			case _1M -> MANA_CELL_1M;
			case _4M -> MANA_CELL_4M;
			case _16M -> MANA_CELL_16M;
			case _64M -> MANA_CELL_64M;
			case _256M -> MANA_CELL_256M;
		};
	}

	public static DeferredItem<Item> getPortableCell(ABItems.Tier tier) {
		return switch (tier) {
			case _1K -> PORTABLE_MANA_CELL_1K;
			case _4K -> PORTABLE_MANA_CELL_4K;
			case _16K -> PORTABLE_MANA_CELL_16K;
			case _64K -> PORTABLE_MANA_CELL_64K;
			case _256K -> PORTABLE_MANA_CELL_256K;
			case _1M -> PORTABLE_MANA_CELL_1M;
			case _4M -> PORTABLE_MANA_CELL_4M;
			case _16M -> PORTABLE_MANA_CELL_16M;
			case _64M -> PORTABLE_MANA_CELL_64M;
			case _256M -> PORTABLE_MANA_CELL_256M;
		};
	}

	public enum Tier {
		_1K,
		_4K,
		_16K,
		_64K,
		_256K,
		_1M,
		_4M,
		_16M,
		_64M,
		_256M;

		/** Size as written in ids and drive cell models: "1k", "256m". */
		public String size() {
			return name().substring(1).toLowerCase(Locale.ROOT);
		}
	}

	/** A MEGA Cells component looked up by id, so this mod needs no MEGA Cells at compile time. */
	private static ItemLike megaComponent(String size) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("megacells", "cell_component_" + size);
		return () -> BuiltInRegistries.ITEM.get(id);
	}
}
