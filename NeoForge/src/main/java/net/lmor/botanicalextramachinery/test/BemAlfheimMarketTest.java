/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityAlfheimMarketPattern;
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

import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.crafting.LexiconElvenTradeRecipe;
import vazkii.botania.test.TestingUtil;

/**
 * Covers Botanical Extra Machinery's alfheim market, the elven trade machine: it
 * finds the trade its inputs spell out and hands back the result.
 * <p>
 * The recipe is the addon's own
 * {@code botanicalextramachinery:recipe/elven_trade/malachite_dragonstone}: a
 * malachite ingot and a dragonstone become a malachite dragonstone. It is named
 * here rather than looked up so that an edit to the recipe shows up as a
 * failure.
 * <p>
 * The machine is worth a test of its own because it does not take a mana cost
 * from its recipe at all. Botania's {@code ElvenTradeRecipe} carries no mana
 * field, so {@code BlockEntityAlfheimMarketPattern} charges a flat
 * {@code AlfheimMarketSettings.recipeCost} of progress per craft instead, which
 * makes one craft twenty ticks no matter how big the inputs are.
 * <p>
 * It is also the one machine that walks every recipe of its type without
 * looking at {@code ElvenTradeRecipe.isSpecial()}. Botania ships a special
 * recipe, {@code data/botania/recipe/elven_trade/lexica_botania_upgrade}, which
 * the elven gateway is supposed to keep to itself, so
 * {@link #doesNotCraftTheSpecialLexiconRecipe(GameTestHelper)} pins that down.
 */
