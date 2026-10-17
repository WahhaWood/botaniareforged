package net.lmor.botanicalextramachinery;

import net.lmor.botanicalextramachinery.config.BemConfigLoader;
import net.lmor.botanicalextramachinery.core.BemRegistration;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.moddingx.libx.registration.RegistrationBuilder;
import org.moddingx.libx.mod.ModXRegistration;

import javax.annotation.Nonnull;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Botanical Extra Machinery Reforged (by LMor, nexxx_7; extra fixes by the
 * BOLTMAGIC fork), merged into BotaniaReforged. The original LibX-based mod
 * entry point is reworked: the mod id stays "botanicalextramachinery" (all
 * registries and assets keep that namespace) while the code runs inside the
 * botania mod container. Datagen was dropped — resources ship pre-built.
 */
public final class ExtraMachinery extends ModXRegistration {
    private static ExtraMachinery instance;
    private static ExtraMachineryTab tab;
    public static final String MOD_ID = "botanicalextramachinery";

    public ExtraMachinery(IEventBus modBus, ModContainer modContainer) {
        super(MOD_ID, modBus);
        instance = this;
        tab = new ExtraMachineryTab(this);

        // Deferred to the registry phase: constructing EntityType allocates an intrusive holder in
        // BuiltInRegistries.ENTITY_TYPE, which is already frozen by the time mod constructors run.
        // Botania does the same thing from its own RegisterEvent handler.
        this.addRegistrationHandler(() -> {
            bind(Registries.ENTITY_TYPE, ModEntities::registerEntities);
            BemRegistration.registerAll(this);
        });
        modBus.addListener(BemRegistration::registerCapabilities);
        // These touch client-only classes (ModelResourceLocation, ClientProxy, EntityRenderer), so
        // they must not be class-loaded on a dedicated server. @EventBusSubscriber would not work
        // either: this addon lives inside the botania container, not under its own mod id.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            net.lmor.botanicalextramachinery.client.ForgeClientInitializer.register(modBus);
            net.lmor.botanicalextramachinery.events.ClientModEventSubscriber.register(modBus);
        }
        net.lmor.botanicalextramachinery.events.EventListener.register();
        net.lmor.botanicalextramachinery.events.TooltipHandler.register();
        BemConfigLoader.register(modContainer,
                BemConfigLoader.ConfigClass.of("botanicalextramachinery-client.toml", net.lmor.botanicalextramachinery.config.LibXClientConfig.class),
                BemConfigLoader.ConfigClass.of("botanicalextramachinery-server.toml", net.lmor.botanicalextramachinery.config.LibXServerConfig.class));
    }

    private <T> void bind(ResourceKey<Registry<T>> registry, Consumer<BiConsumer<T, ResourceLocation>> source) {
        source.accept((t, rl) -> this.register(registry, rl.getPath(), t));
    }

    @Nonnull
    public static ExtraMachinery getInstance() { return instance; }

    public static ExtraMachineryTab getTab() { return tab; }

    @Override
    protected void initRegistration(RegistrationBuilder builder) {}

    @Override
    protected void setup(FMLCommonSetupEvent fmlCommonSetupEvent) {}

    @Override
    protected void clientSetup(FMLClientSetupEvent event) {}

}
