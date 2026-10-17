/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityOrechidPattern;
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

import java.util.Set;
import java.util.Locale;

/**
 * Covers Botanical Extra Machinery's orechid: it takes one lump of stone and
 * hands back a lump of ore, for mana.
 * <p>
 * Stone and deepslate each have eight possible outputs and the recipe picks
 * between them by weight, so a test cannot say which ore it will get. What it
 * can pin is the contract around it: one input becomes one output of some ore,
 * several inputs become several outputs, and nothing happens without mana.
 */
public class BemOrechidTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos ORECHID = new BlockPos(2, 2, 2);

	/** The base orechid's own slot layout: five inputs, each with an output. */
	private static final int FIRST_INPUT_SLOT = 3;
	private static final int FIRST_OUTPUT_SLOT = 8;

	/**
	 * One ore costs this much, whatever the recipe's own mana figure says.
	 */
	private static final int MANA_PER_ORE =
			net.lmor.botanicalextramachinery.config.LibXServerConfig.OrechidSettings.recipeCost;
	/** How many at a time the base orechid crafts, and how long it rests after. */
	private static final int BATCH =
			net.lmor.botanicalextramachinery.config.LibXServerConfig.OrechidSettings.baseOrechid.countCraft;
	private static final int COOLDOWN =
			net.lmor.botanicalextramachinery.config.LibXServerConfig.OrechidSettings.baseOrechid.cooldown;
	/** Long enough for a craft plus a rest, so a second pass can have happened. */
	private static final int CRAFT_TICKS = COOLDOWN + 5;

	private static final Set<Item> STONE_ORES = Set.of(
			Items.COAL_ORE, Items.COPPER_ORE, Items.DIAMOND_ORE, Items.EMERALD_ORE,
			Items.GOLD_ORE, Items.IRON_ORE, Items.LAPIS_ORE, Items.REDSTONE_ORE);

	private static BlockEntityOrechidPattern placeOrechid(GameTestHelper helper) {
		return placeOrechid(helper, "base_orechid", ORECHID);
	}

	private static BlockEntityOrechidPattern placeOrechid(GameTestHelper helper, String id, BlockPos pos) {
		Block block = block("botanicalextramachinery:" + id);
		helper.setBlock(pos, block.defaultBlockState());
		helper.assertBlockPresent(block, pos);
		return (BlockEntityOrechidPattern) helper.getBlockEntity(pos);
	}

	private static void feed(BlockEntityOrechidPattern orechid, Item item, int count) {
		orechid.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item, count));
	}

	private static ItemStack output(BlockEntityOrechidPattern orechid) {
		return orechid.getInventory().getStackInSlot(FIRST_OUTPUT_SLOT);
	}

	private static int orechidManaFor(int ores) {
		return MANA_PER_ORE * ores;
	}

	@GameTest(template = ARENA)
	public void turnsStoneIntoOre(GameTestHelper helper) {
		var orechid = placeOrechid(helper);
		feed(orechid, Items.STONE, 1);
		orechid.receiveMana(orechidManaFor(1));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					ItemStack made = output(orechid);
					TestingUtil.assertThat(STONE_ORES.contains(made.getItem()),
							() -> "The orechid should make some kind of stone ore, but made " + made);
					TestingUtil.assertEquals(1, made.getCount(),
							() -> "One stone should make one ore");
					TestingUtil.assertEquals(0, orechid.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "The stone should have been spent");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void turnsDeepslateIntoDeepslateOre(GameTestHelper helper) {
		var orechid = placeOrechid(helper);
		feed(orechid, Items.DEEPSLATE, 1);
		orechid.receiveMana(orechidManaFor(1));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					ItemStack made = output(orechid);
					TestingUtil.assertThat(made.getItem().getDescription().getString().toLowerCase(Locale.ROOT)
							.contains("deepslate"),
							() -> "Deepslate should come back as a deepslate ore, but made " + made);
					TestingUtil.assertEquals(1, made.getCount(),
							() -> "One deepslate should make one ore");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void paysManaPerOre(GameTestHelper helper) {
		var orechid = placeOrechid(helper);
		feed(orechid, Items.STONE, 1);
		int charged = orechidManaFor(4);
		orechid.receiveMana(charged);

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(charged - orechidManaFor(1),
						orechid.getCurrentMana(),
						() -> "One ore should cost exactly one ore's worth of mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void makesNothingWithoutMana(GameTestHelper helper) {
		var orechid = placeOrechid(helper);
		feed(orechid, Items.STONE, 1);

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertThat(output(orechid).isEmpty(),
							() -> "An orechid with no mana should make nothing");
					TestingUtil.assertEquals(1, orechid.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "The stone should still be sitting there");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void stopsAtWhatItCanAfford(GameTestHelper helper) {
		var orechid = placeOrechid(helper);
		feed(orechid, Items.STONE, 4);
		// Enough mana for two, not for all four.
		orechid.receiveMana(orechidManaFor(2));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS * 2)
				.thenExecute(() -> {
					TestingUtil.assertEquals(2, output(orechid).getCount(),
							() -> "With mana for two it should make two and stop, but made "
									+ output(orechid).getCount() + " with " + orechid.getCurrentMana()
									+ " mana left");
					TestingUtil.assertEquals(4 - 2,
							orechid.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "The other two stones should still be waiting");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void waitsForItsOutputSlot(GameTestHelper helper) {
		var orechid = placeOrechid(helper);
		feed(orechid, Items.STONE, 1);
		orechid.receiveMana(orechidManaFor(4));
		// Something already in the way: the orechid pairs each input with one
		// output slot, and refuses to work while that slot is occupied.
		orechid.getInventory().setStackInSlot(FIRST_OUTPUT_SLOT, new ItemStack(Items.STONE, 1));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS * 2)
				.thenExecute(() -> {
					TestingUtil.assertThat(output(orechid).is(Items.STONE),
							() -> "The blocked output slot should still hold what it held");
					TestingUtil.assertEquals(1, orechid.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "The stone should not have been spent on a blocked slot");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void theOreachidKeepsItsOwnSettings(GameTestHelper helper) {
		// Every machine group in the addon declares its own nested base tier with
		// a cooldown and a batch size. If the config loader keys those nested
		// classes by their simple name, they overwrite each other and every
		// machine ends up running on whichever numbers were written last.
		var settings = net.lmor.botanicalextramachinery.config.LibXServerConfig.OrechidSettings.class;
		TestingUtil.assertEquals(10, COOLDOWN,
				() -> "The base orechid should rest for the 10 ticks it declares, but rests for " + COOLDOWN);
		TestingUtil.assertEquals(4, BATCH,
				() -> "The base orechid should craft four at a time, but crafts " + BATCH);
		TestingUtil.assertThat(settings != null, () -> "the settings class should be there");
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void everyTierHoldsMoreMana(GameTestHelper helper) {
		String[] tiers = { "base_orechid", "upgraded_orechid", "advanced_orechid", "ultimate_orechid" };
		int previous = 0;
		for (int i = 0; i < tiers.length; i++) {
			String tier = tiers[i];
			var orechid = placeOrechid(helper, tier, ORECHID.east(i * 2));
			int below = previous;
			int held = orechid.getMaxMana();
			TestingUtil.assertThat(held > below,
					() -> tier + " should hold more mana than the tier below it: " + held + " after " + below);
			previous = held;
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void makesABatchAtOnce(GameTestHelper helper) {
		var orechid = placeOrechid(helper);
		// The base orechid is configured to craft a batch at a time, so with
		// mana for all of them one pass should turn that many stones into ore.
		int batch = BATCH;
		feed(orechid, Items.STONE, batch);
		orechid.receiveMana(orechidManaFor(batch));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					ItemStack made = output(orechid);
					TestingUtil.assertEquals(batch, made.getCount(),
							() -> "A base orechid should craft a whole batch at once, but made "
									+ made.getCount() + " from " + batch + " stones with "
									+ orechid.getCurrentMana() + " mana left of " + orechidManaFor(batch)
									+ " and " + orechid.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount()
									+ " stones left");
					TestingUtil.assertThat(STONE_ORES.contains(made.getItem()),
							() -> "The batch should be ore, but made " + made);
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
}
