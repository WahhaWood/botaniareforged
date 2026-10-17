package vazkii.botania.ae2.machine;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;

/**
 * Called from {@code CraftingCpuLogicMixin}: a crafting CPU hands an ME machine a whole task in one push.
 *
 * <p>AE2 pushes a pattern one craft at a time, and every craft is a full pass: inputs picked from the CPU
 * (tag substitutes searched one by one), network power checked and drawn, the expected outputs noted.
 * At {@value MachineBlockEntity#OPERATIONS_PER_TICK} crafts per tick for each task that cost the server
 * milliseconds a tick. Here AE2 makes its usual first push, then the machine gets the rest of the task
 * (as far as its queue, the CPU's stock and the network's power go) in one block, and AE2 books it with
 * its own code for that push, scaled. Pushes into an ME machine do not use up co-processor operations.
 */
public final class MachineCpuHooks {
	private MachineCpuHooks() {}

	public static boolean isMachine(ICraftingProvider provider) {
		return provider instanceof MachineBlockEntity;
	}

	/**
	 * After AE2 pushed one craft into the ME machine {@code provider}: moves up to {@code limit} more copies
	 * of the same craft (same input keys, mana and water included) from the CPU inventory into the machine.
	 * {@code expectedOutputs} and {@code expectedContainerItems} hold what AE2 awaits from that one craft
	 * (results; returned catalysts and container leftovers); they are scaled to cover the extra crafts, and
	 * AE2 books them right after the push. The caller scales the pattern power and takes the count off the task.
	 *
	 * @return how many more crafts the machine took, 0..limit
	 */
	public static long pushMore(ICraftingProvider provider, KeyCounter[] inputs, long limit, ListCraftingInventory cpu,
			IEnergyService energy, double patternPower, KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
		if (!(provider instanceof MachineBlockEntity machine) || limit <= 0) {
			return 0;
		}

		long extra = Math.min(limit, machine.freeCapacity());
		KeyCounter perCraft = new KeyCounter();
		for (KeyCounter slot : inputs) {
			perCraft.addAll(slot);
		}
		for (var input : perCraft) {
			if (input.getLongValue() > 0) {
				extra = Math.min(extra, cpu.extract(input.getKey(), Long.MAX_VALUE, Actionable.SIMULATE) / input.getLongValue());
			}
		}

		if (patternPower > 0 && extra > 0) {
			// AE2 checked the power of one craft and draws it after the push: this one and the extra ones must fit
			double available = energy.extractAEPower(patternPower * (extra + 1), Actionable.SIMULATE, PowerMultiplier.CONFIG);
			extra = Math.min(extra, (long) Math.floor(available / patternPower + 1.0E-9) - 1);
		}
		if (extra <= 0) {
			return 0;
		}

		for (var input : perCraft) {
			cpu.extract(input.getKey(), input.getLongValue() * extra, Actionable.MODULATE);
		}
		scale(expectedOutputs, extra);
		scale(expectedContainerItems, extra);
		machine.acceptMore(extra);
		return extra;
	}

	/** Adds {@code extra} more times what one craft put in {@code counter}. */
	private static void scale(KeyCounter counter, long extra) {
		KeyCounter one = new KeyCounter();
		one.addAll(counter);
		for (var entry : one) {
			counter.add(entry.getKey(), entry.getLongValue() * extra);
		}
	}
}
