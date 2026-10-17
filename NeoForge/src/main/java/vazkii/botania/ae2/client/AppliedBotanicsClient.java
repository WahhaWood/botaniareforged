package vazkii.botania.ae2.client;

import vazkii.botania.ae2.ABItems;
import vazkii.botania.ae2.machine.MachineRecipe;
import appeng.items.storage.BasicStorageCell;
import appeng.items.tools.powered.PortableCellItem;
import net.minecraft.client.color.item.ItemColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.util.FastColor.ARGB32;

/**
 * Applied Botanics client setup.
 *
 * <p>Upstream this was a second client-only mod entry point. Here it is called explicitly from
 * {@code net.lmor.botanicalextramachinery.client.ForgeClientInitializer#register}, which already
 * runs on the client only.
 */
public final class AppliedBotanicsClient {

    private AppliedBotanicsClient() {}

    public static void register(IEventBus bus) {
        bus.addListener(AppliedBotanicsClient::registerItemColors);
        ManaRenderer.initialize(bus);
        // the client builds the recipe list of the ME machine GUI from synced recipes
        NeoForge.EVENT_BUS.addListener((RecipesUpdatedEvent event) -> MachineRecipe.invalidate());
    }

    private static void registerItemColors(Item event) {
        ItemColor cells = (stack, tintIndex) -> ARGB32.opaque(BasicStorageCell.getColor(stack, tintIndex));
        ItemColor portableCells = (stack, tintIndex) -> ARGB32.opaque(PortableCellItem.getColor(stack, tintIndex));

        for (ABItems.Tier tier : ABItems.Tier.values()) {
            event.register(cells, ABItems.get(tier)::get);
            event.register(portableCells, ABItems.getPortableCell(tier)::get);
        }
    }
}