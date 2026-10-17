package org.moddingx.libx.mod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import org.moddingx.libx.base.tile.BlockBE;
import org.moddingx.libx.impl.registration.RegistrationDispatcher;
import org.moddingx.libx.registration.Registerable;
import org.moddingx.libx.registration.RegistrationBuilder;

import javax.annotation.Nullable;

/**
 * Vendored from LibX 1.21.1 (Apache-2.0), reworked for BotaniaReforged:
 * the mod event bus is passed in explicitly (merged-mod entry point) and no
 * annotation processor generated code is invoked. Registration handlers are
 * added manually from the mod class constructor.
 */
public abstract class ModXRegistration extends ModX {

    private final RegistrationDispatcher dispatcher;

    protected ModXRegistration(String modid, IEventBus modBus) {
        super(modid, modBus);
        RegistrationBuilder builder = new RegistrationBuilder();
        this.initRegistration(builder);
        this.dispatcher = new RegistrationDispatcher(this, builder.build());
        this.dispatcher.setupEventListeners(modBus);
    }

    protected void initRegistration(RegistrationBuilder builder) {
        //
    }

    /**
     * Adds a registration handler that runs during the registry phase.
     */
    public final void addRegistrationHandler(Runnable handler) {
        this.dispatcher.addRegistrationHandler(handler);
    }

    /**
     * Registers an object to a given registry using a given name as the path part of the objects id.
     * The {@code registry} parameter may be {@code null} to allow registering
     * {@link Registerable registerables} that shouldn't go into any specific registry.
     */
    public final <T> void register(@Nullable ResourceKey<? extends Registry<T>> registry, String id, T value) {
        this.dispatcher.register(registry, id, value);
    }
}
