package vazkii.botania.ae2.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import vazkii.botania.client.integration.jei.ManaPoolRecipeCategory;

import vazkii.botania.ae2.ABBlocks;
import vazkii.botania.ae2.AppliedBotanics;

@JeiPlugin
public class ABJEIPlugin implements IModPlugin {
	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(new ItemStack(ABBlocks.FLUIX_MANA_POOL.get()), new RecipeType[] { ManaPoolRecipeCategory.TYPE });
	}

	@NotNull
	@Override
	public ResourceLocation getPluginUid() {
		return AppliedBotanics.id("main");
	}
}
