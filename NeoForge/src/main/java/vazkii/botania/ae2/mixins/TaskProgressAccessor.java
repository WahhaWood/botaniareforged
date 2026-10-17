package vazkii.botania.ae2.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Crafts left of one task of a crafting CPU job (AE2, and AdvancedAE's copy of it). */
@Pseudo
@Mixin(
		targets = { "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress" },
		remap = false
)
public interface TaskProgressAccessor {
	@Accessor(value = "value", remap = false)
	long botaniaAe2$getValue();

	@Accessor(value = "value", remap = false)
	void botaniaAe2$setValue(long value);
}
