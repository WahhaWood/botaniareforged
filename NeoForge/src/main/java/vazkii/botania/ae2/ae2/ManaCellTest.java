/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package vazkii.botania.ae2.ae2;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import vazkii.botania.ae2.item.ManaCellItem;
import vazkii.botania.ae2.item.cell.ManaCellInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;

import vazkii.botania.test.TestingUtil;

/**
 * Covers the mana cells Applied Botanics adds to Applied Energistics: a cell
 * holds mana against a byte budget and reports how full it is to the network.
 * <p>
 * The cells store their contents in the stack's own components, so each case
 * builds its own stack and never touches shared state.
 */
public class ManaCellTest {
	private static final String ARENA = "botania:addon_arena";
	/** What one byte of a cell is worth in mana, as the cell's own key type says. */
	private static final int PER_BYTE = 500;

	private static final ManaCellItem[] CELLS = {
			cell("mana_cell_1k"), cell("mana_cell_4k"), cell("mana_cell_16k"),
			cell("mana_cell_64k"), cell("mana_cell_256k"),
	};

	private static ManaCellItem cell(String id) {
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
				.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("botania", id));
		if (!(item instanceof ManaCellItem cell)) {
			throw new IllegalStateException("Not a mana cell: botania:" + id);
		}
		return cell;
	}

	private static StorageCell open(ManaCellItem cell) {
		return new ManaCellInventory(cell, new ItemStack(cell), null);
	}

	@GameTest(template = ARENA)
	public void everyCellHoldsItsByteBudget(GameTestHelper helper) {
		for (ManaCellItem cell : CELLS) {
			var open = open(cell);
			long expected = cell.getTotalBytes() * (long) PER_BYTE;
			long inserted = open.insert(ManaKey.KEY, Long.MAX_VALUE, Actionable.MODULATE,
					IActionSource.empty());

			TestingUtil.assertEquals(expected, inserted,
					() -> "A cell of " + cell.getTotalBytes() + " bytes should hold "
							+ expected + " mana, but took " + inserted);
			TestingUtil.assertEquals(CellState.FULL, open.getStatus(),
					() -> "A full cell should report itself as full");
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void biggerCellsHoldMore(GameTestHelper helper) {
		long previous = 0;
		for (ManaCellItem cell : CELLS) {
			long held = cell.getTotalBytes() * (long) PER_BYTE;
			long below = previous;
			TestingUtil.assertThat(held > below,
					() -> "Cell tiers should grow: " + held + " after " + below);
			previous = held;
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void aFreshCellIsEmpty(GameTestHelper helper) {
		for (ManaCellItem cell : CELLS) {
			TestingUtil.assertEquals(CellState.EMPTY, open(cell).getStatus(),
					() -> "A new cell should start empty");
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void statusTracksHowFullTheCellIs(GameTestHelper helper) {
		var open = open(CELLS[0]);
		long capacity = CELLS[0].getTotalBytes() * (long) PER_BYTE;
		var modulate = Actionable.MODULATE;

		open.insert(ManaKey.KEY, capacity / 4, modulate, IActionSource.empty());
		TestingUtil.assertEquals(CellState.NOT_EMPTY, open.getStatus(),
				() -> "A barely filled cell is simply not empty");

		open.insert(ManaKey.KEY, capacity / 2, modulate, IActionSource.empty());
		TestingUtil.assertEquals(CellState.TYPES_FULL, open.getStatus(),
				() -> "A cell past half full is full enough for its type to matter");
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void whatADryRunSaysIsWhatHappens(GameTestHelper helper) {
		var open = open(CELLS[0]);
		var simulate = Actionable.SIMULATE;
		var modulate = Actionable.MODULATE;
		long wanted = 1234;

		long promised = open.insert(ManaKey.KEY, wanted, simulate, IActionSource.empty());
		TestingUtil.assertEquals(wanted, promised,
				() -> "An empty cell should promise all of what was offered");
		TestingUtil.assertEquals(CellState.EMPTY, open.getStatus(),
				() -> "A dry run must not change the cell");

		open.insert(ManaKey.KEY, wanted, modulate, IActionSource.empty());
		TestingUtil.assertEquals(wanted, open.extract(ManaKey.KEY, wanted, simulate, IActionSource.empty()),
				() -> "The cell should now hold what it promised");
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void extractsWhatWasPutIn(GameTestHelper helper) {
		var open = open(CELLS[1]);
		var modulate = Actionable.MODULATE;
		long capacity = CELLS[1].getTotalBytes() * (long) PER_BYTE;
		open.insert(ManaKey.KEY, capacity, modulate, IActionSource.empty());

		TestingUtil.assertEquals(capacity, open.extract(ManaKey.KEY, Long.MAX_VALUE, modulate, IActionSource.empty()),
				() -> "The cell should hand back everything it was given");
		TestingUtil.assertEquals(CellState.EMPTY, open.getStatus(),
				() -> "An emptied cell should be empty again");
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void neverGivesMoreThanItHolds(GameTestHelper helper) {
		var open = open(CELLS[0]);
		var modulate = Actionable.MODULATE;
		long stored = 700;
		open.insert(ManaKey.KEY, stored, modulate, IActionSource.empty());

		long handed = open.extract(ManaKey.KEY, 5000, modulate, IActionSource.empty());
		TestingUtil.assertEquals(stored, handed,
				() -> "A cell should refuse to make up mana it does not have, but handed over " + handed);
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void refusesAnythingButMana(GameTestHelper helper) {
		var open = open(CELLS[0]);
		var modulate = Actionable.MODULATE;
		var other = AEItemKey.of(new ItemStack(Items.STONE));

		long took = open.insert(other, 1000, modulate, IActionSource.empty());
		TestingUtil.assertEquals(0L, took,
				() -> "A mana cell is not a place to put stone, but it took " + took);
		long gave = open.extract(other, 1000, modulate, IActionSource.empty());
		TestingUtil.assertEquals(0L, gave,
				() -> "A mana cell has no stone to give back, but it gave " + gave);
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void idleDrainGrowsWithTheTier(GameTestHelper helper) {
		double previous = -1;
		for (ManaCellItem cell : CELLS) {
			double drain = open(cell).getIdleDrain();
			double below = previous;
			TestingUtil.assertThat(drain > below,
					() -> "Idle drain should rise with the tier: " + drain + " after " + below);
			previous = drain;
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void cellsAreNotStackable(GameTestHelper helper) {
		for (ManaCellItem cell : CELLS) {
			TestingUtil.assertEquals(1, new ItemStack(cell).getMaxStackSize(),
					() -> "A cell must not stack, or one cell would hold another's mana");
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void manaIsItsOwnKeyType(GameTestHelper helper) {
		TestingUtil.assertEquals(PER_BYTE, (int) ManaKeyType.TYPE.getAmountPerByte(),
				() -> "The cell maths assumes a fixed mana per byte");
		TestingUtil.assertThat(ManaKey.KEY.getType() == ManaKeyType.TYPE,
				() -> "ManaKey should use the mana key type");
		helper.succeed();
	}
}