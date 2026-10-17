/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityRunicAltarPattern;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolBase;

import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.test.TestingUtil;

/**
 * Covers Botanical Extra Machinery's runic altar: it finds the recipe its
 * inputs spell out, pays for it in mana, and hands back the result plus the
 * runes it borrowed.
 * <p>
 * The recipe used is the cheapest one the addon ships, the malachite spark at
 * 50000 mana, and it is spelled out here rather than read from data so that a
 * recipe edit shows up as a test failure instead of a silently skipped craft.
 */
public class BemRunicAltarTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos ALTAR = new BlockPos(2, 2, 2);

	/** The base altar's own slot layout. */
	private static final int LIVINGROCK_SLOT = 0;
	private static final int FIRST_INPUT_SLOT = 1;
	private static final int FIRST_OUTPUT_SLOT = 17;

	/** Enough ticks to spend a whole recipe at the altar's 100 mana a tick. */
	private static final int FULL_CRAFT_TICKS = 520;

	/** The altar hands these back one for one, so they are its own recipe keys. */
	private static final String[] RUNES = {
			"botania:rune_of_water",
			"botania:rune_of_fire",
			"botania:rune_of_earth",
			"botania:rune_of_air",
			"botania:rune_of_mana",
	};

	/** The ingredients of botanicalextramachinery:recipe/runic_altar/malachite_spark. */
	private static final String[] SPARK_RECIPE_INPUTS = {
			"botania:rune_of_water",
			"botania:rune_of_fire",
			"botania:rune_of_earth",
			"botania:rune_of_air",
			"botania:rune_of_mana",
			"botanicalextramachinery:base_spark",
			"botanicalextramachinery:malachite_ingot_block",
			"botanicalextramachinery:malachite_ingot",
			"botanicalextramachinery:malachite_dragonstone_block",
	};
	private static final int SPARK_RECIPE_MANA = 50000;

	private static BlockEntityRunicAltarPattern placeAltar(GameTestHelper helper) {
		return placeAltar(helper, "base_runic_altar", ALTAR);
	}

	private static BlockEntityRunicAltarPattern placeAltar(GameTestHelper helper, String id, BlockPos pos) {
		Block block = block("botanicalextramachinery:" + id);
		helper.setBlock(pos, block.defaultBlockState());
		helper.assertBlockPresent(block, pos);
		return (BlockEntityRunicAltarPattern) helper.getBlockEntity(pos);
	}

	/** Fills the altar's input slots and its livingrock slot. */
	private static void loadRecipe(BlockEntityRunicAltarPattern altar) {
		for (int i = 0; i < SPARK_RECIPE_INPUTS.length; i++) {
			altar.getInventory().setStackInSlot(FIRST_INPUT_SLOT + i,
					new ItemStack(item(SPARK_RECIPE_INPUTS[i])));
		}
		altar.getInventory().setStackInSlot(LIVINGROCK_SLOT, new ItemStack(item("botania:livingrock"), 64));
	}

	private static int countIn(BlockEntityRunicAltarPattern altar, String id) {
		Item wanted = item(id);
		int total = 0;
		for (int slot = FIRST_OUTPUT_SLOT; slot < altar.getInventory().getSlots(); slot++) {
			if (altar.getInventory().getStackInSlot(slot).is(wanted)) {
				total += altar.getInventory().getStackInSlot(slot).getCount();
			}
		}
		return total;
	}

	@GameTest(template = ARENA, timeoutTicks = 700)
	public void craftsTheRunicAltarRecipe(GameTestHelper helper) {
		var altar = placeAltar(helper);
		loadRecipe(altar);
		altar.receiveMana(SPARK_RECIPE_MANA);
		int rock = altar.getInventory().getStackInSlot(LIVINGROCK_SLOT).getCount();

		helper.startSequence()
				.thenIdle(FULL_CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(1, countIn(altar, "botanicalextramachinery:malachite_spark"),
							() -> "The altar did not make the malachite spark");
					for (String rune : RUNES) {
						TestingUtil.assertEquals(1, countIn(altar, rune),
								() -> "The altar should give back the rune " + rune);
					}
					TestingUtil.assertThat(
							altar.getInventory().getStackInSlot(LIVINGROCK_SLOT).getCount() < rock,
							() -> "The altar should have spent livingrock on the craft");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void spendsManaOnAPartialCraft(GameTestHelper helper) {
		var altar = placeAltar(helper);
		loadRecipe(altar);
		int charged = 5000;
		altar.receiveMana(charged);

		helper.startSequence()
				.thenIdle(60)
				.thenExecute(() -> {
					TestingUtil.assertThat(altar.getProgress() > 0,
							() -> "The altar should have made progress with mana to spend");
					TestingUtil.assertThat(altar.getCurrentMana() < charged,
							() -> "The altar should have spent mana while crafting");
					TestingUtil.assertEquals(0, countIn(altar, "botanicalextramachinery:malachite_spark"),
							() -> "A partial craft should not produce anything");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void makesNoProgressWithoutMana(GameTestHelper helper) {
		var altar = placeAltar(helper);
		loadRecipe(altar);

		helper.startSequence()
				.thenIdle(60)
				.thenExecute(() -> TestingUtil.assertEquals(0, altar.getProgress(),
						() -> "An altar with no mana should not craft"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void makesNoProgressWithoutLivingRock(GameTestHelper helper) {
		var altar = placeAltar(helper);
		for (int i = 0; i < SPARK_RECIPE_INPUTS.length; i++) {
			altar.getInventory().setStackInSlot(FIRST_INPUT_SLOT + i,
					new ItemStack(item(SPARK_RECIPE_INPUTS[i])));
		}
		altar.receiveMana(SPARK_RECIPE_MANA);

		helper.startSequence()
				.thenIdle(60)
				.thenExecute(() -> TestingUtil.assertEquals(0, altar.getProgress(),
						() -> "The reagent is part of the recipe, so no livingrock should mean no craft"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void makesNoProgressOnTheWrongInputs(GameTestHelper helper) {
		var altar = placeAltar(helper);
		loadRecipe(altar);
		// Swapping one rune for a different one breaks the recipe.
		altar.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item("botania:rune_of_pride")));
		altar.receiveMana(SPARK_RECIPE_MANA);

		helper.startSequence()
				.thenIdle(60)
				.thenExecute(() -> TestingUtil.assertEquals(0, altar.getProgress(),
						() -> "Inputs that do not spell a recipe should not craft"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void keepsItsManaBetweenCrafts(GameTestHelper helper) {
		var altar = placeAltar(helper);
		loadRecipe(altar);
		int charged = SPARK_RECIPE_MANA * 3;
		altar.receiveMana(charged);

		helper.startSequence()
				.thenIdle(60)
				.thenExecute(() -> TestingUtil.assertThat(altar.getCurrentMana() > SPARK_RECIPE_MANA,
						() -> "A single craft should cost the recipe's mana and no more"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void everyTierHoldsMoreMana(GameTestHelper helper) {
		String[] tiers = {"base_runic_altar", "upgraded_runic_altar", "advanced_runic_altar", "ultimate_runic_altar"};
		int previous = 0;
		for (int i = 0; i < tiers.length; i++) {
			String tier = tiers[i];
			var altar = placeAltar(helper, tier, ALTAR.east(i * 2));
			int below = previous;
			int held = altar.getMaxMana();
			TestingUtil.assertThat(held > below,
					() -> tier + " should hold more mana than the tier below it: " + held + " after " + below);
			previous = held;
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void everyTierCraftsMoreAtOnce(GameTestHelper helper) {
		String[] tiers = {"base_runic_altar", "upgraded_runic_altar", "advanced_runic_altar", "ultimate_runic_altar"};
		int previous = 0;
		for (int i = 0; i < tiers.length; i++) {
			String tier = tiers[i];
			var altar = placeAltar(helper, tier, ALTAR.east(i * 2));
			int below = previous;
			int batch = altar.getCountCraft();
			TestingUtil.assertThat(batch > below,
					() -> tier + " should craft more at a time than the tier below it: " + batch + " after " + below);
			previous = batch;
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void manaPoolTiersHoldMoreThanVanillas(GameTestHelper helper) {
		// The addon's pools are meant to be the better version of Botania's own.
		ManaPool vanillas = (ManaPool) helper.getBlockEntity(place(helper, "botania:mana_pool", ALTAR));
		var addon = (BlockEntityManaPoolBase) helper.getBlockEntity(
				place(helper, "botanicalextramachinery:base_mana_pool", ALTAR.east(2)));

		TestingUtil.assertThat(addon.getMaxMana() > vanillas.getMaxMana(),
				() -> "The addon's base pool holds " + addon.getMaxMana()
						+ " against Botania's " + vanillas.getMaxMana());
		helper.succeed();
	}

	private static BlockPos place(GameTestHelper helper, String id, BlockPos pos) {
		var block = block(id);
		helper.setBlock(pos, block.defaultBlockState());
		helper.assertBlockPresent(block, pos);
		return pos;
	}

	private static Block block(String id) {
		ResourceLocation key = ResourceLocation.parse(id);
		Block block = BuiltInRegistries.BLOCK.get(key);
		if (block == Blocks.AIR) {
			throw new IllegalStateException("No such block: " + id);
		}
		return block;
	}

	private static Item item(String id) {
		ResourceLocation key = ResourceLocation.parse(id);
		Item item = BuiltInRegistries.ITEM.get(key);
		if (item == net.minecraft.world.item.Items.AIR) {
			throw new IllegalStateException("No such item: " + id);
		}
		return item;
	}
}