/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package com.jykito.voltany.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import com.jykito.voltany.block.ManaGeneratorBlock;
import com.jykito.voltany.block.entity.ManaGeneratorBlockEntity;
import com.jykito.voltany.registry.ModBlockEntities;
import com.jykito.voltany.registry.ModBlocks;
import com.jykito.voltany.registry.ModItems;

import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.test.TestingUtil;

/**
 * Covers Voltany's mana generator: it turns energy into mana and hands that mana
 * to a pool beside it.
 * <p>
 * Everything is placed by the test itself, so it does not depend on the
 * upstream arena data. The generator only acts on its own ticker, and the test
 * sequence runs before the block entities of a tick, so every check waits with
 * {@code thenIdle} first rather than looking at the state it just created.
 */
public class VoltanyManaGeneratorTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos GENERATOR = new BlockPos(2, 2, 2);
	private static final BlockPos POOL = new BlockPos(2, 2, 3);
	/** Long enough for the generator to spend a full charge and then idle. */
	private static final int DRAIN_TICKS = 40;

	private static ManaGeneratorBlockEntity placeGenerator(GameTestHelper helper) {
		helper.setBlock(GENERATOR, ModBlocks.MANA_GENERATOR.get().defaultBlockState());
		helper.assertBlockPresent(ModBlocks.MANA_GENERATOR.get(), GENERATOR);
		return TestingUtil.assertBlockEntity(helper, GENERATOR, ModBlockEntities.MANA_GENERATOR.get());
	}

	/** Feeds energy in, respecting the per call receive limit. */
	private static void chargeFully(ManaGeneratorBlockEntity generator) {
		int cap = generator.getEnergy().getMaxEnergyStored();
		int guard = cap;
		while (generator.getEnergy().getEnergyStored() < cap && guard-- > 0) {
			if (generator.getEnergy().receiveEnergy(ManaGeneratorBlockEntity.ENERGY_MAX_RECEIVE, false) == 0) {
				break;
			}
		}
	}

	private static ManaPool placePool(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, BotaniaBlocks.MANA_POOL.defaultBlockState());
		return (ManaPool) helper.getBlockEntity(pos);
	}

	@GameTest(template = ARENA)
	public void generatesManaFromEnergy(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		TestingUtil.assertEquals(0, generator.getCurrentMana(),
				() -> "A fresh generator should hold no mana");
		TestingUtil.assertEquals(0, generator.getEnergy().getEnergyStored(),
				() -> "A fresh generator should hold no energy");
		chargeFully(generator);

		helper.startSequence()
				.thenIdle(DRAIN_TICKS)
				.thenExecute(() -> TestingUtil.assertThat(generator.getCurrentMana() > 0,
						() -> "Generator drew energy but never produced mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void producesNothingWithoutEnergy(GameTestHelper helper) {
		var generator = placeGenerator(helper);

		helper.startSequence()
				.thenIdle(DRAIN_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(0, generator.getCurrentMana(),
						() -> "A generator with no energy should not produce mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void drainsEnergyWhileProducing(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		chargeFully(generator);
		int before = generator.getEnergy().getEnergyStored();
		TestingUtil.assertThat(before > 0, () -> "The charge did not take");

		helper.startSequence()
				.thenIdle(DRAIN_TICKS)
				.thenExecute(() -> TestingUtil.assertThat(generator.getEnergy().getEnergyStored() < before,
						() -> "Energy was not consumed while the generator was producing mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void lightsUpWhileItIsWorking(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		chargeFully(generator);
		TestingUtil.assertThat(!helper.getBlockState(GENERATOR).getValue(ManaGeneratorBlock.LIT),
				() -> "A generator that has not run yet should not be lit");

		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> TestingUtil.assertThat(
						helper.getBlockState(GENERATOR).getValue(ManaGeneratorBlock.LIT),
						() -> "A working generator should light up"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void handsManaToANeighbouringPool(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		var pool = placePool(helper, POOL);
		chargeFully(generator);

		helper.startSequence()
				.thenIdle(DRAIN_TICKS)
				.thenExecute(() -> TestingUtil.assertThat(pool.getCurrentMana() > 0,
						() -> "The pool beside the generator never received mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void ignoresAPoolOutOfReach(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		// Three blocks away, past the generator's radius of one.
		var pool = placePool(helper, POOL.east(2));
		chargeFully(generator);

		helper.startSequence()
				.thenIdle(DRAIN_TICKS)
				.thenExecute(() -> TestingUtil.assertThat(pool.getCurrentMana() == 0,
						() -> "A pool out of reach should not be fed mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void holdsManaWhenNoPoolIsNear(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		chargeFully(generator);

		helper.startSequence()
				.thenIdle(DRAIN_TICKS)
				.thenExecute(() -> TestingUtil.assertThat(generator.getCurrentMana() > 0,
						() -> "With nothing to send to, the generator should keep the mana"))
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 300)
	public void neverExceedsItsCapacity(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		chargeFully(generator);

		helper.startSequence()
				.thenIdle(200)
				.thenExecute(() -> TestingUtil.assertThat(
						generator.getCurrentMana() <= ManaGeneratorBlockEntity.MANA_CAPACITY,
						() -> "Generator produced more mana than its capacity: "
								+ generator.getCurrentMana()))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void overclockUpgradeRaisesOutput(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		int plain = generator.manaPerTick();
		generator.getUpgradeHandler().setStackInSlot(0, new ItemStack(ModItems.OVERCLOCK_1.get()));

		TestingUtil.assertThat(generator.manaPerTick() > plain,
				() -> "An overclock upgrade did not raise the per tick output: " + plain + " -> "
						+ generator.manaPerTick());
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void higherOverclockRaisesOutputFurther(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		generator.getUpgradeHandler().setStackInSlot(0, new ItemStack(ModItems.OVERCLOCK_1.get()));
		int one = generator.manaPerTick();
		generator.getUpgradeHandler().setStackInSlot(0, new ItemStack(ModItems.OVERCLOCK_3.get()));

		TestingUtil.assertThat(generator.manaPerTick() > one,
				() -> "Overclock 3 should beat overclock 1: " + one + " -> " + generator.manaPerTick());
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void efficiencyCoreLowersEnergyCost(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		int before = generator.energyPerMana();
		generator.getUpgradeHandler().setStackInSlot(1, new ItemStack(ModItems.EFFICIENCY_CORE.get()));

		TestingUtil.assertThat(generator.energyPerMana() < before,
				() -> "An efficiency core did not lower the energy per mana: " + before + " -> "
						+ generator.energyPerMana());
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void dispersionUpgradeWidensTheOutput(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		int before = generator.dispersionRadius();
		generator.getUpgradeHandler().setStackInSlot(2, new ItemStack(ModItems.DISPERSION_3.get()));

		TestingUtil.assertThat(generator.dispersionRadius() > before,
				() -> "A dispersion upgrade did not widen the output radius: " + before + " -> "
						+ generator.dispersionRadius());
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void dispersionReachesAPoolOutOfReach(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		var pool = placePool(helper, POOL.east(2));
		generator.getUpgradeHandler().setStackInSlot(2, new ItemStack(ModItems.DISPERSION_3.get()));
		chargeFully(generator);

		helper.startSequence()
				.thenIdle(DRAIN_TICKS)
				.thenExecute(() -> TestingUtil.assertThat(pool.getCurrentMana() > 0,
						() -> "Dispersion should reach a pool that plain output misses"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void sparkResonatorSwitchesToTheSparkPath(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		TestingUtil.assertThat(!generator.usesSpark(),
				() -> "A plain generator should hand mana over itself");
		generator.getUpgradeHandler().setStackInSlot(3, new ItemStack(ModItems.SPARK_RESONATOR.get()));

		TestingUtil.assertThat(generator.usesSpark(),
				() -> "A spark resonator should switch the generator onto the spark path");
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void flowerTierRaisesOutput(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		int plain = generator.manaPerTick();
		generator.getFlowerHandler().setStackInSlot(0, new ItemStack(BotaniaBlocks.HYDROANGEAS.asItem(), 4));

		TestingUtil.assertThat(generator.manaPerTick() > plain,
				() -> "A mystical flower did not raise the per tick output: " + plain + " -> "
						+ generator.manaPerTick());
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void betterFlowerRaisesOutputFurther(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		generator.getFlowerHandler().setStackInSlot(0, new ItemStack(BotaniaBlocks.HYDROANGEAS.asItem(), 4));
		int cheap = generator.manaPerTick();
		generator.getFlowerHandler().setStackInSlot(0, new ItemStack(BotaniaBlocks.DANDELIFEON.asItem(), 4));

		TestingUtil.assertThat(generator.manaPerTick() > cheap,
				() -> "Dandelifeon should beat Hydroangeas: " + cheap + " -> " + generator.manaPerTick());
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void dropsItsContentsWhenRemoved(GameTestHelper helper) {
		var generator = placeGenerator(helper);
		// Keep the item itself: dropping empties the stack it was handed.
		Item upgrade = ModItems.OVERCLOCK_1.get();
		generator.getUpgradeHandler().setStackInSlot(0, new ItemStack(upgrade));

		helper.startSequence()
				.thenExecute(() -> {
					helper.setBlock(GENERATOR, Blocks.AIR.defaultBlockState());
					helper.assertItemEntityPresent(upgrade, GENERATOR, 2.0);
				})
				.thenSucceed();
	}
}