package com.jykito.voltany.registry;

import com.jykito.voltany.VoltanyInit;
import com.jykito.voltany.menu.ManaGeneratorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenus {
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, VoltanyInit.MODID);

	public static final Supplier<MenuType<ManaGeneratorMenu>> MANA_GENERATOR = MENUS.register("mana_generator",
			() -> IMenuTypeExtension.create((id, inv, buf) -> new ManaGeneratorMenu(id, inv, buf.readBlockPos())));

	private ModMenus() {}
}
