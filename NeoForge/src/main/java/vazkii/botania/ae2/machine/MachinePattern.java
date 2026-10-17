package vazkii.botania.ae2.machine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import vazkii.botania.ae2.ABMachines;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * A machine recipe offered to the ME network as a processing pattern. There is no pattern item in
 * anyone's inventory: the definition is a hidden item carrying only the recipe id, which the decoder
 * turns back into the pattern when a crafting CPU reloads its job. Every item of a tagged input is a
 * valid substitute; the first one is what AE2 autocrafts if none is stored.
 *
 * <p>Inputs are the item parts of the recipe in order, then its costs (mana, water), so
 * {@code inputHolder[i]} of a push lines up with {@code recipe.parts()} and {@code recipe.costs()}.
 */
public final class MachinePattern implements IPatternDetails {
	private final AEItemKey definition;
	private final MachineRecipe recipe;
	private final IInput[] inputs;
	private final List<GenericStack> outputs;

	public MachinePattern(MachineRecipe recipe) {
		this.recipe = recipe;
		ItemStack stack = new ItemStack(ABMachines.RECIPE_PATTERN.get());
		stack.set(ABMachines.PATTERN_RECIPE.get(), recipe.id());
		this.definition = AEItemKey.of(stack);

		List<IInput> list = new ArrayList<>();
		for (MachineRecipe.Part part : recipe.parts()) {
			list.add(new ItemInput(part));
		}
		for (GenericStack cost : recipe.costs()) {
			list.add(new CostInput(cost));
		}
		this.inputs = list.toArray(new IInput[0]);

		Map<AEKey, Long> condensed = new LinkedHashMap<>();
		for (ItemStack output : recipe.outputs()) {
			AEItemKey key = AEItemKey.of(output);
			if (key != null) {
				condensed.merge(key, (long) output.getCount(), Long::sum);
			}
		}
		List<GenericStack> outs = new ArrayList<>(condensed.size());
		condensed.forEach((key, amount) -> outs.add(new GenericStack(key, amount)));
		this.outputs = List.copyOf(outs);
	}

	public MachineRecipe recipe() {
		return recipe;
	}

	public ResourceLocation recipeId() {
		return recipe.id();
	}

	@Override
	public AEItemKey getDefinition() {
		return definition;
	}

	@Override
	public IInput[] getInputs() {
		return inputs;
	}

	@Override
	public List<GenericStack> getOutputs() {
		return outputs;
	}

	/** Only our own provider ever pushes these, straight into the machine. */
	@Override
	public boolean supportsPushInputsToExternalInventory() {
		return false;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof MachinePattern other && other.definition.equals(definition);
	}

	@Override
	public int hashCode() {
		return definition.hashCode();
	}

	/** What a spent item leaves behind: the item itself for a returned part, its crafting remainder otherwise. */
	@Nullable
	static AEKey leftover(MachineRecipe.Part part, AEKey template) {
		if (part.returned()) {
			return template;
		}
		if (template instanceof AEItemKey item) {
			ItemStack remainder = item.toStack().getCraftingRemainingItem();
			return remainder.isEmpty() ? null : AEItemKey.of(remainder);
		}
		return null;
	}

	private static final class ItemInput implements IInput {
		private final MachineRecipe.Part part;
		private final GenericStack[] possible;
		/** Items a simple (item or tag) ingredient accepts; null if it also checks components. */
		@Nullable
		private final Set<Item> items;

		private ItemInput(MachineRecipe.Part part) {
			this.part = part;
			Set<AEItemKey> keys = new LinkedHashSet<>();
			for (ItemStack stack : part.ingredient().getItems()) {
				AEItemKey key = AEItemKey.of(stack);
				if (key != null) {
					keys.add(key);
				}
			}
			this.possible = keys.stream().map(key -> new GenericStack(key, 1)).toArray(GenericStack[]::new);
			this.items = part.ingredient().isSimple() ? keys.stream().map(AEItemKey::getItem).collect(Collectors.toUnmodifiableSet()) : null;
		}

		@Override
		public GenericStack[] getPossibleInputs() {
			return possible;
		}

		@Override
		public long getMultiplier() {
			return part.count();
		}

		@Override
		public boolean isValid(AEKey input, Level level) {
			if (!(input instanceof AEItemKey item)) {
				return false;
			}
			// Ingredient.test builds an ItemStack per call; a simple ingredient only looks at the item
			return items != null ? items.contains(item.getItem()) : item.matches(part.ingredient());
		}

		@Nullable
		@Override
		public AEKey getRemainingKey(AEKey template) {
			return leftover(part, template);
		}
	}

	/** Mana or water: exactly this key, in the stated amount, and none of it comes back. */
	private static final class CostInput implements IInput {
		private final GenericStack cost;

		private CostInput(GenericStack cost) {
			this.cost = cost;
		}

		@Override
		public GenericStack[] getPossibleInputs() {
			return new GenericStack[] { new GenericStack(cost.what(), 1) };
		}

		@Override
		public long getMultiplier() {
			return cost.amount();
		}

		@Override
		public boolean isValid(AEKey input, Level level) {
			return cost.what().equals(input);
		}

		@Nullable
		@Override
		public AEKey getRemainingKey(AEKey template) {
			return null;
		}
	}

	/** Rebuilds a pattern from its definition (crafting CPUs save jobs by definition only). */
	public static final class Decoder implements IPatternDetailsDecoder {
		@Override
		public boolean isEncodedPattern(ItemStack stack) {
			return stack.is(ABMachines.RECIPE_PATTERN.get());
		}

		@Nullable
		@Override
		public IPatternDetails decodePattern(AEItemKey what, Level level) {
			if (what == null || level == null || !what.is(ABMachines.RECIPE_PATTERN.get())) {
				return null;
			}

			ResourceLocation id = what.get(ABMachines.PATTERN_RECIPE.get());
			RecipeHolder<?> holder = id == null ? null : level.getRecipeManager().byKey(id).orElse(null);
			MachineKind kind = holder == null ? null : MachineKind.byRecipeType(holder.value().getType());
			MachineRecipe recipe = kind == null ? null : kind.convert(holder, level.registryAccess());
			return recipe == null ? null : new MachinePattern(recipe);
		}
	}
}