public class BemAlfheimMarketTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos MARKET = new BlockPos(2, 2, 2);

	/** The base market's own slot layout. */
	private static final int FIRST_INPUT_SLOT = 0;
	private static final int LAST_INPUT_SLOT = 2;
	private static final int FIRST_OUTPUT_SLOT = 3;
	private static final int LAST_OUTPUT_SLOT = 5;

	/** botanicalextramachinery:recipe/elven_trade/malachite_dragonstone. */
	private static final String MALACHITE_INGOT = "botanicalextramachinery:malachite_ingot";
	private static final String DRAGONSTONE = "botania:dragonstone";
	private static final String MALACHITE_DRAGONSTONE = "botanicalextramachinery:malachite_dragonstone";

	/**
	 * What one craft costs. The recipe itself has no mana cost, so this is
	 * {@code AlfheimMarketSettings.recipeCost}, which is also the machine's
	 * progress requirement.
	 */
	private static final int RECIPE_MANA = 500;

	/**
	 * data/botania:recipe/elven_trade/lexica_botania_upgrade, the special recipe
	 * that belongs to the elven gateway. It turns a plain Lexica Botania into an
	 * elven-unlocked one, which is still the same item, so the machine must be
	 * checked by its output slot rather than by the item itself.
	 */
	private static final String LEXICA_BOTANIA = "botania:lexica_botania";

	/**
	 * One craft is {@code recipeCost / workingDuration}, 500 over 25, so twenty
	 * ticks. 80 leaves room for the recipe resync the machine does after it
	 * finishes.
	 */
	private static final int CRAFT_TICKS = 80;

	private static BlockEntityAlfheimMarketPattern place(GameTestHelper helper) {
		Block block = block("botanicalextramachinery:base_alfheim_market");
		helper.setBlock(MARKET, block.defaultBlockState());
		helper.assertBlockPresent(block, MARKET);
		BlockEntityAlfheimMarketPattern market = (BlockEntityAlfheimMarketPattern) helper.getBlockEntity(MARKET);
		// The market pays out of its own buffer, so a fresh one would just sit idle.
		market.setCurrentMana(market.getMaxMana());
		return market;
	}

	private static ItemStack slot(BlockEntityAlfheimMarketPattern market, int slot) {
		return market.getInventory().getStackInSlot(slot);
	}

	/** Everything the machine made, across every output slot. */
	private static int countIn(BlockEntityAlfheimMarketPattern market, String id) {
		Item wanted = item(id);
		int total = 0;
		for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT; slot++) {
			if (slot(market, slot).is(wanted)) {
				total += slot(market, slot).getCount();
			}
		}
		return total;
	}

	private static String outputs(BlockEntityAlfheimMarketPattern market) {
		StringBuilder sb = new StringBuilder();
		for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT; slot++) {
			if (!slot(market, slot).isEmpty()) {
				sb.append(slot(market, slot)).append(" in slot ").append(slot).append("; ");
			}
		}
		return sb.isEmpty() ? "nothing" : sb.toString();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void craftsTheElvenTradeRecipe(GameTestHelper helper) {
		BlockEntityAlfheimMarketPattern market = place(helper);
		market.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(MALACHITE_INGOT), 1));
		market.getInventory().setStackInSlot(FIRST_INPUT_SLOT + 1, new ItemStack(item(DRAGONSTONE), 1));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(item(MALACHITE_DRAGONSTONE),
						slot(market, FIRST_OUTPUT_SLOT).getItem(),
						() -> "The market should have made " + MALACHITE_DRAGONSTONE
								+ ", but its output slots hold " + outputs(market)))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void consumesTheInputsAndPaysInMana(GameTestHelper helper) {
		BlockEntityAlfheimMarketPattern market = place(helper);
		market.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(MALACHITE_INGOT), 1));
		market.getInventory().setStackInSlot(FIRST_INPUT_SLOT + 1, new ItemStack(item(DRAGONSTONE), 1));
		int manaBefore = market.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(manaBefore - RECIPE_MANA, market.getCurrentMana(),
							() -> "A trade costing " + RECIPE_MANA + " should leave " + (manaBefore - RECIPE_MANA)
									+ " mana, but the market holds " + market.getCurrentMana());
					TestingUtil.assertEquals(ItemStack.EMPTY, slot(market, FIRST_INPUT_SLOT),
							() -> "The malachite ingot that was traded away should be gone from slot "
									+ FIRST_INPUT_SLOT + ", but it holds " + slot(market, FIRST_INPUT_SLOT));
					TestingUtil.assertEquals(ItemStack.EMPTY, slot(market, FIRST_INPUT_SLOT + 1),
							() -> "The dragonstone that was traded away should be gone from slot "
									+ (FIRST_INPUT_SLOT + 1) + ", but it holds " + slot(market, FIRST_INPUT_SLOT + 1));
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void refusesTheWrongInputs(GameTestHelper helper) {
		BlockEntityAlfheimMarketPattern market = place(helper);
		// A stick is an ingredient of no elven trade recipe the addon ships.
		for (int slot = FIRST_INPUT_SLOT; slot <= LAST_INPUT_SLOT; slot++) {
			market.getInventory().setStackInSlot(slot, new ItemStack(Items.STICK, 3));
		}
		int manaBefore = market.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, market.getProgress(),
							() -> "Inputs that spell no trade should not start a craft");
					TestingUtil.assertEquals(0, countIn(market, MALACHITE_DRAGONSTONE),
							() -> "A market full of sticks should not make anything, but its output slots hold "
									+ outputs(market));
					TestingUtil.assertEquals(manaBefore, market.getCurrentMana(),
							() -> "A market with no recipe should not spend mana, but it went from " + manaBefore
									+ " to " + market.getCurrentMana());
					TestingUtil.assertEquals(3, slot(market, FIRST_INPUT_SLOT).getCount(),
							() -> "The market should not have eaten the sticks either");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void doesNothingWithEmptyInputs(GameTestHelper helper) {
		BlockEntityAlfheimMarketPattern market = place(helper);
		int manaBefore = market.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, market.getProgress(),
							() -> "An empty market should not start a craft");
					TestingUtil.assertEquals(0, countIn(market, MALACHITE_DRAGONSTONE),
							() -> "An empty market should not make anything, but its output slots hold "
									+ outputs(market));
					TestingUtil.assertEquals(manaBefore, market.getCurrentMana(),
							() -> "An empty market should not spend mana, but it went from " + manaBefore
									+ " to " + market.getCurrentMana());
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void refusesHalfATrade(GameTestHelper helper) {
		BlockEntityAlfheimMarketPattern market = place(helper);
		// One of the two ingredients is missing.
		market.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(MALACHITE_INGOT), 1));
		int manaBefore = market.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, market.getProgress(),
							() -> "One ingredient of two should not be enough to trade");
					TestingUtil.assertEquals(0, countIn(market, MALACHITE_DRAGONSTONE),
							() -> "Half a trade should make nothing, but its output slots hold " + outputs(market));
					TestingUtil.assertEquals(manaBefore, market.getCurrentMana(),
							() -> "Half a trade should not spend mana, but it went from " + manaBefore
									+ " to " + market.getCurrentMana());
				})
				.thenSucceed();
	}

	/**
	 * {@code BlockEntityAlfheimMarketPattern} never asks a recipe whether it is
	 * special, so the market sees Botania's gateway-only lexicon trade as just
	 * another elven trade. Botania keeps that one back with
	 * {@link LexiconElvenTradeRecipe#isSpecial()}, so a market given a Lexica
	 * Botania has to leave it alone.
	 */
	@GameTest(template = ARENA, timeoutTicks = 300)
	public void doesNotCraftTheSpecialLexiconRecipe(GameTestHelper helper) {
		BlockEntityAlfheimMarketPattern market = place(helper);
		market.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(LEXICA_BOTANIA), 1));
		int manaBefore = market.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					boolean specialLoaded = helper.getLevel().getRecipeManager()
							.getAllRecipesFor(BotaniaRecipeTypes.ELVEN_TRADE_TYPE).stream()
							.anyMatch(holder -> holder.value() instanceof LexiconElvenTradeRecipe lex && lex.isSpecial());
					TestingUtil.assertThat(specialLoaded,
							() -> "This test is only meaningful with Botania's special elven trade recipe loaded");

					TestingUtil.assertEquals(0, market.getProgress(),
							() -> "A special recipe belongs to the elven gateway, so the market should not have"
									+ " selected one and started a craft");
					TestingUtil.assertEquals(0, countIn(market, LEXICA_BOTANIA),
							() -> "The market upgraded the Lexica Botania, but the lexicon trade is special and"
									+ " belongs to the elven gateway");
					TestingUtil.assertEquals(manaBefore, market.getCurrentMana(),
							() -> "Refusing a special recipe should cost nothing, but the market went from "
									+ manaBefore + " to " + market.getCurrentMana());
					TestingUtil.assertEquals(item(LEXICA_BOTANIA), slot(market, FIRST_INPUT_SLOT).getItem(),
							() -> "The Lexica Botania should still be sitting untouched in slot " + FIRST_INPUT_SLOT
									+ ", but it holds " + slot(market, FIRST_INPUT_SLOT));
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
