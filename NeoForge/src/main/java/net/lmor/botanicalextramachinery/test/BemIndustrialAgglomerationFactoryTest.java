/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityIndustrialAgglomerationFactoryPattern;
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
 * Covers Botanical Extra Machinery's industrial agglomeration factory, the terra
 * plate machine: it eats a plate's worth of ingredients and pays the recipe's
 * mana out over time.
 * <p>
 * Two recipes are named here rather than looked up, so an edit to either shows
 * up as a failure:
 * <ul>
 * <li>{@code botanicalextramachinery:recipe/terra_plate/malachite_ingot} costs
 * 750000 mana and takes three ingredients, which is what the base tier's three
 * input slots can hold.</li>
 * <li>{@code botanicalextramachinery:recipe/terra_plate/aureate_ingot} costs
 * 2500000 mana and takes six ingredients. The base tier physically cannot hold
 * it -- {@code org.moddingx.libx.crafting.RecipeHelper#matches} wants every
 * ingredient matched to a distinct item in a slot -- so it is crafted on the
 * advanced tier, whose seven input slots fit, and
 * {@link #theBaseTierCannotHoldTheSixIngredientRecipe(GameTestHelper)} pins the
 * base tier's side of that.</li>
 * </ul>
 * <p>
 * This machine is worth a test of its own because it is the only one whose
 * progress <em>is</em> its mana bill. {@code WorkingTile} adds at most
 * {@code getMaxManaPerTick()} to progress and charges the same amount times
 * {@code countCraft} to the buffer, and the factory's progress requirement is
 * {@code TerrestrialAgglomerationRecipe.getMana()}. So the wait is the recipe's
 * mana divided by {@code IndustrialAgglomerationFactorySettings.workingDuration}
 * (5000) times the tier's {@code craftTime} (1): 150 ticks for malachite and
 * 500 for aureate. Every wait below is sized off that, with slack for the
 * recipe resync the machine does once it finishes.
 */
public class BemIndustrialAgglomerationFactoryTest {
	private static final String ARENA = "botania:addon_arena";
	/**
	 * Own batch: the no-mana test fails when an arena of another test that hands out mana lands next to it, which
	 * depends on how many tests run before (fewer without AE2).
	 */
	private static final String BATCH = "bem_agglomeration_factory";
	private static final BlockPos FACTORY = new BlockPos(2, 2, 2);

	/** The base tier's own slot layout. */
	private static final int FIRST_INPUT_SLOT = 0;
	private static final int LAST_INPUT_SLOT = 2;
	private static final int FIRST_OUTPUT_SLOT = 3;
	private static final int LAST_OUTPUT_SLOT = 6;

	/** The advanced tier spends slots 0 and 1 on catalysts, so its inputs start at 2. */
	private static final int ADVANCED_FIRST_INPUT_SLOT = 2;
	private static final int ADVANCED_FIRST_OUTPUT_SLOT = 9;
	private static final int ADVANCED_LAST_OUTPUT_SLOT = 14;

	/** botanicalextramachinery:recipe/terra_plate/malachite_ingot. */
	private static final String TERRASTEEL_INGOT = "botania:terrasteel_ingot";
	private static final String GAIA_SPIRIT = "botania:gaia_spirit";
	private static final String ELEMENTIUM_INGOT = "botania:elementium_ingot";
	private static final String MALACHITE_INGOT = "botanicalextramachinery:malachite_ingot";
	private static final int MALACHITE_RECIPE_MANA = 750000;

	/** botanicalextramachinery:recipe/terra_plate/aureate_ingot. */
	private static final String CRIMSON_INGOT = "botanicalextramachinery:crimson_ingot";
	private static final String GAIA_INGOT = "botania:gaia_ingot";
	private static final String CRIMSON_INGOT_BLOCK = "botanicalextramachinery:crimson_ingot_block";
	private static final String AUREATE_INGOT = "botanicalextramachinery:aureate_ingot";
	private static final int AUREATE_RECIPE_MANA = 2500000;

	/** 750000 of mana at 5000 a tick is 150 ticks; 250 leaves room for the resync. */
	private static final int MALACHITE_TICKS = 250;

	/** 2500000 at 5000 a tick is 500 ticks; 700 leaves room for the resync. */
	private static final int AUREATE_TICKS = 700;

	/** Long enough to see a partial craft, short enough not to finish one. */
	private static final int PARTIAL_TICKS = 60;

	private static BlockEntityIndustrialAgglomerationFactoryPattern place(GameTestHelper helper, String tier) {
		Block block = block("botanicalextramachinery:" + tier);
		helper.setBlock(FACTORY, block.defaultBlockState());
		helper.assertBlockPresent(block, FACTORY);
		BlockEntityIndustrialAgglomerationFactoryPattern factory =
				(BlockEntityIndustrialAgglomerationFactoryPattern) helper.getBlockEntity(FACTORY);
		// The factory pays out of its own buffer, so a fresh one would just sit idle.
		factory.setCurrentMana(factory.getMaxMana());
		return factory;
	}

	private static BlockEntityIndustrialAgglomerationFactoryPattern placeBase(GameTestHelper helper) {
		return place(helper, "base_industrial_agglomeration_factory");
	}

	private static BlockEntityIndustrialAgglomerationFactoryPattern placeAdvanced(GameTestHelper helper) {
		return place(helper, "advanced_industrial_agglomeration_factory");
	}

	/** Fills consecutive input slots with one of each named ingredient. */
	private static void fillInputs(BlockEntityIndustrialAgglomerationFactoryPattern factory, int firstSlot, String... ids) {
		for (int i = 0; i < ids.length; i++) {
			factory.getInventory().setStackInSlot(firstSlot + i, new ItemStack(item(ids[i]), 1));
		}
	}

	/** Everything the machine made, across a range of output slots. */
	private static int countIn(BlockEntityIndustrialAgglomerationFactoryPattern factory, int firstOutputSlot,
			int lastOutputSlot, String id) {
		Item wanted = item(id);
		int total = 0;
		for (int slot = firstOutputSlot; slot <= lastOutputSlot; slot++) {
			ItemStack stack = factory.getInventory().getStackInSlot(slot);
			if (stack.is(wanted)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static String outputs(BlockEntityIndustrialAgglomerationFactoryPattern factory, int firstOutputSlot,
			int lastOutputSlot) {
		StringBuilder sb = new StringBuilder();
		for (int slot = firstOutputSlot; slot <= lastOutputSlot; slot++) {
			ItemStack stack = factory.getInventory().getStackInSlot(slot);
			if (!stack.isEmpty()) {
				sb.append(stack).append(" in slot ").append(slot).append("; ");
			}
		}
		return sb.isEmpty() ? "nothing" : sb.toString();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
	public void craftsTheTerraPlateRecipe(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeBase(helper);
		fillInputs(factory, FIRST_INPUT_SLOT, TERRASTEEL_INGOT, GAIA_SPIRIT, ELEMENTIUM_INGOT);

		helper.startSequence()
				.thenIdle(MALACHITE_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(item(MALACHITE_INGOT),
						factory.getInventory().getStackInSlot(FIRST_OUTPUT_SLOT).getItem(),
						() -> "The factory should have made " + MALACHITE_INGOT + ", but its output slots hold "
								+ outputs(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT)))
				.thenSucceed();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
	public void consumesTheInputsAndPaysInMana(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeBase(helper);
		fillInputs(factory, FIRST_INPUT_SLOT, TERRASTEEL_INGOT, GAIA_SPIRIT, ELEMENTIUM_INGOT);
		int manaBefore = factory.getCurrentMana();

		helper.startSequence()
				.thenIdle(MALACHITE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(manaBefore - MALACHITE_RECIPE_MANA, factory.getCurrentMana(),
							() -> "A plate costing " + MALACHITE_RECIPE_MANA + " should leave "
									+ (manaBefore - MALACHITE_RECIPE_MANA) + " mana, but the factory holds "
									+ factory.getCurrentMana());
					for (int slot = FIRST_INPUT_SLOT; slot <= LAST_INPUT_SLOT; slot++) {
						int used = slot;
						TestingUtil.assertEquals(ItemStack.EMPTY, factory.getInventory().getStackInSlot(used),
								() -> "Slot " + used + " held an ingredient that was agglomerated, but it still holds "
										+ factory.getInventory().getStackInSlot(used));
					}
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
	public void refusesTheWrongInputs(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeBase(helper);
		// A stick is an ingredient of no terra plate recipe.
		for (int slot = FIRST_INPUT_SLOT; slot <= LAST_INPUT_SLOT; slot++) {
			factory.getInventory().setStackInSlot(slot, new ItemStack(Items.STICK, 3));
		}
		int manaBefore = factory.getCurrentMana();

		helper.startSequence()
				.thenIdle(MALACHITE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, factory.getProgress(),
							() -> "Inputs that spell no plate should not start a craft");
					TestingUtil.assertEquals(0,
							countIn(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT, MALACHITE_INGOT),
							() -> "A factory full of sticks should not make anything, but its output slots hold "
									+ outputs(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT));
					TestingUtil.assertEquals(manaBefore, factory.getCurrentMana(),
							() -> "A factory with no recipe should not spend mana, but it went from " + manaBefore
									+ " to " + factory.getCurrentMana());
					TestingUtil.assertEquals(3, factory.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "The factory should not have eaten the sticks either");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
	public void doesNothingWithEmptyInputs(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeBase(helper);
		int manaBefore = factory.getCurrentMana();

		helper.startSequence()
				.thenIdle(MALACHITE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, factory.getProgress(),
							() -> "An empty factory should not start a craft");
					TestingUtil.assertEquals(0,
							countIn(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT, MALACHITE_INGOT),
							() -> "An empty factory should not make anything, but its output slots hold "
									+ outputs(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT));
					TestingUtil.assertEquals(manaBefore, factory.getCurrentMana(),
							() -> "An empty factory should not spend mana, but it went from " + manaBefore
									+ " to " + factory.getCurrentMana());
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 300)
	public void makesNoProgressWithoutMana(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeBase(helper);
		fillInputs(factory, FIRST_INPUT_SLOT, TERRASTEEL_INGOT, GAIA_SPIRIT, ELEMENTIUM_INGOT);
		// An empty buffer. The factory's progress is paid for out of it a tick at a time.
		factory.setCurrentMana(0);

		helper.startSequence()
				.thenIdle(PARTIAL_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, factory.getProgress(),
							() -> "Progress is the mana bill, so a factory with no mana cannot make progress");
					TestingUtil.assertEquals(0,
							countIn(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT, MALACHITE_INGOT),
							() -> "A factory with no mana should not make anything");
					TestingUtil.assertEquals(1, factory.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "A factory with no mana should not have eaten the terrasteel either");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 400)
	public void spendsManaOnAPartialCraft(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeBase(helper);
		fillInputs(factory, FIRST_INPUT_SLOT, TERRASTEEL_INGOT, GAIA_SPIRIT, ELEMENTIUM_INGOT);
		int charged = 2500000;

		helper.startSequence()
				.thenIdle(PARTIAL_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertThat(factory.getProgress() > 0,
							() -> "The factory should have made progress on the plate it is paid for");
					TestingUtil.assertThat(factory.getCurrentMana() < charged,
							() -> "The factory should be spending mana while it works");
					TestingUtil.assertEquals(0,
							countIn(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT, MALACHITE_INGOT),
							() -> "An unfinished plate should not turn into an ingot yet");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 900)
	public void craftsTheSixIngredientPlateOnTheAdvancedTier(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeAdvanced(helper);
		// The aureate plate wants six ingredients; the advanced tier has seven input slots.
		fillInputs(factory, ADVANCED_FIRST_INPUT_SLOT,
				CRIMSON_INGOT, GAIA_INGOT, GAIA_INGOT, GAIA_INGOT, GAIA_INGOT, CRIMSON_INGOT_BLOCK);
		int manaBefore = factory.getCurrentMana();

		helper.startSequence()
				.thenIdle(AUREATE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(item(AUREATE_INGOT),
							factory.getInventory().getStackInSlot(ADVANCED_FIRST_OUTPUT_SLOT).getItem(),
							() -> "The advanced factory should have made " + AUREATE_INGOT
									+ ", but its output slots hold "
									+ outputs(factory, ADVANCED_FIRST_OUTPUT_SLOT, ADVANCED_LAST_OUTPUT_SLOT));
					TestingUtil.assertEquals(manaBefore - AUREATE_RECIPE_MANA, factory.getCurrentMana(),
							() -> "A plate costing " + AUREATE_RECIPE_MANA + " should leave "
									+ (manaBefore - AUREATE_RECIPE_MANA) + " mana, but the factory holds "
									+ factory.getCurrentMana());
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, batch = BATCH, timeoutTicks = 900)
	public void theBaseTierCannotHoldTheSixIngredientRecipe(GameTestHelper helper) {
		BlockEntityIndustrialAgglomerationFactoryPattern factory = placeBase(helper);
		// Three of the aureate plate's six ingredients, which is all the base tier has room for.
		fillInputs(factory, FIRST_INPUT_SLOT, CRIMSON_INGOT, GAIA_INGOT, CRIMSON_INGOT_BLOCK);
		int manaBefore = factory.getCurrentMana();

		helper.startSequence()
				.thenIdle(AUREATE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, factory.getProgress(),
							() -> "The aureate plate needs six ingredients and the base tier has three input slots,"
									+ " so it should not have selected a recipe at all");
					TestingUtil.assertEquals(0,
							countIn(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT, AUREATE_INGOT),
							() -> "The base tier should not make " + AUREATE_INGOT + ", but its output slots hold "
									+ outputs(factory, FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT));
					TestingUtil.assertEquals(manaBefore, factory.getCurrentMana(),
							() -> "Not being able to hold the recipe should cost nothing, but the factory went from "
									+ manaBefore + " to " + factory.getCurrentMana());
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
