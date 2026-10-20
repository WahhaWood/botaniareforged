package net.lmor.botanicalextramachinery.blocks.pattern;

import com.google.common.collect.Range;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.WorkingTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalRunicAltar.BlockEntityRunicAltarAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalRunicAltar.BlockEntityRunicAltarBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalRunicAltar.BlockEntityRunicAltarUpgraded;
import net.lmor.botanicalextramachinery.config.LibXClientConfig;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.crafting.RecipeHelper;
import org.moddingx.libx.inventory.BaseItemStackHandler;

import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.lib.BotaniaTags;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockEntityRunicAltarPattern extends WorkingTile<vazkii.botania.api.recipe.RunicAltarRecipe>
		implements MeOutput.Holder {
	public static final int MAX_MANA_PER_TICK = LibXServerConfig.RunicAltarSettings.manaCost;

	private final int LIVINGROCK_SLOT_1;
	private final int LIVINGROCK_SLOT_2;
	private final int LIVINGROCK_SLOT_3;
	private final int UPGRADE_SLOT_1;
	private final int UPGRADE_SLOT_2;
	private final int FIRST_INPUT_SLOT;
	private final int LAST_INPUT_SLOT;
	private final int FIRST_OUTPUT_SLOT;
	private final int LAST_OUTPUT_SLOT;

	private final BaseItemStackHandler inventory;
	private final SettingPattern settingPattern;

	private final List<Integer> slotsUsed = new ArrayList<>();
	private int timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;

	private List<Integer> slotsLivingRock = new ArrayList<>();

	private boolean isInfinityMana = false;
	private int livingRockBuffer = 0;

	public BlockEntityRunicAltarPattern(BlockEntityType<?> type, BlockPos pos, BlockState state,
			int manaCap, int[] slots, boolean[] isUpgrade, int countCraft,
			SettingPattern config) {

		super(type, BotaniaRecipeTypes.RUNIC_ALTAR_TYPE, pos, state, manaCap, slots[1], slots[3], countCraft);

		LIVINGROCK_SLOT_1 = addLivingRockSlotsArray(slots[0]);

		FIRST_INPUT_SLOT = slots[1];
		LAST_INPUT_SLOT = slots[2];
		FIRST_OUTPUT_SLOT = slots[3];
		LAST_OUTPUT_SLOT = slots[4];

		UPGRADE_SLOT_1 = slots[5];
		UPGRADE_SLOT_2 = slots[6];

		LIVINGROCK_SLOT_2 = addLivingRockSlotsArray(slots[7]);
		LIVINGROCK_SLOT_3 = addLivingRockSlotsArray(slots[8]);

		if (isUpgrade[0] && isUpgrade[1]) {
			inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return stack.getItem() == BotaniaBlocks.LIVINGROCK.asItem();
					}, LIVINGROCK_SLOT_1, LIVINGROCK_SLOT_2, LIVINGROCK_SLOT_3)
					.validator((stack) -> {
						return (stack.getItem() == ModItems.catalystManaInfinity.asItem() || stack.getItem() == ModItems.catalystLivingRockInfinity.asItem());
					}, UPGRADE_SLOT_1)
					.validator((stack) -> {
						return (stack.getItem() == ModItems.catalystManaInfinity.asItem() || stack.getItem() == ModItems.catalystLivingRockInfinity.asItem());
					}, UPGRADE_SLOT_2)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.RUNIC_ALTAR_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.slotLimit(1, UPGRADE_SLOT_1, UPGRADE_SLOT_2).output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		} else if (isUpgrade[0]) {
			inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return stack.getItem() == BotaniaBlocks.LIVINGROCK.asItem();
					}, LIVINGROCK_SLOT_1, LIVINGROCK_SLOT_2, LIVINGROCK_SLOT_3)
					.validator((stack) -> {
						return stack.getItem() == ModItems.catalystManaInfinity.asItem() || stack.getItem() == ModItems.catalystLivingRockInfinity.asItem();
					}, UPGRADE_SLOT_1)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.RUNIC_ALTAR_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.slotLimit(1, UPGRADE_SLOT_1, UPGRADE_SLOT_2).output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		} else {
			inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return stack.getItem() == BotaniaBlocks.LIVINGROCK.asItem();
					}, LIVINGROCK_SLOT_1, LIVINGROCK_SLOT_2, LIVINGROCK_SLOT_3)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.RUNIC_ALTAR_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.slotLimit(1, UPGRADE_SLOT_1, UPGRADE_SLOT_2).output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		}

		this.settingPattern = config;

	}

	public void bufferCheck() {
		if (livingRockBuffer == 0) {
			return;
		}

		// Pay the debt onCrafted left behind out of whichever living-rock slots
		// actually hold rock. This used to inspect each slot in turn but extract
		// from slot 0 unconditionally, so rock in the other slots was never
		// touched, and a debt larger than what slot 0 held was silently
		// cancelled: the altar kept crafting without eating its living rock.
		for (int slot : slotsLivingRock) {
			if (livingRockBuffer == 0) {
				break;
			}

			ItemStack stack = this.inventory.getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}

			int take = Math.min(livingRockBuffer, stack.getCount());
			this.inventory.extractItem(slot, take, false);
			livingRockBuffer -= take;
		}
	}

	//region BASE CODE

	public int addLivingRockSlotsArray(int index) {
		slotsLivingRock.add(index);
		// The index that was handed in is the slot itself. Returning
		// slotsLivingRock.get(index) happens to agree on every tier shipped so
		// far only because those slots are appended in ascending order; a tier
		// whose living-rock slot was past the end would have thrown instead.
		return index;
	}

	public List<ItemStack> getUpgrades() {
		List<ItemStack> res = new ArrayList<>();

		res.add(new ItemStack(ModItems.catalystManaInfinity));
		res.add(new ItemStack(ModItems.catalystLivingRockInfinity));

		return res;
	}

	@Override
	public void tick() {
		if (this.level != null && !this.level.isClientSide) {
			this.me.tick(this.level, this.getBlockPos());

			if (this.me.isOnline()) {
				if (timeCheckOutputSlot <= 0) {
					if (checkOutputSlots()) {
						this.exportResultsItemsME();
					}

					timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;
				} else {
					timeCheckOutputSlot--;
				}
			}

			if (!this.inventory.getStackInSlot(0).isEmpty() && livingRockBuffer != 0) {
				bufferCheck();
			}

			isInfinityMana = ((UPGRADE_SLOT_1 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_1).getItem() == ModItems.catalystManaInfinity) ||
					(UPGRADE_SLOT_2 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_2).getItem() == ModItems.catalystManaInfinity));

			if (inventory.getStackInSlot(LIVINGROCK_SLOT_1).isEmpty() ||
					inventory.getStackInSlot(LIVINGROCK_SLOT_2).isEmpty() ||
					inventory.getStackInSlot(LIVINGROCK_SLOT_3).isEmpty()) {

				if (getSlotUpgrade1().is(ModItems.catalystLivingRockInfinity) || getSlotUpgrade2().is(ModItems.catalystLivingRockInfinity)) {
					addLivingRockSlots();
				}
			}

			List slotsUsed = this.slotsUsed;
			this.runRecipeTick(
					slotsUsed::clear,
					(stack, slot) -> {
						this.slotsUsed.add(slot);
					},
					(stack, slot) -> {});

		} else if (this.level != null && LibXClientConfig.RenderingVisualContent.all && settingPattern.getConfigBoolean("mechanicalRunicAltarRender") && this.getMaxProgress() > 0 && this.getProgress() >= this.getMaxProgress() - 5 * this.getMaxManaPerTick()) {
			for (int i = 0; i < 5; ++i) {
				SparkleParticleData data = SparkleParticleData.sparkle(this.level.random.nextFloat(), this.level.random.nextFloat(), this.level.random.nextFloat(), this.level.random.nextFloat(), 10);
				this.level.addParticle(data, (double) this.worldPosition.getX() + 0.3 + this.level.random.nextDouble() * 0.4, (double) this.worldPosition.getY() + 0.7, (double) this.worldPosition.getZ() + 0.3 + this.level.random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
			}
		}
	}

	public ItemStack getSlotUpgrade1() {
		if (UPGRADE_SLOT_1 == -1) {
			return ItemStack.EMPTY;
		}
		return inventory.getStackInSlot(UPGRADE_SLOT_1);
	}

	public ItemStack getSlotUpgrade2() {
		if (UPGRADE_SLOT_2 == -1) {
			return ItemStack.EMPTY;
		}
		return inventory.getStackInSlot(UPGRADE_SLOT_2);
	}

	public void addLivingRockSlots() {
		ItemStack livingrock = new ItemStack(BotaniaBlocks.LIVINGROCK.asItem());
		livingrock.setCount(64);

		if (LIVINGROCK_SLOT_1 == LIVINGROCK_SLOT_2 && LIVINGROCK_SLOT_2 == LIVINGROCK_SLOT_3
				&& inventory.getStackInSlot(LIVINGROCK_SLOT_1).isEmpty()) {
			inventory.setStackInSlot(LIVINGROCK_SLOT_1, livingrock);
		} else {
			if (inventory.getStackInSlot(LIVINGROCK_SLOT_1).isEmpty()) {
				inventory.setStackInSlot(LIVINGROCK_SLOT_1, livingrock);
			} else if (inventory.getStackInSlot(LIVINGROCK_SLOT_2).isEmpty()) {
				inventory.setStackInSlot(LIVINGROCK_SLOT_2, livingrock);
			} else if (inventory.getStackInSlot(LIVINGROCK_SLOT_3).isEmpty()) {
				inventory.setStackInSlot(LIVINGROCK_SLOT_3, livingrock);
			}
		}
	}

	@Override
	protected Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> inventory) {
		return (slot) -> {
			return slot >= FIRST_OUTPUT_SLOT && slot <= LAST_OUTPUT_SLOT;
		};
	}

	@Nonnull
	@Override
	public BaseItemStackHandler getInventory() {
		return this.inventory;
	}

	@Override
	protected boolean canMatchRecipes() {
		return !this.inventory.getStackInSlot(LIVINGROCK_SLOT_1).isEmpty() ||
				!this.inventory.getStackInSlot(LIVINGROCK_SLOT_2).isEmpty() ||
				!this.inventory.getStackInSlot(LIVINGROCK_SLOT_3).isEmpty();
	}

	@Override
	protected void onCrafted(RunicAltarRecipe recipe, int countCraft) {
		for (int slot : slotsLivingRock) {
			if (!inventory.getStackInSlot(slot).isEmpty()) {
				livingRockBuffer = countCraft > this.inventory.getStackInSlot(slot).getCount() ? countCraft - this.inventory.getStackInSlot(slot).getCount() : 0;
				this.inventory.extractItem(slot, livingRockBuffer == 0 ? countCraft : this.inventory.getStackInSlot(slot).getCount(), false);
				if (livingRockBuffer == 0) {
					return;
				}
			}
		}
	}

	@Override
	protected List<ItemStack> resultItems(RunicAltarRecipe recipe, @NotNull List<ItemStack> stacks) {
		// Return one rune per rune ingredient of the recipe. The consumed stacks can be split
		// across several input slots, so iterating them directly would return a rune per fragment.
		List<ItemStack> res = new ArrayList<>();
		for (Ingredient ingredient : recipe.getIngredients()) {
			for (ItemStack s : stacks) {
				if (s.is(BotaniaTags.Items.RUNES) && ingredient.test(s)) {
					ItemStack copy = s.copy();
					copy.setCount(1);
					res.add(copy);
					break;
				}
			}
		}
		res.addAll(super.resultItems(recipe, stacks));
		return res;
	}

	@Override
	protected int getMaxProgress(@NotNull RunicAltarRecipe recipe) {
		if (recipe instanceof RunicAltarRecipe runic) {
			return runic.getMana();
		}
		return 100;
	}

	private boolean checkOutputSlots() {
		for (int i = FIRST_OUTPUT_SLOT; i < LAST_OUTPUT_SLOT + 1; i++) {
			if (!this.inventory.getStackInSlot(i).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	@Override
	public int getMaxManaPerTick() {
		return MAX_MANA_PER_TICK * settingPattern.getConfigInt("craftTime");
	}

	@Override
	public boolean getUpgradeInfinityMana() {
		return this.isInfinityMana;
	}

	public boolean isSlotUsedCurrently(int slot) {
		return this.slotsUsed.contains(slot);
	}

	@Override
	public void loadAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.slotsUsed.clear();
		this.slotsUsed.addAll(Arrays.stream(nbt.getIntArray("slotsUsed")).boxed().toList());
		this.livingRockBuffer = nbt.getInt("livingRockBuffer");
		this.me.load(nbt);

		this.setChanged();
		this.setDispatchable();
	}

	@Override
	public void saveAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putIntArray("slotsUsed", this.slotsUsed);
		nbt.putInt("livingRockBuffer", this.livingRockBuffer);
		this.me.save(nbt);
	}

	@Override
	public void handleUpdateTag(CompoundTag nbt, HolderLookup.Provider registries) {
		super.handleUpdateTag(nbt, registries);
		if (this.level == null || this.level.isClientSide) {
			this.slotsUsed.clear();
			this.slotsUsed.addAll(Arrays.stream(nbt.getIntArray("slotsUsed")).boxed().toList());
			this.livingRockBuffer = nbt.getInt("livingRockBuffer");
		}
	}

	@Nonnull
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		if (this.level != null && this.level.isClientSide) {
			return super.getUpdateTag(registries);
		} else {
			CompoundTag nbt = super.getUpdateTag(registries);

			nbt.putIntArray("slotsUsed", this.slotsUsed);
			nbt.putInt("livingRockBuffer", this.livingRockBuffer);

			return nbt;
		}
	}

	public BlockEntity getBlockEntity() {
		return this;
	}
	//endregion

	//region AE INTEGRATION

	private final MeOutput me = MeOutput.create(this, this.getItemFromBlockEntity());

	@Override
	public MeOutput meOutput() {
		return this.me;
	}

	protected Item getItemFromBlockEntity() {
		BlockEntity blockEntity = this.getBlockEntity();
		if (blockEntity instanceof BlockEntityRunicAltarBase) {
			return ModBlocks.baseRunicAltar.asItem();
		} else if (blockEntity instanceof BlockEntityRunicAltarUpgraded) {
			return ModBlocks.upgradedRunicAltar.asItem();
		} else if (blockEntity instanceof BlockEntityRunicAltarAdvanced) {
			return ModBlocks.advancedRunicAltar.asItem();
		} else {
			return ModBlocks.ultimateRunicAltar.asItem();
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();

		this.me.destroy();
	}

	private void exportResultsItemsME() {
		for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT; slot++) {
			ItemStack stackInSlot = this.inventory.getStackInSlot(slot);

			if (!stackInSlot.isEmpty()) {
				int getCountExport = this.me.insert(stackInSlot);

				if (getCountExport > 0) {
					stackInSlot.shrink(getCountExport);
					this.inventory.setStackInSlot(slot, stackInSlot);
				}
			}
		}
	}

	//endregion
}
