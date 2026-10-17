package de.melanx.botanicalmachinery.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Re-created Botanical Machinery tag constant (Apache-2.0) for
 * BotaniaReforged. The tag JSON ships in our own data so the apothecary
 * catalyst slots work without Botanical Machinery installed.
 */
public class CommonTags {

    public static final TagKey<Item> MECHANICAL_APOTHECARY_CATALYSTS = TagKey.create(Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("botanicalextramachinery", "mechanical_apothecary_catalysts"));
}
