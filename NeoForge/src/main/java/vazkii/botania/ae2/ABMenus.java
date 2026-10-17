package vazkii.botania.ae2;

import vazkii.botania.ae2.machine.MachineScreen;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.init.client.InitScreens;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ABMenus {
   public static final MenuType<MEStorageMenu> PORTABLE_MANA_CELL_TYPE = MenuTypeBuilder.create(MEStorageMenu::new, IPortableTerminal.class)
      .build(AppliedBotanics.id("portable_mana_cell"));

   public static void initialize(IEventBus bus) {
      bus.addListener((RegisterMenuScreensEvent event) -> InitScreens.register(
         event,
         PORTABLE_MANA_CELL_TYPE,
         (MEStorageMenu menu, Inventory inv, Component title, ScreenStyle style) -> new MEStorageScreen<>(menu, inv, title, style),
         "/screens/terminals/portable_mana_cell.json"
      ));
      bus.addListener((RegisterMenuScreensEvent event) -> event.register(ABMachines.MACHINE_MENU.get(), MachineScreen::new));
   }
}
