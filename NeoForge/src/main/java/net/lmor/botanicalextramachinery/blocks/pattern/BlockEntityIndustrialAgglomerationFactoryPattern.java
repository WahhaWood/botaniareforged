package net.lmor.botanicalextramachinery.blocks.pattern;

import com.google.common.collect.Range;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.WorkingTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalIndustrialAgglomerationFactory.BlockEntityIndustrialAgglomerationFactoryAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalIndustrialAgglomerationFactory.BlockEntityIndustrialAgglomerationFactoryBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalIndustrialAgglomerationFactory.BlockEntityIndustrialAgglomerationFactoryUpgraded;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.crafting.RecipeHelper;
import org.moddingx.libx.inventory.BaseItemStackHandler;

import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import javax.annotation.Nonnull;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockEntityIndustrialAgglomerationFactoryPattern extends WorkingTile<vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe>
		implements MeOutput.Holder {
	public static final int MAX_MANA_PER_TICK = LibXServerConfig.IndustrialAgglomerationFactorySettings.workingDuration;

	private final int FIRST_INPUT_SLOT;
	private final int LAST_INPUT_SLOT;
	private final int FIRST_OUTPUT_SLOT;
	private final int LAST_OUTPUT_SLOT;
	private int UPGRADE_SLOT_1 = -1;
	private int UPGRADE_SLOT_2 = -1;

	private final SettingPattern settingPattern;

	private final BaseItemStackHandler inventory;
	private int timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;

	protected int speedMulti = 1;

	private boolean isInfinityMana = false;

	public BlockEntityIndustrialAgglomerationFactoryPattern(BlockEntityType<?> type, BlockPos pos, BlockState state, int manaCapacity, int countCraft, int[] slots, SettingPattern settingPattern) {
		super(type, BotaniaRecipeTypes.TERRA_PLATE_TYPE, pos, state, manaCapacity, slots[0], slots[2], countCraft);

		FIRST_INPUT_SLOT = slots[0];
		LAST_INPUT_SLOT = slots[1];
		FIRST_OUTPUT_SLOT = slots[2];
		LAST_OUTPUT_SLOT = slots[3];

		if (slots.length >= 5) {
			UPGRADE_SLOT_1 = slots[4];
			UPGRADE_SLOT_2 = slots[5];
		}

		this.settingPattern = settingPattern;

		if (UPGRADE_SLOT_1 != -1 && UPGRADE_SLOT_2 != -1) {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.TERRA_PLATE_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.validator((stack) -> {
						return (stack.getItem() == ModItems.catalystSpeed.asItem() || stack.getItem() == ModItems.catalystManaInfinity.asItem());
					}, UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1))
					.slotLimit(1, UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		} else {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.TERRA_PLATE_TYPE, stack);
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

			isInfinityMana = ((UPGRADE_SLOT_1 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_1).getItem() == ModItems.catalystManaInfinity) ||
					(UPGRADE_SLOT_2 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_2).getItem() == ModItems.catalystManaInfinity));

			if (this.speedMulti == 1 && ((UPGRADE_SLOT_1 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_1).getItem().asItem() == ModItems.catalystSpeed) ||
					(UPGRADE_SLOT_2 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_2).getItem().asItem() == ModItems.catalystSpeed))) {
				this.speedMulti = 4;
			} else {
				this.speedMulti = 1;
			}

			this.runRecipeTick();

		}

	}

	@Override
	public void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.me.load(nbt);

		this.setChanged();
		this.setDispatchable();
	}

	@Override
	public void saveAdditional(@NotNull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		this.me.save(nbt);
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
	protected int getMaxProgress(TerrestrialAgglomerationRecipe recipe) {
		if (recipe instanceof TerrestrialAgglomerationRecipe terra) {
			return terra.getMana();
		}
		return 100;
	}

	@Override
	public int getMaxManaPerTick() {
		return MAX_MANA_PER_TICK * this.settingPattern.getConfigInt("craftTime") * this.speedMulti;
	}

	@Override
	public boolean getUpgradeInfinityMana() {
		return this.isInfinityMana;
	}

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

	@Override
	public void setRemoved() {
		super.setRemoved();

		this.me.destroy();
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
		if (blockEntity instanceof BlockEntityIndustrialAgglomerationFactoryBase) {
			return ModBlocks.baseIndustrialAgglomerationFactory.asItem();
		} else if (blockEntity instanceof BlockEntityIndustrialAgglomerationFactoryUpgraded) {
			return ModBlocks.upgradedIndustrialAgglomerationFactory.asItem();
		} else if (blockEntity instanceof BlockEntityIndustrialAgglomerationFactoryAdvanced) {
			return ModBlocks.advancedIndustrialAgglomerationFactory.asItem();
		} else {
			return ModBlocks.ultimateIndustrialAgglomerationFactory.asItem();
		}
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
