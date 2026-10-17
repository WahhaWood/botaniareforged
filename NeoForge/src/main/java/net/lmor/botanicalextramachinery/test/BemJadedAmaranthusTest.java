/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import net.lmor.botanicalextramachinery.blocks.tiles.BlockEntityJadedAmaranthus;
import net.lmor.botanicalextramachinery.config.LibXServerConfig.JadedAmaranthusSettings;

import vazkii.botania.test.TestingUtil;

/**
 * Covers Botanical Extra Machinery's jaded amaranthus, which grows Botania's
 * sixteen mystical flowers out of mana, a colour at a time.
 * <p>
 * Which item it grows is the catalyst's business: mystical flower blocks by
 * default, petals with the petal catalyst, petal blocks with the block one. It
 * also refuses to start a pass it cannot pay for, which is why the tests watch
 * how far it got rather than only what it ended up with.
 */
public class BemJadedAmaranthusTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos AMARANTHUS = new BlockPos(2, 2, 2);

	private static final int FIRST_OUTPUT_SLOT = 2;
	private static final int COLOURS = 16;
	/** What one pass costs per colour. */
	private static final int MANA_PER_COLOUR = JadedAmaranthusSettings.costMana * JadedAmaranthusSettings.countCraft;
	/** A pass plus its rest. */
	private static final int PASS_TICKS = JadedAmaranthusSettings.cooldown + 5;

	private static final String[] COLOURS_BY_ID = {
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
	};

	private static BlockEntityJadedAmaranthus place(GameTestHelper helper) {
		Block block = block("botanicalextramachinery:jaded_amaranthus");
		helper.setBlock(AMARANTHUS, block.defaultBlockState());
		helper.assertBlockPresent(block, AMARANTHUS);
		return (BlockEntityJadedAmaranthus) helper.getBlockEntity(AMARANTHUS);
	}

	/** What the given colour's slot holds, or null when it is empty. */
	private static ItemStack made(BlockEntityJadedAmaranthus amaranthus, int colour) {
		return amaranthus.getInventory().getStackInSlot(FIRST_OUTPUT_SLOT + colour);
	}

	/** How many of the sixteen colours have something in their slot. */
	private static int countGrown(BlockEntityJadedAmaranthus amaranthus) {
		int grown = 0;
		for (int colour = 0; colour < COLOURS; colour++) {
			if (!made(amaranthus, colour).isEmpty()) {
				grown++;
			}
		}
		return grown;
	}

	private static List<Item> expected(String suffix) {
		List<Item> items = new ArrayList<>();
		for (String colour : COLOURS_BY_ID) {
			items.add(item("botania:" + colour + suffix));
		}
		return items;
	}

	private static void assertEveryColourIs(BlockEntityJadedAmaranthus amaranthus, String suffix) {
		List<Item> wanted = expected(suffix);
		List<String> wrong = new ArrayList<>();
		for (int colour = 0; colour < COLOURS; colour++) {
			ItemStack stack = made(amaranthus, colour);
			if (!stack.is(wanted.get(colour))) {
				wrong.add(COLOURS_BY_ID[colour] + "=" + stack);
			}
		}
		TestingUtil.assertThat(wrong.isEmpty(),
				() -> "The amaranthus should have grown every colour of " + suffix + ", but got " + wrong);
	}

	@GameTest(template = ARENA)
	public void growsEveryColourOfMysticalFlower(GameTestHelper helper) {
		var amaranthus = place(helper);
		amaranthus.receiveMana(MANA_PER_COLOUR * COLOURS);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> assertEveryColourIs(amaranthus, "_mystical_flower"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void growsNothingWithoutMana(GameTestHelper helper) {
		var amaranthus = place(helper);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> {
					List<String> grown = new ArrayList<>();
					for (int colour = 0; colour < COLOURS; colour++) {
						if (!made(amaranthus, colour).isEmpty()) {
							grown.add(COLOURS_BY_ID[colour]);
						}
					}
					TestingUtil.assertThat(grown.isEmpty(),
							() -> "An amaranthus with no mana should grow nothing, but grew " + grown);
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void paysManaPerColour(GameTestHelper helper) {
		var amaranthus = place(helper);
		int charged = MANA_PER_COLOUR * COLOURS;
		amaranthus.receiveMana(charged);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(0, amaranthus.getCurrentMana(),
						() -> "A pass that grows all sixteen colours should spend all sixteen lots of mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void stopsAtWhatItCanAfford(GameTestHelper helper) {
		var amaranthus = place(helper);
		// Enough for four colours and no more. The loop works its way along the
		// colours and gives up rather than going into debt.
		amaranthus.receiveMana(MANA_PER_COLOUR * 4);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, amaranthus.getCurrentMana(),
							() -> "It should have spent exactly what it could afford");
					int grown = countGrown(amaranthus);
					TestingUtil.assertEquals(4, grown,
							() -> "Four lots of mana should have grown four colours, but grew " + grown);
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void petalCatalystGrowsPetals(GameTestHelper helper) {
		var amaranthus = place(helper);
		amaranthus.getInventory().setStackInSlot(0, new ItemStack(item("botanicalextramachinery:catalyst_petal")));
		amaranthus.receiveMana(MANA_PER_COLOUR * COLOURS);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> assertEveryColourIs(amaranthus, "_mystical_petal"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void petalCatalystGrowsTwoPerCraft(GameTestHelper helper) {
		var amaranthus = place(helper);
		amaranthus.getInventory().setStackInSlot(0, new ItemStack(item("botanicalextramachinery:catalyst_petal")));
		amaranthus.receiveMana(MANA_PER_COLOUR * COLOURS);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> {
					// Petals are what the flower is made of, so two per craft.
					int expected = JadedAmaranthusSettings.countCraft * 2;
					TestingUtil.assertEquals(expected, made(amaranthus, 0).getCount(),
							() -> "The petal catalyst should double what a craft yields, but yielded "
									+ made(amaranthus, 0).getCount());
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void petalBlockCatalystGrowsPetalBlocks(GameTestHelper helper) {
		var amaranthus = place(helper);
		amaranthus.getInventory().setStackInSlot(0,
				new ItemStack(item("botanicalextramachinery:catalyst_petal_block")));
		// A petal block is nine petals, and it costs nine times as much.
		amaranthus.receiveMana(MANA_PER_COLOUR * 9 * COLOURS);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> assertEveryColourIs(amaranthus, "_petal_block"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void petalBlockCatalystCostsNineTimesAsMuch(GameTestHelper helper) {
		var amaranthus = place(helper);
		amaranthus.getInventory().setStackInSlot(0,
				new ItemStack(item("botanicalextramachinery:catalyst_petal_block")));
		int charged = MANA_PER_COLOUR * 9 * COLOURS;
		amaranthus.receiveMana(charged);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(0, amaranthus.getCurrentMana(),
						() -> "A pass of petal blocks should spend nine times what a pass of flowers does"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void infinityManaCatalystFillsItUp(GameTestHelper helper) {
		var amaranthus = place(helper);
		amaranthus.getInventory().setStackInSlot(0,
				new ItemStack(item("botanicalextramachinery:catalyst_mana_infinity")));

		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> TestingUtil.assertEquals(amaranthus.getMaxMana(), amaranthus.getCurrentMana(),
						() -> "The infinity catalyst should keep it topped up"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void keepsWhatItHasGrown(GameTestHelper helper) {
		var amaranthus = place(helper);
		amaranthus.receiveMana(MANA_PER_COLOUR * COLOURS);

		helper.startSequence()
				.thenIdle(PASS_TICKS)
				.thenExecute(() -> {
					// A second pass would need more mana than is left, so the
					// first pass's flowers should still all be there.
					int kept = countGrown(amaranthus);
					TestingUtil.assertEquals(COLOURS, kept,
							() -> "It should not have lost any of the flowers it grew, but kept " + kept);
				})
				.thenSucceed();
	}

	private static Block block(String id) {
		ResourceLocation key = ResourceLocation.parse(id);
		Block block = BuiltInRegistries.BLOCK.get(key);
		if (block == net.minecraft.world.level.block.Blocks.AIR) {
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