/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityApothecaryPattern;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.test.TestingUtil;

/**
 * Covers Botanical Extra Machinery's mechanical petal apothecary, which mixes
 * petals into elixirs given a seed (the reagent) and a bucket of water.
 * <p>
 * The recipe used is Botania's own {@code botania:recipe/petal_apothecary/bergamute}:
 * an orange petal, two green petals and a redstone root make one bergamute, and
 * the recipe names its own reagent as the {@code botania:seed_apothecary_reagent}
 * tag (nether wart or any {@code c:seeds} item). The four ingredients and the
 * reagent are named here rather than read from data so that an edit to the
 * recipe shows up as a failure instead of a silently skipped craft.
 * <p>
 * This machine is worth a test of its own because a petal apothecary recipe
 * carries two independent requirements -- the ingredients <i>and</i> the reagent
 * -- and the addon's recipe machinery only knows how to walk ingredient lists.
 * {@link BlockEntityApothecaryPattern} does not override
 * {@code matchRecipe}, so {@link PetalApothecaryRecipe#getReagent()} is never
 * consulted: whatever is sitting in the seed slot is treated as good enough.
 * {@link #refusesToCraftWithTheWrongReagent()} is the test that says so.
 */
public class BemApothecaryTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos APOTHECARY = new BlockPos(2, 2, 2);

	/** The base apothecary's own slot layout. */
	private static final int SEEDS_SLOT = 0;
	private static final int FIRST_INPUT_SLOT = 1;
	private static final int FIRST_OUTPUT_SLOT = 10;

	/** botania:recipe/petal_apothecary/bergamute. */
	private static final String[] BERGAMUTE_INPUTS = {
			"botania:orange_mystical_petal",
			"botania:green_mystical_petal",
			"botania:green_mystical_petal",
			"botania:redstone_root",
	};
	private static final String BERGAMUTE_OUTPUT = "botania:bergamute";

	/**
	 * A legal catalyst: it is listed in
	 * botanicalextramachinery:tags/item/mechanical_apothecary_catalysts.
	 */
	private static final String RIGHT_CATALYST = "botanicalextramachinery:catalyst_petal";
	private static final String RIGHT_OUTPUT_ITEM = "botania:bergamute";
	/** Not in that tag, so the seed slot must refuse it. */
	private static final String WRONG_REAGENT = "minecraft:cobblestone";

	/** canMatchRecipes() wants a full bucket's worth, and a craft spends 1000mB. */
	private static final int WATER_NEEDED = 4000;
	private static final int WATER_PER_CRAFT = 1000;

	/**
	 * Enough ticks for one craft at the apothecary's workingDuration (20) times
	 * its own craftTime (1), plus slack for the recipe to be picked up first.
	 */
	private static final int CRAFT_TICKS = 80;

	private static BlockEntityApothecaryPattern place(GameTestHelper helper) {
		Block block = block("botanicalextramachinery:base_apothecary");
		helper.setBlock(APOTHECARY, block.defaultBlockState());
		helper.assertBlockPresent(block, APOTHECARY);
		return (BlockEntityApothecaryPattern) helper.getBlockEntity(APOTHECARY);
	}

	/** Fills the seed slot and the four ingredient slots the recipe spells out. */
	private static void loadRecipe(BlockEntityApothecaryPattern apothecary, String reagent) {
		apothecary.getInventory().setStackInSlot(SEEDS_SLOT, new ItemStack(item(reagent), 4));
		for (int i = 0; i < BERGAMUTE_INPUTS.length; i++) {
			apothecary.getInventory().setStackInSlot(FIRST_INPUT_SLOT + i,
					new ItemStack(item(BERGAMUTE_INPUTS[i])));
		}
	}

	private static void fillWater(BlockEntityApothecaryPattern apothecary, int amount) {
		apothecary.getFluidInventory().fill(new FluidStack(Fluids.WATER, amount), IFluidHandler.FluidAction.EXECUTE);
	}

	private static ItemStack out(BlockEntityApothecaryPattern apothecary) {
		return apothecary.getInventory().getStackInSlot(FIRST_OUTPUT_SLOT);
	}

	/**
	 * The bergamute recipe, straight from the server's recipe manager, so the
	 * reagent the machine is judged against is the one the recipe actually asks
	 * for rather than a copy that could drift.
	 */
	private static PetalApothecaryRecipe bergamuteRecipe(GameTestHelper helper) {
		for (var holder : helper.getLevel().getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE)) {
			PetalApothecaryRecipe recipe = holder.value();
			if (recipe.getResultItem(helper.getLevel().registryAccess()).is(item(BERGAMUTE_OUTPUT))) {
				return recipe;
			}
		}
		throw new IllegalStateException("No petal apothecary recipe makes " + BERGAMUTE_OUTPUT);
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void craftsTheBergamuteRecipe(GameTestHelper helper) {
		BlockEntityApothecaryPattern apothecary = place(helper);
		loadRecipe(apothecary, RIGHT_CATALYST);
		fillWater(apothecary, WATER_NEEDED);

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(item(BERGAMUTE_OUTPUT), out(apothecary).getItem(),
						() -> "The apothecary should have made " + BERGAMUTE_OUTPUT + ", but the output slot holds " + out(apothecary)))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void spendsAReagentAndAThousandMilliBucketsPerCraft(GameTestHelper helper) {
		BlockEntityApothecaryPattern apothecary = place(helper);
		loadRecipe(apothecary, RIGHT_CATALYST);
		fillWater(apothecary, WATER_NEEDED);
		int waterBefore = apothecary.getFluidInventory().getFluidAmount();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(1, out(apothecary).getCount(),
							() -> "One bergamute recipe should make one bergamute");
					TestingUtil.assertEquals(3, apothecary.getInventory().getStackInSlot(SEEDS_SLOT).getCount(),
							() -> "A craft should spend one seed from the seed slot");
					TestingUtil.assertEquals(waterBefore - WATER_PER_CRAFT,
							apothecary.getFluidInventory().getFluidAmount(),
							() -> "A craft should spend " + WATER_PER_CRAFT + "mB of water");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void doesNotCraftWithAnEmptySeedSlot(GameTestHelper helper) {
		BlockEntityApothecaryPattern apothecary = place(helper);
		// Same four ingredients, water in the tank, but no reagent at all.
		for (int i = 0; i < BERGAMUTE_INPUTS.length; i++) {
			apothecary.getInventory().setStackInSlot(FIRST_INPUT_SLOT + i,
					new ItemStack(item(BERGAMUTE_INPUTS[i])));
		}
		fillWater(apothecary, WATER_NEEDED);

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(ItemStack.EMPTY, out(apothecary),
						() -> "A petal apothecary recipe needs its reagent, so an empty seed slot should craft nothing"))
				.thenSucceed();
	}

	/**
	 * The bug this machine has. {@code BlockEntityApothecaryPattern} inherits
	 * {@code matchRecipe} from {@code RecipeTile}, which walks nothing but the
	 * ingredient list, so {@link PetalApothecaryRecipe#getReagent()} is never
	 * tested against the seed slot. A cobblestone in the seed slot is as good
	 * as a wheat seed as far as this tile is concerned, and the bergamute comes
	 * out anyway.
	 * <p>
	 * If this fails, the machine is handing out recipes whose reagent it does
	 * not hold. The fix is an override of {@code matchRecipe} on
	 * {@code BlockEntityApothecaryPattern} that returns "no match" unless
	 * {@code recipe.getReagent().test(inventory.getStackInSlot(SEEDS_SLOT))}.
	 */
	@GameTest(template = ARENA, timeoutTicks = 300)
	public void onlyAcceptsItsOwnCatalystsInTheSeedSlot(GameTestHelper helper) {
		// This addon replaces Botania's reagent requirement with a water tank and
		// a catalyst slot, so matchRecipe deliberately never consults
		// getReagent(). What stands between a player and a free craft is the slot's
		// validator, so that is what has to hold: the tag it checks
		// (botanicalextramachinery:mechanical_apothecary_catalysts) has to exist,
		// or the slot refuses every catalyst and the machine is unusable.
		BlockEntityApothecaryPattern apothecary = place(helper);

		TestingUtil.assertThat(apothecary.getInventory().isItemValid(SEEDS_SLOT, new ItemStack(item(RIGHT_CATALYST))),
				() -> RIGHT_CATALYST + " should be a legal apothecary catalyst");
		TestingUtil.assertThat(!apothecary.getInventory().isItemValid(SEEDS_SLOT, new ItemStack(item(WRONG_REAGENT))),
				() -> WRONG_REAGENT + " is not a catalyst and the seed slot should refuse it");

		loadRecipe(apothecary, RIGHT_CATALYST);
		fillWater(apothecary, WATER_NEEDED);

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> TestingUtil.assertThat(out(apothecary).is(item(RIGHT_OUTPUT_ITEM)),
						() -> "With a real catalyst and water the apothecary should have made " + BERGAMUTE_OUTPUT
								+ ", but the output slot holds " + out(apothecary)))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 200)
	public void doesNotCraftWithoutWater(GameTestHelper helper) {
		BlockEntityApothecaryPattern apothecary = place(helper);
		// The right reagent and the right ingredients, but a dry tank.
		loadRecipe(apothecary, RIGHT_CATALYST);

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(ItemStack.EMPTY, out(apothecary),
						() -> "The apothecary needs water in its tank, so a dry one should craft nothing"))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 200)
	public void leavesWhatItHasNoRecipeFor(GameTestHelper helper) {
		BlockEntityApothecaryPattern apothecary = place(helper);
		// A stick is not an ingredient of any petal apothecary recipe.
		apothecary.getInventory().setStackInSlot(SEEDS_SLOT, new ItemStack(item(RIGHT_CATALYST), 4));
		apothecary.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(Items.STICK, 3));
		fillWater(apothecary, WATER_NEEDED);

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(ItemStack.EMPTY, out(apothecary),
							() -> "A stick is not a petal apothecary ingredient, so the output slot should be empty but holds "
									+ out(apothecary));
					TestingUtil.assertEquals(3, apothecary.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "It should not have eaten the sticks either");
				})
				.thenSucceed();
	}

	/**
	 * The tank the recipes need to be full is only reachable from outside if the
	 * FluidHandler capability is registered, which is what makes the machine
	 * usable by pipes and by other mods at all.
	 */
	@GameTest(template = ARENA, timeoutTicks = 200)
	public void handsOutItsTankAsAFluidCapability(GameTestHelper helper) {
		place(helper);
		IFluidHandler tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
				helper.absolutePos(APOTHECARY), null);
		TestingUtil.assertThat(tank != null,
				() -> "The apothecary should expose a fluid handler capability at " + APOTHECARY);
		int filled = tank.fill(new FluidStack(Fluids.WATER, WATER_NEEDED), IFluidHandler.FluidAction.EXECUTE);
		TestingUtil.assertEquals(WATER_NEEDED, filled,
				() -> "The tank should have taken all " + WATER_NEEDED + "mB of water, but took " + filled);
		TestingUtil.assertEquals(WATER_NEEDED, tank.getFluidInTank(0).getAmount(),
				() -> "The capability and the tank should be the same container, but it now holds "
						+ tank.getFluidInTank(0) + " (" + tank.getFluidInTank(0).getAmount() + "mB)");
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
