package com.jykito.voltany.registry;

import com.jykito.voltany.VoltanyInit;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTab {
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister
			.create(Registries.CREATIVE_MODE_TAB, VoltanyInit.MODID);

	public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.voltany"))
			.icon(() -> new ItemStack(ModItems.MANA_GENERATOR.get()))
			.displayItems((params, output) -> {
				output.accept(ModItems.MANA_GENERATOR.get());
				output.accept(ModItems.OVERCLOCK_1.get());
				output.accept(ModItems.OVERCLOCK_2.get());
				output.accept(ModItems.OVERCLOCK_3.get());
				output.accept(ModItems.DISPERSION_1.get());
				output.accept(ModItems.DISPERSION_2.get());
				output.accept(ModItems.DISPERSION_3.get());
				output.accept(ModItems.EFFICIENCY_CORE.get());
				output.accept(ModItems.SPARK_RESONATOR.get());
			})
			.build());

	private ModCreativeTab() {}
}
