package org.moddingx.libx;

import net.minecraft.resources.ResourceLocation;
import net.lmor.botanicalextramachinery.core.BemNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * LibX instance class (Apache-2.0), adapted for BotaniaReforged.
 *
 * <p>Upstream LibX is its own {@code @Mod("libx")} extending {@link ModX}. Here LibX is
 * vendored as a library <em>inside</em> the botania mod container — Botanical Extra Machinery
 * registers through its own {@link org.moddingx.libx.mod.ModXRegistration} subclass, so a second
 * mod entry point would be wrong. Hence this is a singleton rather than a {@code ModX} subclass.
 *
 * <p>Because it is not a {@code ModX}, the members the vendored LibX code normally inherits from
 * {@code ModX} have to be provided here:
 * <ul>
 *     <li>{@link #logger} — upstream declares it directly on {@code LibX};</li>
 *     <li>{@link #modid} — upstream inherits it from {@code ModX}.</li>
 * </ul>
 * Both are referenced by the vendored config and datagen code; see for example
 * {@code config.ConfigManager} and {@code datagen.DatagenSystem}.
 */
public final class LibX {

    /** Logging channel for the vendored LibX code, including its config and datagen subsystems. */
    public static final Logger logger = LoggerFactory.getLogger("libx");

    /**
     * The "mod" namespace LibX registers its own content under.
     *
     * <p>Upstream this is {@code ModX#modid}, set from the {@code @Mod} annotation. Retained here
     * because {@code datagen.DatagenSystem} compares it against the active namespace to tell
     * LibX's own registries apart from a consuming mod's.
     */
    public final String modid = "libx";

    private static final LibX INSTANCE = new LibX();

    private LibX() {}

    public static LibX getInstance() {
        return INSTANCE;
    }

    public ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(this.modid, path);
    }

    /**
     * The block entity update network. Re-implemented by
     * {@link net.lmor.botanicalextramachinery.core.BemNetwork} rather than LibX's own
     * {@code impl.network.NetworkImpl}, so BEM packets live under the BEM namespace.
     */
    @Nonnull
    public static BemNetwork getNetwork() {
        return BemNetwork.INSTANCE;
    }
}
