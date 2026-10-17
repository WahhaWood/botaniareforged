package net.lmor.botanicalextramachinery.client;

import com.google.common.base.Suppliers;
import net.lmor.botanicalextramachinery.ModEntities;
import net.lmor.botanicalextramachinery.client.render.ColorHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.client.core.proxy.ClientProxy;
import vazkii.botania.client.integration.ears.EarsIntegration;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * NeoForge client-side initializer for Botanical Extra Machinery.
 * Registers entity renderers, item colors, and the Wand-of-the-Forest HUD
 * capability for the mana spark entities.
 *
 * <p>Deliberately <em>not</em> annotated {@code @EventBusSubscriber}: that annotation is resolved
 * per mod container by scanning that container's own classes, and this addon runs inside the
 * {@code botania} container rather than as {@code botanicalextramachinery}. FML would silently
 * never register these handlers. {@link net.lmor.botanicalextramachinery.ExtraMachinery} adds them
 * to the mod event bus by hand instead.
 */
public class ForgeClientInitializer {

    /**
     * Hooks the client-side handlers onto the mod event bus. Called from
     * {@link net.lmor.botanicalextramachinery.ExtraMachinery} on the client only, because the
     * handlers touch client-only classes.
     */
    public static void register(IEventBus modBus) {
        modBus.addListener(ForgeClientInitializer::clientInit);
        modBus.addListener(ForgeClientInitializer::registerEntityRenderers);
        modBus.addListener(ForgeClientInitializer::registerItemColors);
        modBus.addListener(ForgeClientInitializer::registerWandHudCapabilities);
        modBus.addListener(BemMenuScreens::register);
        vazkii.botania.ae2.client.AppliedBotanicsClient.register(modBus);
    }

    @SubscribeEvent
    public static void clientInit(FMLClientSetupEvent evt) {
        ClientProxy.initSeasonal();

        if (XplatAbstractions.INSTANCE.isModLoaded("ears")) {
            EarsIntegration.register();
        }
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        net.lmor.botanicalextramachinery.client.EntityRenderers.registerEntityRenderers(evt::registerEntityRenderer);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item evt) {
        ColorHandler.submitItems(evt::register);
    }

    private static final Supplier<Map<EntityType<?>, Function<Entity, WandHUD>>> ENTITY_WAND_HUD = Suppliers.memoize(() -> {
        IdentityHashMap<EntityType<?>, Function<Entity, WandHUD>> ret = new IdentityHashMap<>();
        ModEntities.registerWandHudCaps((factory, types) -> {
            for (EntityType<?> type : types) {
                ret.put(type, factory);
            }
        });
        return Collections.unmodifiableMap(ret);
    });

    /**
     * Register the WandHUD capability for all mana spark entity types.
     * Called from the NeoForge capability registration.
     */
    public static void registerWandHudCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent evt) {
        Map<EntityType<?>, Function<Entity, WandHUD>> hudMap = ENTITY_WAND_HUD.get();
        for (Map.Entry<EntityType<?>, Function<Entity, WandHUD>> entry : hudMap.entrySet()) {
            EntityType<?> type = entry.getKey();
            Function<Entity, WandHUD> factory = entry.getValue();
            evt.registerEntity(
                    vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities.getEntityApiLookupById(WandHUD.ENTITY_LOOKUP),
                    type,
                    (entity, context) -> factory.apply(entity)
            );
        }
    }
}
