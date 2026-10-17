/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.lmor.botanicalextramachinery.blocks.tiles.BlockEntityGreenhouse;
import net.lmor.botanicalextramachinery.config.LibXServerConfig.GreenhouseSettings;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import vazkii.botania.test.TestingUtil;

/**
 * Covers Botanical Extra Machinery's greenhouse, which turns a mystical flower
 * into mana by burning Forge energy over a flower-shaped fuel item.
 * <p>
 * The recipe is not a Botania recipe at all: the addon pairs a
 * {@code GenFlowers} flower with a fuel item and pays out
 * {@code GreenhouseSettings.Flowers.<flower>} mana per fuel. Rafflowsia is the
 * cheapest of them at 100 mana, and it burns any Botania flower as fuel, so a
 * plain white mystical flower makes a fuel stack for it.
 * <p>
 * The machine is worth a test of its own because its whole output sits behind
 * an energy gate. {@code BlockEntityGreenhouse#tick()} opens with
 * 
 * <pre>
 * if (this.getMaxMana() == this.getCurrentMana()
 *         || this.getEnergyStored() &lt; this.energyCost
 *         || fuelSlots.isEmpty()) { ... return; }
 * </pre>
 * 
 * so a greenhouse that has never been given energy returns before it reaches
 * any flower at all. The energy arrives through the Forge energy capability,
 * which is why the capability test comes first: without it there is no way in,
 * and the tile can never make mana no matter what is planted in it.
 * <p>
 * The upgrade tests cover the flower-slot limit. Taking a limit upgrade out
 * under a partly filled flower slot used to hand a negative count to
 * {@code ItemStack#setCount}, which 1.21.1 rejects, so the tile threw on the
 * tick that dropped the upgrade.
 */
