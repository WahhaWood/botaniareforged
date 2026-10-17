/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package vazkii.botania.kubejs;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.IngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.util.IntBounds;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.rhino.type.TypeInfo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import vazkii.botania.api.recipe.StateIngredient;
import vazkii.botania.common.crafting.StateIngredients;

/**
 * KubeJS entry point for Botania and the addons merged into it.
 * <p>
 * Registered through {@code kubejs.plugins.txt}, which KubeJS reads out of every
 * mod jar; the class is never touched by the mod's own bootstrap, so a pack
 * without KubeJS simply never loads it and the dependency can stay
 * compile-only.
 * <p>
 * What this buys a script author is the mod's custom recipe types. Vanilla
 * shaped and shapeless recipes already work in KubeJS out of the box, but
 * Botania's six machines each have a type of their own, and without a schema a
 * script can neither add a runic altar recipe nor edit one that exists:
 *
 * <pre>
 * ServerEvents.recipes(event =&gt; {
 *   event.recipes.botania.mana_infusion('botania:mana_pearl', 'botania:quartz', 5000)
 *   event.recipes.botania.pure_daisy('minecraft:sandstone', 'minecraft:sand')
 * })
 * </pre>
 */
public class BotaniaKubeJSPlugin implements KubeJSPlugin {

	private static final RecipeComponent<ItemStack> ITEM = ItemStackComponent.ITEM_STACK.instance();
	private static final RecipeComponent<Ingredient> INGREDIENT = IngredientComponent.INGREDIENT.instance();

	/**
	 * Botania's list-shaped JSON fields are always arrays, never a bare item, and
	 * some of them are legitimately empty — a runic altar recipe's catalyst list
	 * is empty whenever the rune needs no catalyst. Rejecting the empty case made
	 * every such recipe fall back to vanilla, where KubeJS could neither edit nor
	 * remove it, so empty lists have to be accepted here.
	 */
	private static final RecipeComponent<List<ItemStack>> ITEM_LIST = ListRecipeComponent.create(
			ITEM, true, false, new IntBounds(0, Integer.MAX_VALUE), Optional.empty());
	private static final RecipeComponent<List<Ingredient>> INGREDIENT_LIST = ListRecipeComponent.create(
			INGREDIENT, true, false, new IntBounds(0, Integer.MAX_VALUE), Optional.empty());

	/**
	 * A state ingredient is Botania's way of naming one or more block states — a
	 * block, a tag, a predicate or a literal state — and it is what the mana
	 * infuser's catalyst and both ends of a pure daisy recipe are made of. KubeJS
	 * has no component for it, so this one hands it Botania's own codec: scripts
	 * write whatever the JSON format accepts, and an edited recipe round-trips
	 * exactly instead of being flattened into what a generic component could
	 * express.
	 */
	private static final RecipeComponent<StateIngredient> STATE_INGREDIENT = new RecipeComponent<>() {
		@Override
		public RecipeComponentType<StateIngredient> type() {
			return STATE_INGREDIENT_TYPE;
		}

		@Override
		public Codec<StateIngredient> codec() {
			return StateIngredients.TYPED_CODEC;
		}

		@Override
		public TypeInfo typeInfo() {
			return TypeInfo.of(StateIngredient.class);
		}
	};

	private static final RecipeComponentType<StateIngredient> STATE_INGREDIENT_TYPE = RecipeComponentType.unit(
			ResourceLocation.fromNamespaceAndPath("botania", "state_ingredient"),
			c -> STATE_INGREDIENT);

	/**
	 * Teaches the script engine how to read a {@link StateIngredient} out of a
	 * plain block name.
	 *
	 * <p>The codec behind the recipe component only accepts the tagged form the
	 * recipe JSON uses ({@code {"type": "botania:block_type", "block": ...}}), so
	 * without this alias a script author writing the obvious
	 * {@code pure_daisy('minecraft:sandstone', 'minecraft:sand')} got a conversion
	 * error instead of a recipe. A script that does write the full JSON still
	 * round-trips through the component's own codec untouched.
	 */
	@Override
	public void registerTypeWrappers(TypeWrapperRegistry registry) {
		// One call only: registerAlias installs a wrapper for the *target* class,
		// and the registry rejects a second one for the same class, so a second
		// alias (say from ItemStack) would abort mod loading rather than add.
		// A String covers what a script actually writes here — a block name —
		// and KubeJS builds an ItemStack from that same string when a recipe
		// wants one.
		registry.registerAlias(StateIngredient.class, String.class, BotaniaKubeJSPlugin::stateIngredientFromString);
	}

