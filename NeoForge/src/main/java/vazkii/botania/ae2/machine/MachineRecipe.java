package vazkii.botania.ae2.machine;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.recipe.StateIngredient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.api.stacks.GenericStack;

/**
 * One Botania recipe as an ME machine sees it: item inputs with counts (equal ingredients merged),
 * inputs that come back unchanged (runic altar catalysts), costs that are not items (mana, water),
 * and the fixed outputs.
 *
 * @param catalyst block that has to stand under the machine (mana infusion catalysts), or null
 */
public record MachineRecipe(ResourceLocation id, List<Part> parts, List<GenericStack> costs, List<ItemStack> outputs,
		@Nullable StateIngredient catalyst) {

	/** Ingredient taken {@code count} times; a {@code returned} part is handed back after the craft. */
	public record Part(Ingredient ingredient, int count, boolean returned) {}

	/**
	 * Bumped whenever recipes may have changed (datapack reload on the server, recipe sync on the
	 * client). Machines compare it with the value they built their list for.
	 */
	private static final AtomicInteger GENERATION = new AtomicInteger();

	@Nullable
	static MachineRecipe of(ResourceLocation id, List<Ingredient> ingredients, List<Ingredient> returned,
			List<GenericStack> costs, List<ItemStack> results, @Nullable StateIngredient catalyst) {
		List<Part> parts = new ArrayList<>();
		if (!merge(parts, ingredients, false) || !merge(parts, returned, true) || parts.isEmpty()) {
			return null;
		}

		List<ItemStack> outputs = new ArrayList<>(results.size());
		for (ItemStack result : results) {
			if (!result.isEmpty()) {
				outputs.add(result.copy());
			}
		}
		return outputs.isEmpty() ? null : new MachineRecipe(id, List.copyOf(parts), List.copyOf(costs), List.copyOf(outputs), catalyst);
	}

	/** Adds the ingredients as parts, counting equal ones once; false if one of them cannot match anything. */
	private static boolean merge(List<Part> parts, List<Ingredient> ingredients, boolean returned) {
		outer:
		for (Ingredient ingredient : ingredients) {
			ItemStack[] items = ingredient.getItems();
			if (ingredient.isEmpty() || items.length == 0) {
				return false;
			}
			for (int i = 0; i < parts.size(); i++) {
				Part part = parts.get(i);
				if (part.returned() == returned && ItemStack.listMatches(Arrays.asList(items), Arrays.asList(part.ingredient().getItems()))) {
					parts.set(i, new Part(part.ingredient(), part.count() + 1, returned));
					continue outer;
				}
			}
			parts.add(new Part(ingredient, 1, returned));
		}
		return true;
	}

	/** The item the GUI shows for this recipe. */
	public ItemStack icon() {
		return outputs.get(0);
	}

	public static int generation() {
		return GENERATION.get();
	}

	public static void invalidate() {
		GENERATION.incrementAndGet();
	}

	/**
	 * All usable recipes of a kind, ordered by id. Server and client build the same list from the same
	 * synced recipes, so the GUI only needs the set of switched-off ids, never the recipes themselves.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map<ResourceLocation, MachineRecipe> collect(MachineKind kind, Level level) {
		HolderLookup.Provider registries = level.registryAccess();
		List<MachineRecipe> list = new ArrayList<>();
		for (RecipeHolder<?> holder : level.getRecipeManager().getAllRecipesFor((RecipeType<Recipe<RecipeInput>>) (RecipeType) kind.recipeType())) {
			MachineRecipe recipe = kind.convert(holder, registries);
			if (recipe != null) {
				list.add(recipe);
			}
		}

		list.sort(Comparator.comparing(recipe -> recipe.id().toString()));
		Map<ResourceLocation, MachineRecipe> byId = new LinkedHashMap<>();
		for (MachineRecipe recipe : list) {
			byId.put(recipe.id(), recipe);
		}
		return byId;
	}
}
