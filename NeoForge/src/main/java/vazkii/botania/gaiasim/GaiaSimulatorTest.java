package vazkii.botania.gaiasim;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

import vazkii.botania.api.item.Relic;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.lib.BotaniaTags;
import vazkii.botania.test.TestingUtil;
import vazkii.botania.xplat.BotaniaConfig;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Covers the Gaia Simulator: the multiblock gates it, mana starts a fight, energy drives it, and a finished fight
 * leaves the hard-mode reward of a lone winner in the output.
 * <p>
 * The controller re-checks its structure once a second, so every check first idles past that.
 */
public class GaiaSimulatorTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos CONTROLLER = new BlockPos(4, 2, 4);
	/** Long enough for a structure check and a few ticks of work after it. */
	private static final int SETTLE_TICKS = 30;

	private static void buildStructure(GameTestHelper helper) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				boolean corner = x != 0 && z != 0;
				helper.setBlock(CONTROLLER.offset(x, -1, z), x == 0 && z == 0 ? BotaniaBlocks.DRAGONSTONE_BLOCK
						: corner ? BotaniaBlocks.TERRASTEEL_BLOCK : BotaniaBlocks.ELEMENTIUM_BLOCK);
				if (corner) {
					helper.setBlock(CONTROLLER.offset(x, 0, z), BotaniaBlocks.GAIA_PYLON);
				}
			}
		}
	}

	private static GaiaSimulatorBlockEntity placeController(GameTestHelper helper) {
		helper.setBlock(CONTROLLER, GaiaSimulator.BLOCK.get());
		return TestingUtil.assertBlockEntity(helper, CONTROLLER, GaiaSimulator.BLOCK_ENTITY.get());
	}

	private static int count(GaiaSimulatorBlockEntity simulator, Predicate<ItemStack> filter) {
		int total = 0;
		for (int slot = 0; slot < GaiaSimulatorBlockEntity.OUTPUT_SLOTS; slot++) {
			ItemStack stack = simulator.getOutput().getStackInSlot(slot);
			if (filter.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	@GameTest(template = ARENA)
	public void idlesWithoutStructure(GameTestHelper helper) {
		var simulator = placeController(helper);
		simulator.receiveMana(GaiaSimulatorBlockEntity.MANA_PER_FIGHT);
		simulator.getEnergy().receiveEnergy(GaiaSimulatorBlockEntity.ENERGY_CAPACITY, false);

		helper.startSequence()
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(GaiaSimulatorBlockEntity.Status.NO_STRUCTURE, simulator.getStatus());
					TestingUtil.assertEquals(GaiaSimulatorBlockEntity.MANA_PER_FIGHT, simulator.getCurrentMana(),
							() -> "Mana was spent without the multiblock");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void missingPylonBreaksStructure(GameTestHelper helper) {
		buildStructure(helper);
		helper.setBlock(CONTROLLER.offset(1, 0, 1), BotaniaBlocks.MANA_PYLON);
		var simulator = placeController(helper);
		simulator.receiveMana(GaiaSimulatorBlockEntity.MANA_PER_FIGHT);

		helper.startSequence()
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> TestingUtil.assertEquals(GaiaSimulatorBlockEntity.Status.NO_STRUCTURE, simulator.getStatus(),
						() -> "A Mana Pylon in place of a Gaia Pylon still counted as formed"))
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void startsFightWithManaAndEnergy(GameTestHelper helper) {
		buildStructure(helper);
		var simulator = placeController(helper);
		simulator.receiveMana(GaiaSimulatorBlockEntity.MANA_PER_FIGHT);
		simulator.getEnergy().receiveEnergy(GaiaSimulatorBlockEntity.ENERGY_CAPACITY, false);

		helper.startSequence()
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(GaiaSimulatorBlockEntity.Status.FIGHTING, simulator.getStatus());
					TestingUtil.assertEquals(0, simulator.getCurrentMana(), () -> "Starting a fight did not spend the mana");
					TestingUtil.assertThat(simulator.getProgress() > 0, () -> "The fight did not advance");
					TestingUtil.assertThat(simulator.getEnergy().getEnergyStored() < GaiaSimulatorBlockEntity.ENERGY_CAPACITY,
							() -> "The fight did not draw energy");
					TestingUtil.assertThat(helper.getBlockState(CONTROLLER).getValue(GaiaSimulatorBlock.LIT),
							() -> "The controller is not lit while fighting");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void pausesWithoutEnergy(GameTestHelper helper) {
		buildStructure(helper);
		var simulator = placeController(helper);
		simulator.receiveMana(GaiaSimulatorBlockEntity.MANA_PER_FIGHT);

		helper.startSequence()
				.thenIdle(SETTLE_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(GaiaSimulatorBlockEntity.Status.NO_ENERGY, simulator.getStatus());
					TestingUtil.assertThat(simulator.isFighting(), () -> "Mana was there but no fight started");
					TestingUtil.assertEquals(0, simulator.getProgress(), () -> "The fight advanced without energy");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = GaiaSimulatorBlockEntity.FIGHT_TICKS + 200)
	public void finishedFightGivesHardModeLoot(GameTestHelper helper) {
		buildStructure(helper);
		var simulator = placeController(helper);
		UUID owner = UUID.randomUUID();
		simulator.setOwner(owner);
		simulator.receiveMana(GaiaSimulatorBlockEntity.MANA_PER_FIGHT);
		helper.onEachTick(() -> simulator.getEnergy().receiveEnergy(GaiaSimulatorBlockEntity.ENERGY_CAPACITY, false));

		helper.succeedWhen(() -> {
			TestingUtil.assertThat(count(simulator, stack -> stack.is(BotaniaItems.GAIA_SPIRIT)) > 0, () -> "No Gaia Spirit yet");
			// a lone winner is also the true killer, who gets 16 instead of 10
			TestingUtil.assertEquals(16, count(simulator, stack -> stack.is(BotaniaItems.GAIA_SPIRIT)), () -> "Wrong number of Gaia Spirits");
			// the hard-mode reward always holds one Ancient Will; the plain reward has none
			TestingUtil.assertEquals(1, count(simulator, stack -> stack.is(BotaniaTags.Items.ANCIENT_WILLS)),
					() -> "No Ancient Will, so this was not the hard-mode reward");
			TestingUtil.assertThat(!simulator.isFighting(), () -> "A second fight started without mana");
			if (BotaniaConfig.common().relicsEnabled()) {
				TestingUtil.assertEquals(1, count(simulator, stack -> stack.is(BotaniaItems.DICE_OF_FATE)), () -> "No Dice of Fate");
				for (int slot = 0; slot < GaiaSimulatorBlockEntity.OUTPUT_SLOTS; slot++) {
					ItemStack stack = simulator.getOutput().getStackInSlot(slot);
					if (stack.is(BotaniaItems.DICE_OF_FATE)) {
						TestingUtil.assertEquals(owner, Relic.LOOKUP.find(stack).getSoulbindUUID(),
								() -> "The Dice of Fate is not bound to the owner");
					}
				}
			}
		});
	}
}
