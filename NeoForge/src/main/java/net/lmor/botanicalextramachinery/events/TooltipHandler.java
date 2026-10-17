package net.lmor.botanicalextramachinery.events;

import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.entities.manaSpark.*;
import net.lmor.botanicalextramachinery.util.NumberFormatter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.lmor.botanicalextramachinery.ModBlocks.*;
import static net.lmor.botanicalextramachinery.ModItems.*;

/**
 * Registered manually from {@code ExtraMachinery} — the original used the Forge
 * {@code @Mod.EventBusSubscriber} annotation, which has no NeoForge equivalent
 * for a mod that is merged into another mod's container.
 */
public class TooltipHandler {

	public static void register() {
		NeoForge.EVENT_BUS.addListener(TooltipHandler::onTooltip);
	}

	public static final List<Item> tooltipManaAllBlocks = new ArrayList<>();
	public static final List<Item> tooltipEnergyAllBlocks = new ArrayList<>();
	public static final List<Item> tooltipWaterAllBlocks = new ArrayList<>();
	public static final List<Item> tooltipSparks = new ArrayList<>();
	public static final Map<Item, String[]> catalystList = new HashMap<>();
	public static final Map<Item, String[]> upgradeGreenhouse = new HashMap<>();

	private static void addItem() {
		tooltipManaAllBlocks.add(baseManaPool.asItem());
		tooltipManaAllBlocks.add(upgradedManaPool.asItem());
		tooltipManaAllBlocks.add(advancedManaPool.asItem());
		tooltipManaAllBlocks.add(ultimateManaPool.asItem());

		tooltipManaAllBlocks.add(baseRunicAltar.asItem());
		tooltipManaAllBlocks.add(upgradedRunicAltar.asItem());
		tooltipManaAllBlocks.add(advancedRunicAltar.asItem());
		tooltipManaAllBlocks.add(ultimateRunicAltar.asItem());

		tooltipManaAllBlocks.add(baseIndustrialAgglomerationFactory.asItem());
		tooltipManaAllBlocks.add(upgradedIndustrialAgglomerationFactory.asItem());
		tooltipManaAllBlocks.add(advancedIndustrialAgglomerationFactory.asItem());
		tooltipManaAllBlocks.add(ultimateIndustrialAgglomerationFactory.asItem());

		tooltipManaAllBlocks.add(baseAlfheimMarket.asItem());
		tooltipManaAllBlocks.add(upgradedAlfheimMarket.asItem());
		tooltipManaAllBlocks.add(advancedAlfheimMarket.asItem());
		tooltipManaAllBlocks.add(ultimateAlfheimMarket.asItem());

		tooltipManaAllBlocks.add(baseOrechid.asItem());
		tooltipManaAllBlocks.add(upgradedOrechid.asItem());
		tooltipManaAllBlocks.add(advancedOrechid.asItem());
		tooltipManaAllBlocks.add(ultimateOrechid.asItem());

		tooltipManaAllBlocks.add(jadedAmaranthus.asItem());
		tooltipManaAllBlocks.add(ModBlocks.greenhouse.asItem());

		tooltipEnergyAllBlocks.add(ModBlocks.greenhouse.asItem());

		tooltipWaterAllBlocks.add(baseApothecary.asItem());
		tooltipWaterAllBlocks.add(upgradedApothecary.asItem());
		tooltipWaterAllBlocks.add(advancedApothecary.asItem());
		tooltipWaterAllBlocks.add(ultimateApothecary.asItem());

		tooltipSparks.add(baseSpark);
		tooltipSparks.add(malachiteSpark);
		tooltipSparks.add(saffronSpark);
		tooltipSparks.add(shadowSpark);
		tooltipSparks.add(crimsonSpark);
		tooltipSparks.add(aureateSpark);
		tooltipSparks.add(mazarineSpark);

		catalystList.put(catalystManaInfinity, new String[] { "botanicalextramachinery.tooltip.item.mana_infinity" });
		catalystList.put(catalystLivingRockInfinity, new String[] { "botanicalextramachinery.tooltip.item.livingrock" });
		catalystList.put(catalystWaterInfinity, new String[] { "botanicalextramachinery.tooltip.item.water" });
		catalystList.put(catalystSeedInfinity, new String[] { "botanicalextramachinery.tooltip.item.seed" });
		catalystList.put(catalystStoneInfinity, new String[] { "botanicalextramachinery.tooltip.item.stone" });
		catalystList.put(catalystWoodInfinity, new String[] { "botanicalextramachinery.tooltip.item.wood" });
		catalystList.put(catalystSpeed, new String[] { "botanicalextramachinery.tooltip.item.upgrade_speed" });
		catalystList.put(catalystPetal, new String[] { "botanicalextramachinery.tooltip.item.catalyst_petal_1", "botanicalextramachinery.tooltip.item.catalyst_petal_3" });
		catalystList.put(catalystPetalBlock, new String[] { "botanicalextramachinery.tooltip.item.catalyst_petal_2", "botanicalextramachinery.tooltip.item.catalyst_petal_3" });

		upgradeGreenhouse.put(upgradeCostEnergy, new String[] { "botanicalextramachinery.tooltip.item.upgradeCostEnergy" });
		upgradeGreenhouse.put(upgradeFlower_4x, new String[] { "botanicalextramachinery.tooltip.item.upgradeFlower_4x" });
		upgradeGreenhouse.put(upgradeFlower_16x, new String[] { "botanicalextramachinery.tooltip.item.upgradeFlower_16x" });
		upgradeGreenhouse.put(upgradeFlower_32x, new String[] { "botanicalextramachinery.tooltip.item.upgradeFlower_32x" });
		upgradeGreenhouse.put(upgradeFlower_64x, new String[] { "botanicalextramachinery.tooltip.item.upgradeFlower_64x" });
		upgradeGreenhouse.put(upgradeGenMana, new String[] { "botanicalextramachinery.tooltip.item.upgradeGenMana" });
		upgradeGreenhouse.put(upgradeSlotAdd, new String[] { "botanicalextramachinery.tooltip.item.upgradeSlotAdd" });
		upgradeGreenhouse.put(upgradeHeatGreenhouse, new String[] { "botanicalextramachinery.tooltip.item.upgradeHeatGreenhouse" });
		upgradeGreenhouse.put(upgradeStorageMana_1, new String[] { "botanicalextramachinery.tooltip.item.upgradeStorageMana_1" });
		upgradeGreenhouse.put(upgradeStorageMana_2, new String[] { "botanicalextramachinery.tooltip.item.upgradeStorageMana_2" });
		upgradeGreenhouse.put(upgradeStorageMana_3, new String[] { "botanicalextramachinery.tooltip.item.upgradeStorageMana_3" });
		upgradeGreenhouse.put(upgradeStorageEnergy_1, new String[] { "botanicalextramachinery.tooltip.item.upgradeStorageEnergy_1" });
		upgradeGreenhouse.put(upgradeStorageEnergy_2, new String[] { "botanicalextramachinery.tooltip.item.upgradeStorageEnergy_2" });
		upgradeGreenhouse.put(upgradeStorageEnergy_3, new String[] { "botanicalextramachinery.tooltip.item.upgradeStorageEnergy_3" });
		upgradeGreenhouse.put(upgradeTickGenMana_1, new String[] { "botanicalextramachinery.tooltip.item.upgradeTickGenMana_1" });
		upgradeGreenhouse.put(upgradeTickGenMana_2, new String[] { "botanicalextramachinery.tooltip.item.upgradeTickGenMana_2" });

	}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		if (tooltipManaAllBlocks.size() == 0) {
			addItem();
		}

