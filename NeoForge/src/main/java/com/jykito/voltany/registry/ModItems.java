package com.jykito.voltany.registry;

import com.jykito.voltany.VoltanyInit;
import com.jykito.voltany.item.GeneratorUpgradeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VoltanyInit.MODID);

	public static final DeferredItem<BlockItem> MANA_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.MANA_GENERATOR);

	public static final DeferredItem<Item> OVERCLOCK_1 = upgrade("overclock_1", GeneratorUpgradeItem.Type.OVERCLOCK_1);
	public static final DeferredItem<Item> OVERCLOCK_2 = upgrade("overclock_2", GeneratorUpgradeItem.Type.OVERCLOCK_2);
	public static final DeferredItem<Item> OVERCLOCK_3 = upgrade("overclock_3", GeneratorUpgradeItem.Type.OVERCLOCK_3);
	public static final DeferredItem<Item> DISPERSION_1 = upgrade("dispersion_1", GeneratorUpgradeItem.Type.DISPERSION_1);
	public static final DeferredItem<Item> DISPERSION_2 = upgrade("dispersion_2", GeneratorUpgradeItem.Type.DISPERSION_2);
	public static final DeferredItem<Item> DISPERSION_3 = upgrade("dispersion_3", GeneratorUpgradeItem.Type.DISPERSION_3);
	public static final DeferredItem<Item> EFFICIENCY_CORE = upgrade("efficiency_core", GeneratorUpgradeItem.Type.EFFICIENCY);
	public static final DeferredItem<Item> SPARK_RESONATOR = upgrade("spark_resonator", GeneratorUpgradeItem.Type.SPARK);

	private static DeferredItem<Item> upgrade(String id, GeneratorUpgradeItem.Type type) {
		return ITEMS.register(id, () -> new GeneratorUpgradeItem(type, new Item.Properties().stacksTo(1)));
	}

	private ModItems() {}
}