public class BemGreenhouseTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos GREENHOUSE = new BlockPos(2, 2, 2);

	/** The generator flower, and what it will burn. */
	private static final String FLOWER = "botania:rafflowsia";
	private static final String FUEL = "botania:white_mystical_flower";
	private static final int FUEL_STACK = 8;

	/** The upgrade slot for the per-slot flower limit is the first of the eight. */
	private static final int FLOWER_LIMIT_UPGRADE_SLOT = 0;
	private static final String FLOWER_LIMIT_UPGRADE = "botanicalextramachinery:upgrade_flower_16x";

	/** Mana the chosen flower pays per fuel item, and energy it costs to pay it. */
	private static final int MANA_PER_FUEL = GreenhouseSettings.Flowers.rafflowsia;
	private static final int ENERGY_PER_FUEL = GreenhouseSettings.energyCost;

	/**
	 * The greenhouse only tries to craft once every {@code GreenhouseSettings.sleep}
	 * ticks, so this has to span several of those periods.
	 */
	private static final int IDLE_TICKS = GreenhouseSettings.sleep * 3 + 20;

	private static BlockEntityGreenhouse place(GameTestHelper helper) {
		Block block = block("botanicalextramachinery:greenhouse");
		helper.setBlock(GREENHOUSE, block.defaultBlockState());
		helper.assertBlockPresent(block, GREENHOUSE);
		return (BlockEntityGreenhouse) helper.getBlockEntity(GREENHOUSE);
	}

	/**
	 * Plants the generator flower and gives it fuel. The flower goes in first:
	 * the fuel slot's validator asks which flower the fuel is for, so a fuel
	 * item is only accepted while a flower is already in the machine.
	 */
	private static void plant(BlockEntityGreenhouse greenhouse) {
		greenhouse.getFlowerInventory().setStackInSlot(0, new ItemStack(item(FLOWER)));
		greenhouse.getInventory().setStackInSlot(0, new ItemStack(item(FUEL), FUEL_STACK));
	}

	private static int fuelLeft(BlockEntityGreenhouse greenhouse) {
		return greenhouse.getInventory().getStackInSlot(0).getCount();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void handsOutItsEnergyTankAsABlockCapability(GameTestHelper helper) {
		place(helper);
		IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
				helper.absolutePos(GREENHOUSE), null);
		TestingUtil.assertThat(energy != null,
				() -> "The greenhouse should expose an energy storage capability at " + GREENHOUSE);
		TestingUtil.assertEquals(GreenhouseSettings.energyCapacity, energy.getMaxEnergyStored(),
				() -> "The greenhouse should hold the configured energy capacity");
		helper.succeed();
	}

	/**
	 * The question the tile's opening energy check turns on: can energy actually
	 * get in, and does the machine then pay out mana for a valid flower?
	 */
	@GameTest(template = ARENA, timeoutTicks = 400)
	public void producesManaFromItsFlowerWhenGivenEnergy(GameTestHelper helper) {
		BlockEntityGreenhouse greenhouse = place(helper);
		plant(greenhouse);
		int energyGiven = greenhouse.receiveEnergy(greenhouse.getMaxEnergyStored(), false);
		TestingUtil.assertEquals(greenhouse.getMaxEnergyStored(), energyGiven,
				() -> "The greenhouse should have taken a full tank of energy but took " + energyGiven);
		TestingUtil.assertThat(greenhouse.getEnergyStored() >= ENERGY_PER_FUEL,
				() -> "The greenhouse should be holding enough energy to pay for at least one craft");
		int fuelBefore = fuelLeft(greenhouse);

		helper.startSequence()
				.thenIdle(IDLE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertThat(greenhouse.getCurrentMana() >= MANA_PER_FUEL,
							() -> "A greenhouse with energy, a rafflowsia and fuel should have made mana, but holds "
									+ greenhouse.getCurrentMana());
					TestingUtil.assertThat(fuelLeft(greenhouse) < fuelBefore,
							() -> "Making mana should have burned some of the fuel, but the stack still holds "
									+ fuelLeft(greenhouse));
					TestingUtil.assertThat(greenhouse.getEnergyStored() < greenhouse.getMaxEnergyStored(),
							() -> "Making mana should have cost the greenhouse some energy");
				})
				.thenSucceed();
	}

	/**
	 * The same machine without the energy. This is the gate in
	 * {@code BlockEntityGreenhouse#tick()} doing its job, and it is the reason
	 * a greenhouse looks inert in a world where nothing ever powered it.
	 */
	@GameTest(template = ARENA, timeoutTicks = 400)
	public void makesNoManaWithoutEnergy(GameTestHelper helper) {
		BlockEntityGreenhouse greenhouse = place(helper);
		plant(greenhouse);

		helper.startSequence()
				.thenIdle(IDLE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(0, greenhouse.getCurrentMana(),
							() -> "A greenhouse with no energy should make no mana");
					TestingUtil.assertEquals(FUEL_STACK, fuelLeft(greenhouse),
							() -> "A greenhouse with no energy should not burn its fuel either");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 200)
	public void makesNoManaWithoutAFlower(GameTestHelper helper) {
		BlockEntityGreenhouse greenhouse = place(helper);
		// Fuel on its own is not a recipe: the flower is what says what to make.
		greenhouse.getInventory().setStackInSlot(0, new ItemStack(item(FUEL), FUEL_STACK));
		greenhouse.receiveEnergy(greenhouse.getMaxEnergyStored(), false);

		helper.startSequence()
				.thenIdle(IDLE_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(0, greenhouse.getCurrentMana(),
						() -> "A greenhouse with fuel but no flower should make no mana"))
				.thenSucceed();
	}

	/**
	 * Putting a wide flower-limit upgrade over a slot holding fewer flowers than
	 * the upgrade allows is the shape of the old crash: the tile recomputed the
	 * slot's contents as {@code count - slotLimit} without asking whether that
	 * was positive.
	 */
	@GameTest(template = ARENA, timeoutTicks = 200)
	public void wideningTheFlowerLimitUnderASparseSlotDoesNotThrow(GameTestHelper helper) {
		BlockEntityGreenhouse greenhouse = place(helper);
		greenhouse.getFlowerInventory().setStackInSlot(0, new ItemStack(item(FLOWER)));

		helper.startSequence()
				.thenExecute(() -> greenhouse.getUpgradeInventory().setStackInSlot(FLOWER_LIMIT_UPGRADE_SLOT,
						new ItemStack(item(FLOWER_LIMIT_UPGRADE))))
				.thenIdle(5)
				.thenExecute(() -> TestingUtil.assertEquals(1,
						greenhouse.getFlowerInventory().getStackInSlot(0).getCount(),
						() -> "Widening the limit should leave the flower where it was"))
				.thenSucceed();
	}

	/**
	 * And the other direction: taking the limit away under a slot that holds
	 * more than the base limit has to spill the excess, not crash on it.
	 */
	@GameTest(template = ARENA, timeoutTicks = 200)
	public void removingTheFlowerLimitSpillsWhatNoLongerFits(GameTestHelper helper) {
		BlockEntityGreenhouse greenhouse = place(helper);
		greenhouse.getFlowerInventory().setStackInSlot(0, new ItemStack(item(FLOWER)));
		greenhouse.getUpgradeInventory().setStackInSlot(FLOWER_LIMIT_UPGRADE_SLOT,
				new ItemStack(item(FLOWER_LIMIT_UPGRADE)));
		// Four flowers fit under a 16x limit but not under the base limit of one.
		greenhouse.getFlowerInventory().setStackInSlot(0, new ItemStack(item(FLOWER), 4));

		helper.startSequence()
				.thenExecute(() -> greenhouse.getUpgradeInventory().setStackInSlot(FLOWER_LIMIT_UPGRADE_SLOT, ItemStack.EMPTY))
				.thenIdle(5)
				.thenExecute(() -> TestingUtil.assertEquals(1,
						greenhouse.getFlowerInventory().getStackInSlot(0).getCount(),
						() -> "Only one flower fits under the base limit, so three should have been dropped"))
				.thenSucceed();
	}

	/**
	 * A slot holding exactly the base limit, with the upgrade coming out, is the
	 * borderline the negative-count crash lived on: {@code count - slotLimit} is
	 * zero, which must be left alone rather than turned into a stack of nought
	 * or a dropped item.
	 */
	@GameTest(template = ARENA, timeoutTicks = 200)
	public void removingTheFlowerLimitAtExactlyTheLimitDoesNotThrow(GameTestHelper helper) {
		BlockEntityGreenhouse greenhouse = place(helper);
		greenhouse.getUpgradeInventory().setStackInSlot(FLOWER_LIMIT_UPGRADE_SLOT,
				new ItemStack(item(FLOWER_LIMIT_UPGRADE)));
		greenhouse.getFlowerInventory().setStackInSlot(0, new ItemStack(item(FLOWER), 2));

		helper.startSequence()
				.thenExecute(() -> greenhouse.getUpgradeInventory().setStackInSlot(FLOWER_LIMIT_UPGRADE_SLOT, ItemStack.EMPTY))
				.thenIdle(5)
				.thenExecute(() -> TestingUtil.assertEquals(1,
						greenhouse.getFlowerInventory().getStackInSlot(0).getCount(),
						() -> "One flower fits under the base limit, so one should have been dropped"))
				.thenSucceed();
	}

	/** The other upgrade slots are unrelated to flowers, but removing them should be just as quiet. */
	@GameTest(template = ARENA, timeoutTicks = 200)
	public void droppingAnyUpgradeLeavesTheMachineStanding(GameTestHelper helper) {
		BlockEntityGreenhouse greenhouse = place(helper);
		String[] upgrades = {
				"botanicalextramachinery:upgrade_flower_4x",
				"botanicalextramachinery:upgrade_flower_16x",
				"botanicalextramachinery:upgrade_flower_32x",
				"botanicalextramachinery:upgrade_flower_64x",
		};

		helper.startSequence()
				.thenExecute(() -> {
					for (int slot = 0; slot < upgrades.length; slot++) {
						greenhouse.getUpgradeInventory().setStackInSlot(slot, new ItemStack(item(upgrades[slot])));
					}
				})
				.thenIdle(5)
				.thenExecute(() -> {
					for (int slot = 0; slot < upgrades.length; slot++) {
						greenhouse.getUpgradeInventory().setStackInSlot(slot, ItemStack.EMPTY);
					}
				})
				.thenIdle(5)
				.thenExecute(() -> TestingUtil.assertEquals(ItemStack.EMPTY,
						greenhouse.getUpgradeInventory().getStackInSlot(FLOWER_LIMIT_UPGRADE_SLOT),
						() -> "The upgrade slots should be empty again"))
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