		ItemStack stack = event.getItemStack();
		if (!stack.isEmpty()) {
			// 1.21.1: block entity data lives in the BLOCK_ENTITY_DATA data component,
			// the old "BlockEntityTag" NBT compound is gone.
			CustomData blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
			CompoundTag tag = blockEntityData == null ? null : blockEntityData.copyTag();

			if (tag != null) {
				if (tooltipManaAllBlocks.contains(stack.getItem())) {
					if (tag.contains("mana")) {
						int mana = tag.getInt("mana");

						event.getToolTip().add(Component.translatable("botanicalextramachinery.tooltip.block.mana", NumberFormatter.formatInteger(mana)));
					}
				}

				if (tooltipWaterAllBlocks.contains(stack.getItem())) {
					if (tag.contains("fluid")) {
						int water = tag.getCompound("fluid").getInt("Amount");

						event.getToolTip().add(Component.translatable("botanicalextramachinery.tooltip.block.water", NumberFormatter.formatIntegerWater(water)));
					}
				}

				if (tooltipEnergyAllBlocks.contains(stack.getItem())) {
					if (tag.contains("energyStorage")) {
						int energy = tag.getInt("energyStorage");

						event.getToolTip().add(Component.translatable("botanicalextramachinery.tooltip.block.energy", NumberFormatter.formatInteger(energy)));
					}
				}
			}

			if (tooltipSparks.contains(stack.getItem().asItem())) {
				int transfer = 0;
				if (stack.getItem() == baseSpark) {
					transfer = EntityBaseManaSpark.getRate();
				} else if (stack.getItem() == malachiteSpark) {
					transfer = EntityMalachiteManaSpark.getRate();
				} else if (stack.getItem() == saffronSpark) {
					transfer = EntitySaffronManaSpark.getRate();
				} else if (stack.getItem() == shadowSpark) {
					transfer = EntityShadowManaSpark.getRate();
				} else if (stack.getItem() == crimsonSpark) {
					transfer = EntityCrimsonManaSpark.getRate();
				} else if (stack.getItem() == aureateSpark) {
					transfer = EntityAureateManaSpark.getRate();
				} else if (stack.getItem() == mazarineSpark) {
					transfer = EntityMazarineManaSpark.getRate();
				}

				if (transfer != 0) {
					event.getToolTip().add(Component.translatable("botanicalextramachinery.tooltip.item.sparkTransfer", NumberFormatter.formatInteger(transfer)));
				}
			}

			if (catalystList.containsKey(stack.getItem())) {
				for (String tooltip_add : catalystList.get(stack.getItem())) {
					event.getToolTip().add(Component.translatable(tooltip_add));
				}
			}

			if (upgradeGreenhouse.containsKey(stack.getItem())) {
				for (String tooltip_add : upgradeGreenhouse.get(stack.getItem())) {
					event.getToolTip().add(Component.translatable(tooltip_add));
				}
			}

		}
	}
}
