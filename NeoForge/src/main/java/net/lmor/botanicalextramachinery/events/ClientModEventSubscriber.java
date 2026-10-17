package net.lmor.botanicalextramachinery.events;

import net.lmor.botanicalextramachinery.ExtraMachinery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Registers the Greenhouse's additional models.
 *
 * <p>Not {@code @EventBusSubscriber}: this addon runs inside the {@code botania} container, so FML's
 * per-mod-container annotation scan would never pick this up under the
 * {@code botanicalextramachinery} id. {@link ExtraMachinery} registers it on the mod bus directly.
 */
public class ClientModEventSubscriber {

    /** Hooks {@link #onModelRegister} onto the mod bus. Client side only. */
    public static void register(IEventBus modBus) {
        modBus.register(ClientModEventSubscriber.class);
    }

    @SubscribeEvent
    public static void onModelRegister(ModelEvent.RegisterAdditional event) {
        // 1.21.1: ModelEvent.RegisterAdditional.register takes ModelResourceLocation
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ExtraMachinery.MOD_ID, "block/greenhouse/pylons")));
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ExtraMachinery.MOD_ID, "block/greenhouse/circle_1")));
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ExtraMachinery.MOD_ID, "block/greenhouse/circle_2")));
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ExtraMachinery.MOD_ID, "block/greenhouse/circle_3")));
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ExtraMachinery.MOD_ID, "block/greenhouse/garden_bed")));
    }
}