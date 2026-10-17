/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityDaisyPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import vazkii.botania.test.TestingUtil;

/**
 * Covers Botanical Extra Machinery's daisy, which turns one block into another
 * given nothing but time.
 * <p>
 * The recipes are Botania's own pure daisy recipes, so the test names one of
 * them rather than inventing a conversion: netherrack into cobblestone takes
 * 150 ticks whatever the machine, and the daisy's own setting is how many times
 * over that each tier takes.
 */
public class BemDaisyTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos DAISY = new BlockPos(2, 2, 2);

	private static final String RECIPE_INPUT = "minecraft:netherrack";
	private static final String RECIPE_OUTPUT = "minecraft:cobblestone";
	/** What botania:recipe/pure_daisy/cobblestone says, and what the code reads. */
	private static final int RECIPE_TICKS = 150;

	private static BlockEntityDaisyPattern place(GameTestHelper helper, String id) {
		Block block = block("botanicalextramachinery:" + id);
		helper.setBlock(DAISY, block.defaultBlockState());
		helper.assertBlockPresent(block, DAISY);
		return (BlockEntityDaisyPattern) helper.getBlockEntity(DAISY);
	}

	private static BlockEntityDaisyPattern place(GameTestHelper helper) {
		return place(helper, "base_daisy");
	}

	private static void feed(BlockEntityDaisyPattern daisy, String id, int count) {
		daisy.getInventory().setStackInSlot(0, new ItemStack(item(id), count));
	}

	private static ItemStack slot(BlockEntityDaisyPattern daisy) {
		return daisy.getInventory().getStackInSlot(0);
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void turnsOneBlockIntoAnother(GameTestHelper helper) {
		var daisy = place(helper);
		feed(daisy, RECIPE_INPUT, 1);

		helper.startSequence()
				.thenIdle(RECIPE_TICKS + 20)
				.thenExecute(() -> TestingUtil.assertEquals(item(RECIPE_OUTPUT), slot(daisy).getItem(),
						() -> "The daisy should have made " + RECIPE_OUTPUT + ", but the slot holds " + slot(daisy)))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void worksOnASingleItemEachTime(GameTestHelper helper) {
		var daisy = place(helper);
		feed(daisy, RECIPE_INPUT, 1);

		helper.startSequence()
				.thenIdle(RECIPE_TICKS + 20)
				.thenExecute(() -> {
					TestingUtil.assertEquals(item(RECIPE_OUTPUT), slot(daisy).getItem(),
							() -> "The daisy should have made " + RECIPE_OUTPUT);
					TestingUtil.assertEquals(1, slot(daisy).getCount(),
							() -> "One netherrack should make one cobblestone");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void keepsGoingWhileThereIsWork(GameTestHelper helper) {
		var daisy = place(helper);
		// Two crafts' worth of input in separate slots: the daisy walks its
		// slots, so both should come out.
		feed(daisy, RECIPE_INPUT, 1);
		daisy.getInventory().setStackInSlot(1, new ItemStack(item(RECIPE_INPUT), 1));

		helper.startSequence()
				.thenIdle(RECIPE_TICKS + 20)
				.thenExecute(() -> {
					for (int i = 0; i < 2; i++) {
						int slot = i;
						TestingUtil.assertEquals(item(RECIPE_OUTPUT),
								daisy.getInventory().getStackInSlot(slot).getItem(),
								() -> "Slot " + slot + " should have been worked too");
					}
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 200)
	public void leavesWhatItHasNoRecipeFor(GameTestHelper helper) {
		var daisy = place(helper);
		feed(daisy, "minecraft:stick", 3);

		helper.startSequence()
				.thenIdle(RECIPE_TICKS + 20)
				.thenExecute(() -> {
					TestingUtil.assertEquals(item("minecraft:stick"), slot(daisy).getItem(),
							() -> "A stick is not something a daisy makes, so it should still be a stick");
					TestingUtil.assertEquals(3, slot(daisy).getCount(),
							() -> "It should not have eaten the sticks either");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 200)
	public void waitsBeforeFinishing(GameTestHelper helper) {
		var daisy = place(helper);
		feed(daisy, RECIPE_INPUT, 1);

		helper.startSequence()
				.thenIdle(RECIPE_TICKS - 40)
				.thenExecute(() -> TestingUtil.assertEquals(item(RECIPE_INPUT), slot(daisy).getItem(),
						() -> "The daisy should not have finished yet"))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 200)
	public void moreTiersHaveMoreSlots(GameTestHelper helper) {
		String[] tiers = { "base_daisy", "upgraded_daisy", "advanced_daisy", "ultimate_daisy" };
		int previous = 0;
		for (int i = 0; i < tiers.length; i++) {
			String tier = tiers[i];
			var daisy = place(helper, tier);
			int below = previous;
			int slots = daisy.getInventory().getSlots();
			TestingUtil.assertThat(slots > below,
					() -> tier + " should have more slots than the tier below it: " + slots + " after " + below);
			previous = slots;
		}
		helper.succeed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void everyTierFinishesTheSameRecipe(GameTestHelper helper) {
		String[] tiers = { "base_daisy", "upgraded_daisy", "advanced_daisy", "ultimate_daisy" };
		for (int i = 0; i < tiers.length; i++) {
			String tier = tiers[i];
			BlockPos pos = DAISY.east(i * 2);
			Block block = block("botanicalextramachinery:" + tier);
			helper.setBlock(pos, block.defaultBlockState());
			var daisy = (BlockEntityDaisyPattern) helper.getBlockEntity(pos);
			daisy.getInventory().setStackInSlot(0, new ItemStack(item(RECIPE_INPUT), 1));
			helper.runAfterDelay(RECIPE_TICKS + 20L, () -> TestingUtil.assertEquals(
					item(RECIPE_OUTPUT), daisy.getInventory().getStackInSlot(0).getItem(),
					() -> tier + " should have made " + RECIPE_OUTPUT + " too"));
		}
		helper.succeed();
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
		if (item == Items.AIR) {
			throw new IllegalStateException("No such item: " + id);
		}
		return item;
	}
}
