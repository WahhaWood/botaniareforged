package net.lmor.botanicalextramachinery.blocks.pattern;

import com.google.common.collect.Range;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.WorkingTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalAlfheimMarket.BlockEntityAlfheimMarketAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalAlfheimMarket.BlockEntityAlfheimMarketBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalAlfheimMarket.BlockEntityAlfheimMarketUpgraded;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.crafting.RecipeHelper;
import org.moddingx.libx.inventory.BaseItemStackHandler;

import vazkii.botania.api.recipe.ElvenTradeRecipe;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockEntityAlfheimMarketPattern extends WorkingTile<vazkii.botania.api.recipe.ElvenTradeRecipe>
		implements MeOutput.Holder {

	public static final int MAX_MANA_PER_TICK = LibXServerConfig.AlfheimMarketSettings.workingDuration;
	private BaseItemStackHandler inventory;
	private ItemStack currentInput;
	private ItemStack currentOutput;

	private int UPGRADE_SLOT = -1;
	private final int FIRST_INPUT_SLOT;
	private final int LAST_INPUT_SLOT;
	private final int FIRST_OUTPUT_SLOT;
	private final int LAST_OUTPUT_SLOT;

	private final SettingPattern settingPattern;
	private int timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;

	private boolean isInfinityMana = false;

	public BlockEntityAlfheimMarketPattern(BlockEntityType<?> type, BlockPos pos, BlockState state, int manaCapacity, int countCraft, int[] slots, SettingPattern settingPattern) {
		super(type, BotaniaRecipeTypes.ELVEN_TRADE_TYPE, pos, state, manaCapacity, slots[0], slots[2], countCraft);

		this.currentInput = ItemStack.EMPTY;
		this.currentOutput = ItemStack.EMPTY;

		FIRST_INPUT_SLOT = slots[0];
		LAST_INPUT_SLOT = slots[1];
		FIRST_OUTPUT_SLOT = slots[2];
		LAST_OUTPUT_SLOT = slots[3];

		if (slots.length >= 5) {
			UPGRADE_SLOT = slots[4];
		}

		this.settingPattern = settingPattern;

		if (UPGRADE_SLOT != -1) {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.ELVEN_TRADE_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.validator((stack) -> {
						return stack.getItem() == ModItems.catalystManaInfinity.asItem();
					}, UPGRADE_SLOT)
					.slotLimit(1, UPGRADE_SLOT)
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1))
					.contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		} else {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.ELVEN_TRADE_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1))
					.contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		}

	}

	//region Base

	@Override
	public void tick() {
		if (this.level != null && !this.level.isClientSide) {
			this.me.tick(this.level, this.getBlockPos());

			isInfinityMana = UPGRADE_SLOT != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT).getItem().asItem() == ModItems.catalystManaInfinity;

			this.runRecipeTick(
					() -> {
						this.currentInput = ItemStack.EMPTY;
					},
					(stack, slotx) -> {
						this.currentInput = stack.copy();
					},
					(stack, slotx) -> {}
			);

			if (this.recipe != null) {
				this.currentOutput = this.recipe.getOutputs().size() == 0 ? ItemStack.EMPTY : this.recipe.getOutputs().get(0).copy();
				this.setChanged();
				this.setDispatchable();
			} else if (!this.currentInput.isEmpty() || !this.currentOutput.isEmpty()) {
				this.currentInput = ItemStack.EMPTY;
				this.currentOutput = ItemStack.EMPTY;
				this.setChanged();
				this.setDispatchable();
			}

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

			if (this.getCurrentMana() > 0) {
				for (int slot = 0; slot < 4; ++slot) {
					if (this.inventory.getStackInSlot(slot).getItem() == Items.BREAD) {
						this.level.setBlock(this.worldPosition, Blocks.AIR.defaultBlockState(), 3);
						this.level.explode(null, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), 3.0F, Level.ExplosionInteraction.BLOCK);
						break;
					}
				}
			}
		}

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
	protected boolean matchRecipe(ElvenTradeRecipe recipe, List<ItemStack> stacks) {
		// Botania ships one "special" elven trade, the Lexica Botania upgrade, and
		// marks it isSpecial() so that only the elven gate can honour it. To the
		// recipe machinery it looks like any other trade: one ingredient in, one
		// item out. Without this check the market would take a player's Lexica
		// Botania and hand back the upgraded book for free.
		if (recipe.isSpecial()) {
			return true;
		}
		return super.matchRecipe(recipe, stacks);
	}

	@Override
	protected List<ItemStack> resultItems(ElvenTradeRecipe recipe, List<ItemStack> stacks) {

		return recipe.getOutputs().stream().map(ItemStack::copy).toList();
	}

	@Override
	protected int getMaxProgress(ElvenTradeRecipe recipe) {
		return LibXServerConfig.AlfheimMarketSettings.recipeCost;
	}

	@Override
	public int getMaxManaPerTick() {
		return MAX_MANA_PER_TICK * settingPattern.getConfigInt("craftTime");
	}

	@Override
	public boolean getUpgradeInfinityMana() {
		return this.isInfinityMana;
	}

	public ItemStack getCurrentInput() {
		return this.currentInput;
	}

	public ItemStack getCurrentOutput() {
		return this.currentOutput;
	}

	@Override
	public void loadAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.currentInput = ItemStack.parseOptional(registries, nbt.getCompound("currentInput"));
		this.currentOutput = ItemStack.parseOptional(registries, nbt.getCompound("currentOutput"));
		this.me.load(nbt);

		this.setChanged();
		this.setDispatchable();
	}

	@Override
	public void saveAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		// ItemStack.save throws on an empty stack since 1.21, and both start out as
		// ItemStack.EMPTY. loadAdditional uses parseOptional, so omitting them is fine.
		if (!this.currentInput.isEmpty()) {
			nbt.put("currentInput", this.currentInput.save(registries));
		}
		if (!this.currentOutput.isEmpty()) {
			nbt.put("currentOutput", this.currentOutput.save(registries));
		}
		this.me.save(nbt);
	}

	@Override
	public void handleUpdateTag(CompoundTag nbt, HolderLookup.Provider registries) {
		if (this.level == null || this.level.isClientSide) {
			super.handleUpdateTag(nbt, registries);
			// registries is the provider to read stacks with: this.level is
			// explicitly allowed to be null in this branch.
			this.currentInput = ItemStack.parseOptional(registries, nbt.getCompound("currentInput"));
			this.currentOutput = ItemStack.parseOptional(registries, nbt.getCompound("currentOutput"));
		}
	}

	@Nonnull
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		if (this.level != null && this.level.isClientSide) {
			return super.getUpdateTag(registries);
		} else {
			CompoundTag nbt = super.getUpdateTag(registries);
			// registries here too: the else branch also runs when level is null.
			if (!this.currentInput.isEmpty()) {
				nbt.put("currentInput", this.currentInput.save(registries));
			}
			if (!this.currentOutput.isEmpty()) {
				nbt.put("currentOutput", this.currentOutput.save(registries));
			}
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
		if (blockEntity instanceof BlockEntityAlfheimMarketBase) {
			return ModBlocks.baseAlfheimMarket.asItem();
		} else if (blockEntity instanceof BlockEntityAlfheimMarketUpgraded) {
			return ModBlocks.upgradedAlfheimMarket.asItem();
		} else if (blockEntity instanceof BlockEntityAlfheimMarketAdvanced) {
			return ModBlocks.advancedAlfheimMarket.asItem();
		} else {
			return ModBlocks.ultimateAlfheimMarket.asItem();
		}
	}

	@Override
	public void setRemoved() {
		this.me.destroy();

		super.setRemoved();
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
