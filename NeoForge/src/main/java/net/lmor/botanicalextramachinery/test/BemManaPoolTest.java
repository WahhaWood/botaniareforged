/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityManaPoolPattern;
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
 * Covers Botanical Extra Machinery's mana pool, which turns items into other
 * items by spending the mana it is holding.
 * <p>
 * The recipe is Botania's own {@code botania:recipe/mana_infusion/mana_in_a_bottle}:
 * one glass bottle becomes one mana-in-a-bottle for 5000 mana. It is named here
 * rather than looked up so that an edit to the recipe shows up as a failure.
 * <p>
 * This machine is worth a test of its own because mana infusion recipes describe
 * themselves differently from every other recipe the addon handles. Botania's
 * {@code ManaInfusionRecipe} leads its ingredient list with {@code Ingredient.EMPTY},
 * standing in for the slot the infusion is triggered from. The addon's recipe
 * machinery walks that list to decide what a machine can craft, and an empty
 * ingredient matches no stack that is actually in a slot, so the walk fails and
 * the pool selects nothing at all.
 */
public class BemManaPoolTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos POOL = new BlockPos(2, 2, 2);

	/** The base pool's own slot layout. */
	private static final int FIRST_INPUT_SLOT = 1;
	private static final int FIRST_OUTPUT_SLOT = 4;

	/** botania:recipe/mana_infusion/mana_in_a_bottle: glass bottle in, bottle of mana out. */
	private static final String RECIPE_INPUT = "minecraft:glass_bottle";
	private static final String RECIPE_OUTPUT = "botania:mana_in_a_bottle";
	private static final int RECIPE_MANA = 5000;

	/** Enough ticks for one craft at the pool's 10 tick craft time, plus slack. */
	private static final int CRAFT_TICKS = 80;

	private static BlockEntityManaPoolPattern place(GameTestHelper helper) {
		Block block = block("botanicalextramachinery:base_mana_pool");
		helper.setBlock(POOL, block.defaultBlockState());
		helper.assertBlockPresent(block, POOL);
		BlockEntityManaPoolPattern pool = (BlockEntityManaPoolPattern) helper.getBlockEntity(POOL);
		// The pool pays out of its own buffer, so a fresh one would just sit idle.
		pool.setCurrentMana(pool.getMaxMana());
		return pool;
	}

	private static ItemStack slot(BlockEntityManaPoolPattern pool, int slot) {
		return pool.getInventory().getStackInSlot(slot);
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void turnsAnItemIntoAnother(GameTestHelper helper) {
		BlockEntityManaPoolPattern pool = place(helper);
		pool.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(RECIPE_INPUT), 1));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(item(RECIPE_OUTPUT), slot(pool, FIRST_OUTPUT_SLOT).getItem(),
						() -> "The pool should have made " + RECIPE_OUTPUT + ", but the output slot holds " + slot(pool, FIRST_OUTPUT_SLOT)))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void consumesTheInputAndPaysInMana(GameTestHelper helper) {
		BlockEntityManaPoolPattern pool = place(helper);
		pool.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(RECIPE_INPUT), 1));
		int manaBefore = pool.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(manaBefore - RECIPE_MANA, pool.getCurrentMana(),
							() -> "A recipe costing " + RECIPE_MANA + " should leave " + (manaBefore - RECIPE_MANA)
									+ " mana, but the pool holds " + pool.getCurrentMana());
					TestingUtil.assertEquals(ItemStack.EMPTY, slot(pool, FIRST_INPUT_SLOT),
							() -> "The bottle that was paid for should be gone from the input slot, but it holds "
									+ slot(pool, FIRST_INPUT_SLOT));
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 200)
	public void doesNothingWithoutAnythingToInfuse(GameTestHelper helper) {
		BlockEntityManaPoolPattern pool = place(helper);
		int manaBefore = pool.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(manaBefore, pool.getCurrentMana(),
							() -> "An empty pool should not spend mana, but it went from " + manaBefore
									+ " to " + pool.getCurrentMana());
					TestingUtil.assertEquals(ItemStack.EMPTY, slot(pool, FIRST_OUTPUT_SLOT),
							() -> "An empty pool should not produce anything, but the output slot holds "
									+ slot(pool, FIRST_OUTPUT_SLOT));
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void leavesWhatItCannotInfuse(GameTestHelper helper) {
		BlockEntityManaPoolPattern pool = place(helper);
		// A stick is not an input to any infusion recipe.
		pool.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(Items.STICK, 3));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(ItemStack.EMPTY, slot(pool, FIRST_OUTPUT_SLOT),
							() -> "A stick is not something a pool infuses, so the output slot should be empty but holds "
									+ slot(pool, FIRST_OUTPUT_SLOT));
					TestingUtil.assertEquals(3, slot(pool, FIRST_INPUT_SLOT).getCount(),
							() -> "It should not have eaten the sticks either");
				})
				.thenSucceed();
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
