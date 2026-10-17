package net.lmor.botanicalextramachinery.Items.render;

import net.lmor.botanicalextramachinery.client.ModModels;
import net.lmor.botanicalextramachinery.entities.manaSpark.EntityMazarineManaSpark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import vazkii.botania.client.render.entity.BaseSparkRenderer;

import java.util.Objects;
import java.util.function.Function;

public class RenderMazarineManaSpark extends BaseSparkRenderer<EntityMazarineManaSpark> {

	private final TextureAtlasSprite dispersiveIcon;
	private final TextureAtlasSprite dominantIcon;
	private final TextureAtlasSprite recessiveIcon;
	private final TextureAtlasSprite isolatedIcon;

	@Override
	protected TextureAtlasSprite getBaseIcon(EntityMazarineManaSpark entity) {
		return ModModels.INSTANCE.mazarineSparkWorldIcon.sprite();
	}

	public RenderMazarineManaSpark(EntityRendererProvider.Context ctx) {
		super(ctx);
		Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
		this.dispersiveIcon = Objects.requireNonNull(atlas.apply(ResourceLocation.fromNamespaceAndPath("botania", "item/spark_augment_rune_dispersive")));
		this.dominantIcon = Objects.requireNonNull(atlas.apply(ResourceLocation.fromNamespaceAndPath("botania", "item/spark_augment_rune_dominant")));
		this.recessiveIcon = Objects.requireNonNull(atlas.apply(ResourceLocation.fromNamespaceAndPath("botania", "item/spark_augment_rune_recessive")));
		this.isolatedIcon = Objects.requireNonNull(atlas.apply(ResourceLocation.fromNamespaceAndPath("botania", "item/spark_augment_rune_isolated")));
	}

	@Override
	public TextureAtlasSprite getSpinningIcon(EntityMazarineManaSpark entity) {
		ItemStack upgr = entity.getUpgrade();
		if (upgr.is(vazkii.botania.common.item.BotaniaItems.SPARK_AUGMENT_DISPERSIVE)) {
			return this.dispersiveIcon;
		}
		if (upgr.is(vazkii.botania.common.item.BotaniaItems.SPARK_AUGMENT_DOMINANT)) {
			return this.dominantIcon;
		}
		if (upgr.is(vazkii.botania.common.item.BotaniaItems.SPARK_AUGMENT_RECESSIVE)) {
			return this.recessiveIcon;
		}
		if (upgr.is(vazkii.botania.common.item.BotaniaItems.SPARK_AUGMENT_ISOLATED)) {
			return this.isolatedIcon;
		}
		return null;
	}
}
