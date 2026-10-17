package vazkii.botania.ae2.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

import vazkii.botania.ae2.machine.MachineCpuHooks;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;

/**
 * Crafting CPU: an ME machine takes a whole task in one push (see {@link MachineCpuHooks}).
 * AdvancedAE's quantum computer runs a copy of AE2's CPU logic with the same method and local names,
 * so one wrapper serves both. Checked against AE2 19.2.17 and AdvancedAE 1.6.12 (javap: one
 * {@code pushPattern} call, the captured locals in the LVT). IndustrialReforged wraps the same call for
 * its own machines: MixinExtras shares the local references between both wrappers, each changes them
 * only for its own provider.
 */
@Pseudo
@Mixin(targets = { "appeng.crafting.execution.CraftingCpuLogic", "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic" }, remap = false)
public abstract class CraftingCpuLogicMixin {
	@Shadow(remap = false)
	@Final
	private ListCraftingInventory inventory;

	@WrapOperation(
			method = "executeCrafting",
			at = @At(
					value = "INVOKE",
					target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"
			),
			require = 0
	)
	private boolean botaniaAe2$pushToMachine(
			ICraftingProvider provider,
			IPatternDetails details,
			KeyCounter[] inputs,
			Operation<Boolean> original,
			@Local(argsOnly = true) IEnergyService energy,
			@Local(name = "task") Map.Entry<IPatternDetails, ?> task,
			@Local(name = "expectedOutputs") KeyCounter expectedOutputs,
			@Local(name = "expectedContainerItems") KeyCounter expectedContainerItems,
			@Local(name = "patternPower") LocalDoubleRef patternPower,
			@Local(name = "pushedPatterns") LocalIntRef pushedPatterns) {
		boolean pushed = original.call(provider, details, inputs);
		if (!pushed || !MachineCpuHooks.isMachine(provider)) {
			return pushed;
		}

		// the CPU counts the push right after this call: cancel it out, ME machines cost no co-processor time
		pushedPatterns.set(pushedPatterns.get() - 1);
		TaskProgressAccessor progress = (TaskProgressAccessor) task.getValue();
		// AE2 takes this push off the task itself, so at most value - 1 more
		long extra = MachineCpuHooks.pushMore(provider, inputs, progress.botaniaAe2$getValue() - 1, inventory, energy,
				patternPower.get(), expectedOutputs, expectedContainerItems);
		if (extra > 0) {
			progress.botaniaAe2$setValue(progress.botaniaAe2$getValue() - extra);
			// drawn by AE2 after this call, together with the outputs and container items pushMore scaled
			patternPower.set(patternPower.get() * (extra + 1));
		}
		return pushed;
	}
}
