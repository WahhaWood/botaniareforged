package net.lmor.botanicalextramachinery.blocks.pattern;

import com.google.common.collect.Range;

import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalOrechid.BlockEntityOrechidAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalOrechid.BlockEntityOrechidBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalOrechid.BlockEntityOrechidUpgraded;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.inventory.BaseItemStackHandler;
import org.moddingx.libx.inventory.IAdvancedItemHandlerModifiable;

import vazkii.botania.api.recipe.OrechidRecipe;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;

public class BlockEntityOrechidPattern extends ExtraBotanicalTile
		implements IInWorldGridNodeHost, IGridConnectedBlockEntity {

	private final List<Integer> FILTER_SLOTS = new ArrayList<>();

	private final int FIRST_INPUT_SLOT;
	private final int LAST_INPUT_SLOT;
	private final int FIRST_OUTPUT_SLOT;
	private final int LAST_OUTPUT_SLOT;
	private int UPGRADE_SLOT_1 = -1;
	private int UPGRADE_SLOT_2 = -1;
	private final BaseItemStackHandler inventory;
	private final SettingPattern settingPattern;

	// Static caches for inputs/outputs to avoid scanning recipes every time
	private static volatile Set<Item> STATIC_ORECHID_INPUTS = null;
	private static volatile Set<Item> STATIC_ORECHID_OUTPUTS = null;

	private int cooldown;

	private int timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;

	public BlockEntityOrechidPattern(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state,
			int manaCap, int[] slots, int[] upgrade_slot, SettingPattern settingPattern) {

		super(blockEntityType, pos, state, manaCap);

		FIRST_INPUT_SLOT = slots[0];
		LAST_INPUT_SLOT = slots[1];
		FIRST_OUTPUT_SLOT = slots[2];
		LAST_OUTPUT_SLOT = slots[3];

		for (int i = 4; i < slots.length; i++) {
			FILTER_SLOTS.add(slots[i]);
		}

		if (upgrade_slot != null) {
			UPGRADE_SLOT_1 = upgrade_slot[0];
			UPGRADE_SLOT_2 = upgrade_slot[1];
		}

		this.settingPattern = settingPattern;

		if (upgrade_slot != null) {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> stack.getItem() == ModItems.catalystManaInfinity.asItem() || stack.getItem() == ModItems.catalystStoneInfinity.asItem(), UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.validator((stack) -> this.level != null && getInputs().contains(stack.getItem()), Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.validator((stack) -> getOutputs().contains(stack.getItem()), FILTER_SLOTS.stream().mapToInt(Integer::intValue).toArray())
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
					})
					.slotLimit(1, UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.slotLimit(1, FILTER_SLOTS.stream().mapToInt(Integer::intValue).toArray())
					.build();
		} else {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> this.level != null && getInputs().contains(stack.getItem()), Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.validator((stack) -> getOutputs().contains(stack.getItem()), FILTER_SLOTS.stream().mapToInt(Integer::intValue).toArray())
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
					})
					.slotLimit(1, FILTER_SLOTS.stream().mapToInt(Integer::intValue).toArray())
					.build();
		}

		this.setChangedQueued = false;
	}

	//region Base
	public BlockEntity getBlockEntity() {
		return this;
	}

	private boolean checkOutputSlots() {
		for (int i = FIRST_OUTPUT_SLOT; i <= LAST_OUTPUT_SLOT; i++) {
			if (!inventory.getStackInSlot(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private boolean checkFilterSlots() {
		for (int index : FILTER_SLOTS) {
			if (!inventory.getStackInSlot(index).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void tick() {
		if (this.level != null && !this.level.isClientSide) {
			if (!this.getMainNode().isReady()) {
				this.getMainNode().create(this.level, this.getBlockPos());
			}

			if (getMainNode() != null && getMainNode().getNode() != null && getMainNode().isOnline()) {
				if (timeCheckOutputSlot <= 0) {
					if (checkOutputSlots()) {
						this.exportResultsItemsME();
					}

					timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;
				} else {
					timeCheckOutputSlot--;
				}
			}

			boolean isInfinityMana = ((UPGRADE_SLOT_1 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_1).getItem() == ModItems.catalystManaInfinity) ||
					(UPGRADE_SLOT_2 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_2).getItem() == ModItems.catalystManaInfinity));

			if (this.cooldown > 0) {
				--this.cooldown;
				this.setChanged();
				this.setDispatchable();
			}

			if (this.cooldown <= 0) {
				int output_slot = LAST_INPUT_SLOT;
				for (int input_slot = FIRST_INPUT_SLOT; input_slot <= LAST_INPUT_SLOT; input_slot++) {
					output_slot++;
					if (inventory.getStackInSlot(input_slot).isEmpty() || !inventory.getStackInSlot(output_slot).isEmpty()) {
						continue;
					}

					OrechidRecipe recipe = getRecipeOrechid(
							blockStateOf(inventory.getStackInSlot(input_slot)), checkFilterSlots());
					if (recipe == null) {
						continue;
					}

					// Pick the ore before sizing the batch, so the limit is the
					// one for the ore actually being made. Asking an empty slot
					// instead reports a limit of one, which quietly reduced every
					// orechid to making a single ore however many stones it held.
					Block made = recipe.getOutput().pick(this.level.random).getBlock();
					ItemStack resItemStack = new ItemStack(made.asItem());
					int limit = resItemStack.getMaxStackSize();
					ItemStack already = inventory.getStackInSlot(output_slot);
					int room = already.isEmpty() ? limit : limit - already.getCount();

					int count_recipe = Math.min(inventory.getStackInSlot(input_slot).getCount(), settingPattern.getConfigInt("countCraft"));
					count_recipe = Math.min(count_recipe, room);

					if (!isInfinityMana) {
						if (this.getCurrentMana() < LibXServerConfig.OrechidSettings.recipeCost * count_recipe) {
							while (this.getCurrentMana() < LibXServerConfig.OrechidSettings.recipeCost * count_recipe) {
								count_recipe--;
							}
						}

						if (count_recipe == 0) {
							break;
						}

						this.receiveMana(-(count_recipe * LibXServerConfig.OrechidSettings.recipeCost));
					}

					resItemStack.setCount(count_recipe);

					IAdvancedItemHandlerModifiable inventory = this.getInventory().getUnrestricted();

					inventory.extractItem(input_slot, count_recipe, false);
					inventory.insertItem(output_slot, resItemStack, false);
				}

				if (UPGRADE_SLOT_1 != -1 && UPGRADE_SLOT_2 != -1) {
					if (this.inventory.getStackInSlot(UPGRADE_SLOT_1).getItem() == ModItems.catalystStoneInfinity
							|| this.inventory.getStackInSlot(UPGRADE_SLOT_2).getItem() == ModItems.catalystStoneInfinity) {
						ItemStack stone = new ItemStack(Items.STONE);
						stone.setCount(64);
						for (int i = FIRST_INPUT_SLOT; i <= LAST_INPUT_SLOT; i++) {
							inventory.setStackInSlot(i, stone.copy());
						}
					}
				}

				this.cooldown = settingPattern.getConfigInt("cooldown");
			}
		}
	}

	@Override
	protected Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> supplier) {
		return (slot) -> slot >= FIRST_OUTPUT_SLOT && slot <= LAST_OUTPUT_SLOT;
	}

	@NotNull
	@Override
	public BaseItemStackHandler getInventory() {
		return this.inventory;
	}

	@Override
	public int getComparatorOutput() {
		return 0;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();

		if (this.getMainNode() != null) {
			this.getMainNode().destroy();
		}
	}

	@Override
	public void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.cooldown = nbt.getInt("cooldown");
		this.getMainNode().loadFromNBT(nbt);

		this.setChanged();
		this.setDispatchable();
	}

	@Override
	public void saveAdditional(@NotNull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("cooldown", this.cooldown);
		this.getMainNode().saveToNBT(nbt);
	}

	@Override
	public void handleUpdateTag(CompoundTag nbt, HolderLookup.Provider registries) {
		super.handleUpdateTag(nbt, registries);
		if (this.level == null || this.level.isClientSide) {
			this.cooldown = nbt.getInt("cooldown");

		}
	}

	@NotNull
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		if (this.level != null && this.level.isClientSide) {
			return super.getUpdateTag(registries);
		} else {
			CompoundTag nbt = super.getUpdateTag(registries);
			nbt.putInt("cooldown", this.cooldown);

			return nbt;
		}
	}

	public List<Item> getOutputs() {
		if (STATIC_ORECHID_OUTPUTS == null) {
			synchronized (BlockEntityOrechidPattern.class) {
				if (STATIC_ORECHID_OUTPUTS == null) {
					Set<Item> outputs = new LinkedHashSet<>();
					if (this.level != null) {
						this.level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.ORECHID_TYPE).forEach(recipeHolder -> {
							OrechidRecipe recipe = recipeHolder.value();
							if (recipe.getOutput() != null) {
								recipe.getOutput().getDisplayedStacks().stream().map(ItemStack::getItem).forEach(outputs::add);
							}
						});
					}
					STATIC_ORECHID_OUTPUTS = outputs;
				}
			}
		}
		return STATIC_ORECHID_OUTPUTS == null ? List.of() : List.copyOf(STATIC_ORECHID_OUTPUTS);
	}

	public List<Item> getInputs() {
		if (STATIC_ORECHID_INPUTS == null) {
			synchronized (BlockEntityOrechidPattern.class) {
				if (STATIC_ORECHID_INPUTS == null) {
					Set<Item> inputs = new LinkedHashSet<>();
					if (this.level != null) {
						this.level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.ORECHID_TYPE).forEach(recipeHolder -> {
							OrechidRecipe recipe = recipeHolder.value();
							if (recipe.getInput() != null) {
								recipe.getInput().getDisplayedStacks().stream().map(ItemStack::getItem).forEach(inputs::add);
							}
						});
					}
					STATIC_ORECHID_INPUTS = inputs;
				}
			}
		}
		return STATIC_ORECHID_INPUTS == null ? List.of() : List.copyOf(STATIC_ORECHID_INPUTS);
	}

	@SuppressWarnings("unused")
	public static void invalidateOrechidCaches() {
		STATIC_ORECHID_INPUTS = null;
		STATIC_ORECHID_OUTPUTS = null;
	}

	/** The block state a recipe's input ingredient is tested against. */
	private static BlockState blockStateOf(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem
				? blockItem.getBlock().defaultBlockState()
				: Blocks.AIR.defaultBlockState();
	}

	/**
	 * The ore recipes that {@code input} can be turned into.
	 *
	 * <p>
	 * The input decides which recipes are on the table at all: without that,
	 * the orechid picks one recipe out of the whole registry by weight and so
	 * will happily hand back a deepslate ore for a lump of stone, or an ore for
	 * something that is not stone at all. What is left is weighted, because that
	 * is how the addon's recipe data expresses how common each ore is.
	 *
	 * @param input    the stack in the input slot, as a block state to match
	 * @param filtered whether the filter slots should restrict the outputs
	 */
	public @Nullable OrechidRecipe getRecipeOrechid(BlockState input, boolean filtered) {
		List<WeightedEntry.Wrapper<OrechidRecipe>> values = new ArrayList<>();

		assert this.level != null;
		this.level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.ORECHID_TYPE).forEach((recipeHolder) -> {
			OrechidRecipe recipe = recipeHolder.value();
			if (recipe.getInput() == null || !recipe.getInput().test(input)) {
				return;
			}
			if (filtered && !passesFilter(recipe)) {
				return;
			}
			values.add(WeightedEntry.wrap(recipe, recipe.getWeight()));
		});

		return WeightedRandom.getRandomItem(this.level.random, values).map(WeightedEntry.Wrapper::data).orElse(null);
	}

	/** Whether any ore the recipe can make is one of the ores in the filters. */
	private boolean passesFilter(OrechidRecipe recipe) {
		if (recipe.getOutput() == null) {
			return false;
		}
		for (ItemStack made : recipe.getOutput().getDisplayedStacks()) {
			for (int index : FILTER_SLOTS) {
				if (inventory.getStackInSlot(index).getItem() == made.getItem()) {
					return true;
				}
			}
		}
		return false;
	}
	//endregion

	//region AE INTEGRATION

	private boolean setChangedQueued;

	private final IManagedGridNode mainNode = this.createMainNode().setVisualRepresentation(this.getItemFromBlockEntity()).setInWorldNode(true).setTagName("proxy");

	protected IManagedGridNode createMainNode() {
		return GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE);
	}

	protected Item getItemFromBlockEntity() {
		BlockEntity blockEntity = this.getBlockEntity();
		if (blockEntity instanceof BlockEntityOrechidBase) {
			return ModBlocks.baseOrechid.asItem();
		} else if (blockEntity instanceof BlockEntityOrechidUpgraded) {
			return ModBlocks.upgradedOrechid.asItem();
		} else if (blockEntity instanceof BlockEntityOrechidAdvanced) {
			return ModBlocks.advancedOrechid.asItem();
		} else {
			return ModBlocks.ultimateOrechid.asItem();
		}
	}

	private void setChangedAtEndOfTick(Level level) {
		this.setChanged();
		this.setChangedQueued = false;
	}

	@Nullable
	@Override
	public IGridNode getGridNode(Direction direction) {
		return this.getMainNode().getNode();
	}

	@Override
	public IManagedGridNode getMainNode() {
		return this.mainNode;
	}

	@Override
	public void saveChanges() {
		if (level != null) {
			if (level.isClientSide) {
				this.setChanged();
			} else {
				this.level.blockEntityChanged(this.worldPosition);
				if (!this.setChangedQueued) {
					TickHandler.instance().addCallable(null, this::setChangedAtEndOfTick);
					this.setChangedQueued = true;
				}
			}
		}
	}

	@Override
	public AECableType getCableConnectionType(Direction dir) {
		return AECableType.SMART;
	}

	private void exportResultsItemsME() {
		for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT; slot++) {
			ItemStack stackInSlot = this.inventory.getStackInSlot(slot);

			if (!stackInSlot.isEmpty()) {
				int getCountExport = Math.toIntExact(Objects.requireNonNull(this.getMainNode().getGrid()).getStorageService().getInventory().insert(GenericStack.fromItemStack(stackInSlot).what(), stackInSlot.getCount(), Actionable.MODULATE, IActionSource.empty()));

				if (getCountExport > 0) {
					stackInSlot.shrink(getCountExport);
					this.inventory.setStackInSlot(slot, stackInSlot);
				}
			}
		}
	}
	//endregion
}
