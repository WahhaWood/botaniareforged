package org.moddingx.libx.mod;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Vendored from LibX 1.21.1 (Apache-2.0), reworked for BotaniaReforged:
 * merged mods run inside the botania mod container, so the mod id is
 * passed explicitly instead of being derived from a {@code @Mod} annotation
 * and the active container.
 */
public abstract class ModX {

    public final String modid;
    /**
     * The mod event bus of the container this merged mod runs in.
     */
    public final IEventBus modEventBus;

    protected ModX(String modid, IEventBus modEventBus) {
        this.modid = modid;
        this.modEventBus = modEventBus;
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    }

    protected abstract void setup(FMLCommonSetupEvent event);

    protected abstract void clientSetup(FMLClientSetupEvent event);

    public final ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(this.modid, path);
    }
}