	@Override
	public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
		var botania = registry.namespace("botania");

		RecipeKey<Ingredient> input = INGREDIENT.inputKey("input");
		RecipeKey<ItemStack> output = ITEM.outputKey("output");
		RecipeKey<Integer> mana = NumberComponent.INT.otherKey("mana").optional(0);
		// The optional component types are the ones whose *default value* is
		// empty: STRING rejects "" outright ("not allowed to be empty"), so the
		// group has to be OPTIONAL_STRING, which the recipe class itself also
		// defaults to "". And the catalyst must be part of the constructor —
		// KubeJS matches positional arguments against the constructor's arity,
		// so leaving it out makes a four-argument call impossible.
		RecipeKey<StateIngredient> infusionCatalyst = STATE_INGREDIENT.otherKey("catalyst").defaultOptional();
		botania.register("mana_infusion", new RecipeSchema(
				input, output, mana,
				infusionCatalyst,
				StringComponent.OPTIONAL_STRING.otherKey("group").optional("")
		).constructor(output, input, mana, infusionCatalyst));

		RecipeKey<List<Ingredient>> altarIngredients = INGREDIENT_LIST.inputKey("ingredients");
		RecipeKey<List<Ingredient>> altarCatalysts = INGREDIENT_LIST.otherKey("catalysts");
		RecipeKey<ItemStack> altarOutput = ITEM.outputKey("output");
		RecipeKey<Integer> altarMana = NumberComponent.INT.otherKey("mana");
		RecipeKey<Ingredient> altarReagent = INGREDIENT.otherKey("reagent").defaultOptional();
		botania.register("runic_altar", new RecipeSchema(
				altarIngredients, altarCatalysts, altarOutput, altarMana, altarReagent
		).constructor(altarOutput, altarIngredients, altarCatalysts, altarMana, altarReagent));

		RecipeKey<List<Ingredient>> petals = INGREDIENT_LIST.inputKey("ingredients");
		RecipeKey<ItemStack> potion = ITEM.outputKey("output");
		RecipeKey<Ingredient> reagent = INGREDIENT.inputKey("reagent");
		botania.register("petal_apothecary", new RecipeSchema(
				petals, potion, reagent
		).constructor(potion, petals, reagent));

		RecipeKey<List<Ingredient>> tradeInputs = INGREDIENT_LIST.inputKey("ingredients");
		RecipeKey<List<ItemStack>> tradeOutputs = ITEM_LIST.outputKey("output");
		botania.register("elven_trade", new RecipeSchema(
				tradeInputs, tradeOutputs
		).constructor(tradeOutputs, tradeInputs));

		RecipeKey<StateIngredient> daisyInput = STATE_INGREDIENT.inputKey("input");
		RecipeKey<StateIngredient> daisyOutput = STATE_INGREDIENT.outputKey("output");
		// The recipe class defaults time to 0, so a two-argument call has to
		// work: without the default KubeJS rejects it as a missing required
		// component before it ever reaches the codec.
		RecipeKey<Integer> daisyTime = NumberComponent.INT.otherKey("time").optional(0);
		botania.register("pure_daisy", new RecipeSchema(
				daisyInput, daisyOutput, daisyTime
		).constructor(daisyOutput, daisyInput, daisyTime));

		RecipeKey<List<Ingredient>> plateIngredients = INGREDIENT_LIST.inputKey("ingredients");
		// This one recipe type spells its output "result", not "output" — it
		// reuses the vanilla shaped-recipe field. The key name has to match what
		// the codec reads, or the recipe KubeJS writes fails to parse with
		// "No key result"; the role still marks it as the output for matching.
		RecipeKey<ItemStack> plateOutput = ITEM.key("result", ComponentRole.OUTPUT);
		// The plate recipe class defaults mana to 0, so a two-argument call has
		// to work for the same reason the daisy's two-argument call does.
		RecipeKey<Integer> plateMana = NumberComponent.INT.otherKey("mana").optional(0);
		botania.register("terrestrial_agglomeration_plate", new RecipeSchema(
				plateIngredients, plateOutput, plateMana
		).constructor(plateOutput, plateIngredients, plateMana));
	}

	/**
	 * Reads the shorthand a script writes for a state ingredient: a block name,
	 * or a tag name with the leading {@code #} KubeJS uses everywhere else.
	 */
	private static StateIngredient stateIngredientFromString(String name) {
		if (name.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.parse(name.substring(1));
			return StateIngredients.of(TagKey.create(Registries.BLOCK, tag));
		}
		return StateIngredients.of(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name)));
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("botania", path);
	}
}
