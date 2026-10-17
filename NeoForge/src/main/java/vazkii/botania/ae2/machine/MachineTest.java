/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package vazkii.botania.ae2.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import vazkii.botania.ae2.ABItems;
import vazkii.botania.ae2.ABMachines;
import vazkii.botania.ae2.ae2.ManaKey;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import vazkii.botania.test.TestingUtil;

/**
 * Covers the recipe-providing ME machines: that each offers its Botania recipes as patterns, that a
 * pushed craft comes back as items in the network (with catalysts returned), that a real crafting CPU
 * hands crafts over in bulk instead of one per coprocessor, that a CPU job with catalysts closes, and
 * that the AE2 mixin applied.
 */
public class MachineTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos MACHINE = new BlockPos(2, 2, 2);
	private static final BlockPos ENERGY = new BlockPos(3, 2, 2);
	private static final BlockPos DRIVE = new BlockPos(2, 2, 1);
	private static final BlockPos CPU = new BlockPos(1, 2, 2);
	/** AE2 turns the network power on after 30 steady ticks. */
	private static final int ONLINE_TICKS = 45;

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	private static MachineBlockEntity place(GameTestHelper helper, MachineKind kind) {
		helper.setBlock(MACHINE, ABMachines.BLOCKS_BY_KIND.get(kind).get().defaultBlockState());
		return (MachineBlockEntity) helper.getBlockEntity(MACHINE);
	}

	/** Machine, creative energy, and a drive with an item, a mana and a fluid cell: a network by adjacency. */
	private static MachineBlockEntity placeNetwork(GameTestHelper helper, MachineKind kind) {
		MachineBlockEntity machine = place(helper, kind);
		helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
		helper.setBlock(DRIVE, AEBlocks.DRIVE.block().defaultBlockState());
		var drive = (DriveBlockEntity) helper.getBlockEntity(DRIVE);
		drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
		drive.getInternalInventory().setItemDirect(1, new ItemStack(ABItems.MANA_CELL_256K.get()));
		drive.getInternalInventory().setItemDirect(2, AEItems.FLUID_CELL_64K.stack());
		return machine;
	}

	private static IGrid grid(MachineBlockEntity machine) {
		IGrid grid = machine.getMainNode().getGrid();
		TestingUtil.assertThat(grid != null && machine.getMainNode().isActive(), () -> "The machine should be on a powered network");
		return grid;
	}

	private static MachineRecipe recipeFor(MachineBlockEntity machine, String output) {
		for (MachineRecipe recipe : machine.recipes().values()) {
			if (BuiltInRegistries.ITEM.getKey(recipe.icon().getItem()).toString().equals(output)) {
				return recipe;
			}
		}
		throw new AssertionError("No recipe making " + output + " among " + machine.recipes().size());
	}

	private static IPatternDetails patternOf(MachineBlockEntity machine, MachineRecipe recipe) {
		for (IPatternDetails pattern : machine.getAvailablePatterns()) {
			if (pattern instanceof MachinePattern ours && ours.recipeId().equals(recipe.id())) {
				return pattern;
			}
		}
		throw new AssertionError("Recipe " + recipe.id() + " is not offered as a pattern");
	}

	/** The inputs one push carries: the first possible item of each part, the exact cost of the rest. */
	private static KeyCounter[] inputsOf(IPatternDetails pattern) {
		var inputs = pattern.getInputs();
		KeyCounter[] holder = new KeyCounter[inputs.length];
		for (int i = 0; i < inputs.length; i++) {
			holder[i] = new KeyCounter();
			holder[i].add(inputs[i].getPossibleInputs()[0].what(), inputs[i].getMultiplier());
		}
		return holder;
	}

	private static long stored(IGrid grid, AEKey key) {
		return grid.getStorageService().getInventory().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
	}

	@GameTest(template = ARENA)
	public void everyKindOffersItsRecipesAsPatterns(GameTestHelper helper) {
		for (MachineKind kind : MachineKind.values()) {
			MachineBlockEntity machine = place(helper, kind);
			TestingUtil.assertThat(!machine.recipes().isEmpty(), () -> kind.id + " should find recipes of its type");
			long plain = machine.recipes().values().stream().filter(recipe -> recipe.catalyst() == null).count();
			TestingUtil.assertEquals(plain, (long) machine.getAvailablePatterns().size(),
					() -> kind.id + " should offer every recipe that needs no catalyst block");

			for (IPatternDetails details : machine.getAvailablePatterns()) {
				MachinePattern pattern = (MachinePattern) details;
				TestingUtil.assertThat(!pattern.getOutputs().isEmpty() && pattern.getInputs().length > 0,
						() -> pattern.recipeId() + " needs inputs and outputs");
				// crafting CPUs save a job by its definition only: the decoder has to rebuild the same pattern
				IPatternDetails decoded = new MachinePattern.Decoder().decodePattern(pattern.getDefinition(), helper.getLevel());
				TestingUtil.assertEquals(pattern, decoded, () -> pattern.recipeId() + " should survive a decode round trip");
			}
		}
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void costsAreInputsOfThePattern(GameTestHelper helper) {
		var infuser = place(helper, MachineKind.MANA_INFUSER);
		var diamond = patternOf(infuser, recipeFor(infuser, "botania:mana_diamond"));
		GenericStack lastInput = diamond.getInputs()[diamond.getInputs().length - 1].getPossibleInputs()[0];
		TestingUtil.assertEquals(ManaKey.KEY, lastInput.what(), () -> "The last input of an infusion should be mana");
		TestingUtil.assertEquals(10000L, diamond.getInputs()[diamond.getInputs().length - 1].getMultiplier(),
				() -> "The mana diamond infusion costs 10000 mana");

		var apothecary = place(helper, MachineKind.APOTHECARY);
		var anyPattern = apothecary.getAvailablePatterns().get(0);
		var water = anyPattern.getInputs()[anyPattern.getInputs().length - 1];
		TestingUtil.assertThat(water.getPossibleInputs()[0].what() instanceof AEFluidKey fluid && fluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER,
				() -> "An apothecary craft should take water");
		TestingUtil.assertEquals(1000L, water.getMultiplier(), () -> "An apothecary craft should take a bucket of water");
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void catalystBlockUnlocksItsRecipes(GameTestHelper helper) {
		var infuser = place(helper, MachineKind.MANA_INFUSER);
		int before = infuser.getAvailablePatterns().size();
		TestingUtil.assertThat(infuser.recipes().size() > before, () -> "Some infusion recipes should need a catalyst block");

		helper.setBlock(MACHINE.below(), block("botania:alchemy_catalyst").defaultBlockState());
		helper.setBlock(MACHINE, ABMachines.BLOCKS_BY_KIND.get(MachineKind.MANA_INFUSER).get().defaultBlockState());
		var placed = (MachineBlockEntity) helper.getBlockEntity(MACHINE);
		TestingUtil.assertThat(placed.getAvailablePatterns().size() > before,
				() -> "An alchemy catalyst under the infuser should add the alchemy recipes");
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void pushedCraftsEndUpInTheNetwork(GameTestHelper helper) {
		var machine = placeNetwork(helper, MachineKind.MANA_INFUSER);
		helper.startSequence()
				.thenIdle(ONLINE_TICKS)
				.thenExecute(() -> {
					IGrid grid = grid(machine);
					var pattern = patternOf(machine, recipeFor(machine, "botania:mana_diamond"));
					for (int i = 0; i < 100; i++) {
						TestingUtil.assertThat(machine.pushPattern(pattern, inputsOf(pattern)), () -> "The machine should take craft " + pattern);
					}
					TestingUtil.assertEquals(100L, machine.queuedOperations(), () -> "All 100 crafts should be queued");
				})
				.thenIdle(5)
				.thenExecute(() -> {
					IGrid grid = grid(machine);
					TestingUtil.assertEquals(100L, stored(grid, AEItemKey.of(item("botania:mana_diamond"))),
							() -> "100 crafts should leave 100 mana diamonds in the network");
					TestingUtil.assertEquals(0L, machine.queuedOperations(), () -> "The queue should be empty");
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA)
	public void runicAltarReturnsItsCatalysts(GameTestHelper helper) {
		var machine = placeNetwork(helper, MachineKind.RUNIC_ALTAR);
		MachineRecipe withCatalyst = machine.recipes().values().stream()
				.filter(recipe -> recipe.parts().stream().anyMatch(MachineRecipe.Part::returned))
				.findFirst().orElseThrow(() -> new AssertionError("No runic altar recipe with a catalyst"));
		helper.startSequence()
				.thenIdle(ONLINE_TICKS)
				.thenExecute(() -> {
					var pattern = patternOf(machine, withCatalyst);
					TestingUtil.assertThat(machine.pushPattern(pattern, inputsOf(pattern)), () -> "The altar should take the craft");
				})
				.thenIdle(5)
				.thenExecute(() -> {
					IGrid grid = grid(machine);
					List<String> missing = new ArrayList<>();
					for (MachineRecipe.Part part : withCatalyst.parts()) {
						if (part.returned()) {
							AEItemKey key = AEItemKey.of(part.ingredient().getItems()[0]);
							if (stored(grid, key) < part.count()) {
								missing.add(key.toString());
							}
						}
					}
					TestingUtil.assertThat(missing.isEmpty(), () -> "Catalysts should come back to the network: " + missing);
					var output = withCatalyst.outputs().get(0);
					TestingUtil.assertEquals((long) output.getCount(), stored(grid, AEItemKey.of(output)),
							() -> "The craft should give its result");
				})
				.thenSucceed();
	}

	/** One CPU with no coprocessor, 200 crafts: AE2 alone needs over 200 ticks, bulk handover a handful. */
	@GameTest(template = ARENA, timeoutTicks = 220)
	public void craftingCpuHandsCraftsOverInBulk(GameTestHelper helper) {
		helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
		bulkCraft(helper);
	}

	/**
	 * The same through AdvancedAE's quantum computer, which runs its own copy of AE2's CPU logic; the mixin
	 * targets both. Passes without running when AdvancedAE is not installed.
	 */
	@GameTest(template = ARENA, timeoutTicks = 220)
	public void quantumComputerHandsCraftsOverInBulk(GameTestHelper helper) {
		if (!ModList.get().isLoaded("advanced_ae")) {
			helper.succeed();
			return;
		}
		assertHandlerIn(AE2_QUANTUM_CPU);
		// a 3x3x3 shell of frame blocks with the core in the middle, on top of the machine
		for (int x = 1; x <= 3; x++) {
			for (int y = 3; y <= 5; y++) {
				for (int z = 1; z <= 3; z++) {
					boolean centre = x == 2 && y == 4 && z == 2;
					helper.setBlock(new BlockPos(x, y, z), block(centre ? "advanced_ae:quantum_core" : "advanced_ae:quantum_structure").defaultBlockState());
				}
			}
		}
		bulkCraft(helper);
	}

	/** 200 mana diamonds through whatever crafting CPU the test placed. */
	private static void bulkCraft(GameTestHelper helper) {
		var machine = placeNetwork(helper, MachineKind.MANA_INFUSER);
		AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
		AEItemKey result = AEItemKey.of(item("botania:mana_diamond"));
		Future<?>[] calculation = new Future<?>[1];

		helper.startSequence()
				.thenIdle(ONLINE_TICKS)
				.thenExecute(() -> {
					IGrid grid = grid(machine);
					var storage = grid.getStorageService().getInventory();
					TestingUtil.assertEquals(200L, storage.insert(diamond, 200, Actionable.MODULATE, IActionSource.empty()), () -> "Diamonds should fit the cell");
					TestingUtil.assertEquals(2_000_000L, storage.insert(ManaKey.KEY, 2_000_000, Actionable.MODULATE, IActionSource.empty()),
							() -> "Mana should fit the mana cell");
					TestingUtil.assertEquals(1, grid.getCraftingService().getCpus().size(), () -> "There should be one crafting CPU");
				})
				.thenWaitUntil(() -> TestingUtil.assertThat(grid(machine).getCraftingService().isCraftable(result), () -> "Waiting for the patterns"))
				.thenExecute(() -> calculation[0] = grid(machine).getCraftingService()
						.beginCraftingCalculation(helper.getLevel(), requester(machine), result, 200, CalculationStrategy.REPORT_MISSING_ITEMS))
				.thenWaitUntil(() -> TestingUtil.assertThat(calculation[0].isDone(), () -> "Waiting for the crafting plan"))
				.thenExecute(() -> submit(machine, calculation[0]))
				.thenWaitUntil(() -> TestingUtil.assertEquals(200L, stored(grid(machine), result), () -> "Waiting for the 200 mana diamonds"))
				.thenWaitUntil(() -> TestingUtil.assertEquals(0L, stored(grid(machine), diamond), () -> "Every diamond should be used, none duplicated"))
				.thenSucceed();
	}

	/**
	 * A recipe with catalysts through a CPU: AE2 plans one rune set and reuses it, so every craft waits for the
	 * runes to come back. The job has to finish, with every result made and the runes back in the network.
	 */
	@GameTest(template = ARENA, timeoutTicks = 300)
	public void craftingCpuRunsCatalystRecipes(GameTestHelper helper) {
		var machine = placeNetwork(helper, MachineKind.RUNIC_ALTAR);
		helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
		MachineRecipe recipe = machine.recipes().values().stream()
				.filter(r -> r.parts().stream().anyMatch(MachineRecipe.Part::returned))
				.findFirst().orElseThrow(() -> new AssertionError("No runic altar recipe with a catalyst"));
		AEItemKey result = AEItemKey.of(recipe.outputs().get(0));
		long crafts = 10;
		long made = crafts * recipe.outputs().get(0).getCount();
		Future<?>[] calculation = new Future<?>[1];
		helper.startSequence()
				.thenIdle(ONLINE_TICKS)
				.thenExecute(() -> {
					var storage = grid(machine).getStorageService().getInventory();
					for (MachineRecipe.Part part : recipe.parts()) {
						// catalysts once: the plan reuses them; everything else for every craft
						long amount = part.returned() ? part.count() : part.count() * crafts;
						storage.insert(AEItemKey.of(part.ingredient().getItems()[0]), amount, Actionable.MODULATE, IActionSource.empty());
					}
					for (GenericStack cost : recipe.costs()) {
						storage.insert(cost.what(), cost.amount() * crafts, Actionable.MODULATE, IActionSource.empty());
					}
				})
				.thenWaitUntil(() -> TestingUtil.assertThat(grid(machine).getCraftingService().isCraftable(result), () -> "Waiting for the patterns"))
				.thenExecute(() -> calculation[0] = grid(machine).getCraftingService()
						.beginCraftingCalculation(helper.getLevel(), requester(machine), result, made, CalculationStrategy.REPORT_MISSING_ITEMS))
				.thenWaitUntil(() -> TestingUtil.assertThat(calculation[0].isDone(), () -> "Waiting for the crafting plan"))
				.thenExecute(() -> submit(machine, calculation[0]))
				.thenWaitUntil(() -> TestingUtil.assertEquals(made, stored(grid(machine), result), () -> "Waiting for " + made + " " + result))
				.thenWaitUntil(() -> TestingUtil.assertThat(grid(machine).getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
						() -> "The CPU should close the job: every catalyst it waited for came back"))
				// the last runes come back with the last result, while the CPU is closing the job inside that very insert:
				// AE2 refuses the nested insert, and the CPU stores them on its next tick
				.thenWaitUntil(() -> {
					List<String> missing = new ArrayList<>();
					for (MachineRecipe.Part part : recipe.parts()) {
						AEItemKey key = AEItemKey.of(part.ingredient().getItems()[0]);
						if (part.returned() && stored(grid(machine), key) < part.count()) {
							missing.add(key.toString());
						}
					}
					TestingUtil.assertThat(missing.isEmpty(), () -> "Catalysts should be back in the network: " + missing);
				})
				.thenSucceed();
	}

	/** Plans are calculated for the machine's own node: the default requester is no grid node and sees no pattern. */
	private static ICraftingSimulationRequester requester(MachineBlockEntity machine) {
		return new ICraftingSimulationRequester() {
			@Override
			public IActionSource getActionSource() {
				return IActionSource.empty();
			}

			@Override
			public appeng.api.networking.IGridNode getGridNode() {
				return machine.getMainNode().getNode();
			}
		};
	}

	private static void submit(MachineBlockEntity machine, Future<?> calculation) {
		try {
			ICraftingPlan plan = (ICraftingPlan) calculation.get();
			var submit = grid(machine).getCraftingService().submitJob(plan, null, null, false, IActionSource.empty());
			TestingUtil.assertThat(submit.successful(), () -> "The job should be accepted: " + submit.errorCode() + " missing=" + plan.missingItems());
		} catch (InterruptedException | java.util.concurrent.ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(template = ARENA)
	public void cpuMixinApplied(GameTestHelper helper) {
		assertHandlerIn("appeng.crafting.execution.CraftingCpuLogic");
		helper.succeed();
	}

	private static final String AE2_QUANTUM_CPU = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic";

	/** The bulk handover wrapper landed in this CPU logic class (with {@code require = 0} a miss would be silent). */
	private static void assertHandlerIn(String className) {
		List<String> found = new ArrayList<>();
		try {
			for (Method method : Class.forName(className).getDeclaredMethods()) {
				if (method.getName().contains("botaniaAe2$pushToMachine")) {
					found.add(method.getName());
				}
			}
		} catch (ClassNotFoundException e) {
			throw new AssertionError(className + " is not loaded", e);
		}
		TestingUtil.assertThat(!found.isEmpty(), () -> "The CPU mixin handler should be applied to " + className);
	}
}
