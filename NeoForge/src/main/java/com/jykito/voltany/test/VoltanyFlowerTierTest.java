/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package com.jykito.voltany.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.jykito.voltany.block.entity.ManaGeneratorBlockEntity;

import vazkii.botania.common.block.flower.SlowGeneratingFlowerBlock;
import vazkii.botania.test.TestingUtil;

/**
 * Pins down which flowers the mana generator recognises.
 * <p>
 * The generator keys off item ids written out by hand, so a misspelling costs
 * nothing at runtime and simply drops a flower to the base rate -- which is how
 * {@code entropinnium} went unnoticed while the block is called
 * {@code entropinnyum}. Walking the registry instead of a list means a rename
 * upstream, or a new generating flower, fails here.
 */
public class VoltanyFlowerTierTest {
	@GameTest(template = "botania:addon_arena")
	public void everyGeneratingFlowerHasATier(GameTestHelper helper) {
		List<String> missed = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			// The generating flowers share a block base class; the floating and
			// potted variants do not extend it.
			if (!(block instanceof SlowGeneratingFlowerBlock)) {
				continue;
			}
			String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
			// The floating, petite and potted variants are different ids, and the
			// generator only lists the plain ones.
			if (id.startsWith("floating_") || id.endsWith("_petite") || id.startsWith("potted_")) {
				continue;
			}
			if (ManaGeneratorBlockEntity.flowerTier(new ItemStack(block.asItem())) <= 0) {
				missed.add(id);
			}
		}
		TestingUtil.assertThat(missed.isEmpty(),
				() -> "Generating flowers the mana generator does not recognise: " + missed);
		helper.succeed();
	}

	@GameTest(template = "botania:addon_arena")
	public void tierGrowsWithTheFlower(GameTestHelper helper) {
		Item plain = find("hydroangeas");
		Item better = find("thermalily");
		Item best = find("dandelifeon");
		int a = ManaGeneratorBlockEntity.flowerTier(new ItemStack(plain));
		int b = ManaGeneratorBlockEntity.flowerTier(new ItemStack(better));
		int c = ManaGeneratorBlockEntity.flowerTier(new ItemStack(best));

		TestingUtil.assertThat(a > 0 && b > a && c > b,
				() -> "Expected rising tiers, got " + a + ", " + b + ", " + c);
		helper.succeed();
	}

	@GameTest(template = "botania:addon_arena")
	public void mysticalFlowersAreTierTwo(GameTestHelper helper) {
		Item petal = find("white_mystical_flower");
		TestingUtil.assertEquals(2, ManaGeneratorBlockEntity.flowerTier(new ItemStack(petal)),
				() -> "A mystical flower should be tier two");
		helper.succeed();
	}

	@GameTest(template = "botania:addon_arena")
	public void nonBotaniaItemsHaveNoTier(GameTestHelper helper) {
		TestingUtil.assertEquals(0, ManaGeneratorBlockEntity.flowerTier(new ItemStack(Items.DANDELION)),
				() -> "A vanilla flower is not a generating flower");
		TestingUtil.assertEquals(0, ManaGeneratorBlockEntity.flowerTier(ItemStack.EMPTY),
				() -> "An empty stack is not a flower");
		helper.succeed();
	}

	private static Item find(String path) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("botania", path);
		Item item = BuiltInRegistries.ITEM.get(id);
		if (item == Items.AIR) {
			throw new IllegalStateException("No such item: " + id);
		}
		return item;
	}
}