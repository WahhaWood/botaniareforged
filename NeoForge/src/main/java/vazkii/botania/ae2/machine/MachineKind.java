package vazkii.botania.ae2.machine;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import net.lmor.botanicalextramachinery.config.LibXServerConfig;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.recipe.ElvenTradeRecipe;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.crafting.StateIngredients;

import java.util.List;
import java.util.function.Supplier;

import vazkii.botania.ae2.ae2.ManaKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.level.material.Fluids;

/**
 * The recipe-backed ME machines: which Botania recipe type each one offers to the ME network and how
 * a recipe of that type turns into a {@link MachineRecipe}. Everything the recipe costs (mana, water)
 * becomes an input of the pattern, so the crafting CPU fetches it from the network and pays for it.
 * Client code uses this too, so nothing here may touch a block entity.
 */
public enum MachineKind {
	MANA_INFUSER("me_mana_infuser", () -> BotaniaRecipeTypes.MANA_INFUSION_TYPE),
	APOTHECARY("me_apothecary", () -> BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE),
	RUNIC_ALTAR("me_runic_altar", () -> BotaniaRecipeTypes.RUNIC_ALTAR_TYPE),
	AGGLOMERATION_FACTORY("me_agglomeration_factory", () -> BotaniaRecipeTypes.TERRA_PLATE_TYPE),
	ALFHEIM_MARKET("me_alfheim_market", () -> BotaniaRecipeTypes.ELVEN_TRADE_TYPE);

	/** What one apothecary craft takes from the altar, in AE2 fluid units (1000 = one bucket). */
	public static final long APOTHECARY_WATER = AEFluidKey.AMOUNT_BUCKET;

	public final String id;
	private final Supplier<? extends RecipeType<?>> recipeType;

	MachineKind(String id, Supplier<? extends RecipeType<?>> recipeType) {
		this.id = id;
		this.recipeType = recipeType;
	}

	public RecipeType<?> recipeType() {
		return recipeType.get();
	}

	@Nullable
	public static MachineKind byRecipeType(RecipeType<?> type) {
		for (MachineKind kind : values()) {
			if (kind.recipeType() == type) {
				return kind;
			}
		}
		return null;
	}

	/** The recipe as a machine pattern, or null when it cannot be one (empty tag, no output, ...). */
	@Nullable
	public MachineRecipe convert(RecipeHolder<?> holder, HolderLookup.Provider registries) {
		Object recipe = holder.value();
		switch (this) {
			case MANA_INFUSER -> {
				if (recipe instanceof ManaInfusionRecipe infusion) {
					var catalyst = infusion.getRecipeCatalyst();
					return MachineRecipe.of(holder.id(), infusion.getIngredients(), List.of(),
							List.of(mana(infusion.getManaToConsume())),
							List.of(infusion.getResultItem(registries)),
							catalyst == null || catalyst == StateIngredients.NONE ? null : catalyst);
				}
			}
			case APOTHECARY -> {
				if (recipe instanceof PetalApothecaryRecipe apothecary) {
					List<Ingredient> inputs = new java.util.ArrayList<>(apothecary.getIngredients());
					inputs.add(apothecary.getReagent());
					return MachineRecipe.of(holder.id(), inputs, List.of(),
							List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), APOTHECARY_WATER)),
							List.of(apothecary.getResultItem(registries)), null);
				}
			}
			case RUNIC_ALTAR -> {
				if (recipe instanceof RunicAltarRecipe altar) {
					List<Ingredient> inputs = new java.util.ArrayList<>(altar.getIngredients());
					inputs.add(altar.getReagent());
					return MachineRecipe.of(holder.id(), inputs, altar.getCatalysts(),
							List.of(mana(altar.getMana())),
							List.of(altar.getResultItem(registries)), null);
				}
			}
			case AGGLOMERATION_FACTORY -> {
				if (recipe instanceof TerrestrialAgglomerationRecipe plate) {
					return MachineRecipe.of(holder.id(), plate.getIngredients(), List.of(),
							List.of(mana(plate.getMana())),
							List.of(plate.getResultItem(registries)), null);
				}
			}
			case ALFHEIM_MARKET -> {
				if (recipe instanceof ElvenTradeRecipe trade && !trade.isReturnRecipe()) {
					List<ItemStack> outputs = trade.getOutputs();
					return MachineRecipe.of(holder.id(), trade.getIngredients(), List.of(),
							List.of(mana(LibXServerConfig.AlfheimMarketSettings.recipeCost)),
							outputs, null);
				}
			}
		}
		return null;
	}

	private static GenericStack mana(int amount) {
		return new GenericStack(ManaKey.KEY, amount);
	}
}
